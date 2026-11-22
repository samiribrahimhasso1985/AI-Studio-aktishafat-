package com.iktishafat.teleprompter.platform.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.VideoCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface UvcState {
    data object Idle : UvcState
    /** Android shows its "Allow access to USB device" dialog. */
    data object WaitingForPermission : UvcState
    data object Opening : UvcState
    data class Previewing(val width: Int, val height: Int, val fps: Int) : UvcState
    data object PermissionDenied : UvcState
    data object Disconnected : UvcState
    data class Failed(val message: String) : UvcState
}

/**
 * Direct driver for USB Video Class devices (HDMI→USB-C capture cards such as those used with a
 * Sony FX3, USB webcams, cameras in USB-streaming mode). Uses the Android USB Host API through
 * the UVCAndroid library, so it works on tablets whose system does not expose external cameras
 * to Camera2/CameraX (common on Samsung devices).
 */
class UvcCamera(private val context: Context) {
    private val helper: ICameraHelper = CameraHelper()
    private var device: UsbDevice? = null
    private var surface: Surface? = null
    private var targetHeight = 1080
    private var sizeChosen = false

    private val _state = MutableStateFlow<UvcState>(UvcState.Idle)
    val state: StateFlow<UvcState> = _state

    private val callback = object : ICameraHelper.StateCallback {
        override fun onAttach(d: UsbDevice) {
            // Re-plugged the device we were using: reopen it.
            if (device != null && d.deviceName == device?.deviceName && _state.value is UvcState.Disconnected) select(d)
        }

        override fun onDeviceOpen(d: UsbDevice, isFirstOpen: Boolean) {
            if (d.deviceName != device?.deviceName) return
            _state.value = UvcState.Opening
            sizeChosen = false
            helper.openCamera()
        }

        override fun onCameraOpen(d: UsbDevice) {
            if (d.deviceName != device?.deviceName) return
            if (!sizeChosen) {
                sizeChosen = true
                // Prefer the requested resolution (720p/1080p/2160p), else the closest smaller one.
                val sizes = runCatching { helper.supportedSizeList }.getOrNull().orEmpty()
                val best = sizes.filter { it.height <= targetHeight }.maxWithOrNull(compareBy({ it.height }, { it.fps }))
                val current = helper.previewSize
                if (best != null && (current == null || best.width != current.width || best.height != current.height)) {
                    runCatching { helper.previewSize = best }
                }
            }
            helper.startPreview()
            surface?.let { helper.addSurface(it, false) }
            val s = helper.previewSize
            _state.value = UvcState.Previewing(s?.width ?: 0, s?.height ?: 0, s?.fps ?: 0)
        }

        override fun onCameraClose(d: UsbDevice) {
            surface?.let { runCatching { helper.removeSurface(it) } }
        }

        override fun onDeviceClose(d: UsbDevice) = Unit

        override fun onDetach(d: UsbDevice) {
            if (d.deviceName == device?.deviceName) _state.value = UvcState.Disconnected
        }

        override fun onCancel(d: UsbDevice) {
            if (d.deviceName == device?.deviceName) _state.value = UvcState.PermissionDenied
        }

        override fun onError(d: UsbDevice?, e: CameraException) {
            Log.w(TAG, "UVC error ${e.code}", e)
            _state.value = UvcState.Failed(e.message ?: "code ${e.code}")
        }
    }

    init {
        helper.setStateCallback(callback)
    }

    /** Opens the USB device with this name (from UsbManager). Android asks the user for permission once. */
    fun open(deviceName: String, targetHeight: Int): Boolean {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val d = usb.deviceList[deviceName] ?: run { _state.value = UvcState.Disconnected; return false }
        this.targetHeight = targetHeight
        select(d)
        return true
    }

    private fun select(d: UsbDevice) {
        device = d
        _state.value = UvcState.WaitingForPermission
        helper.selectDevice(d)
    }

    fun attachSurface(s: Surface) {
        surface = s
        if (helper.isCameraOpened) helper.addSurface(s, false)
    }

    fun detachSurface(s: Surface) {
        if (surface == s) surface = null
        runCatching { helper.removeSurface(s) }
    }

    val isRecording: Boolean get() = runCatching { helper.isRecording }.getOrDefault(false)

    /** Records the incoming UVC stream to MediaStore (Movies/Iktishafat). */
    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO when withAudio is true
    fun startRecording(
        collection: Uri,
        values: ContentValues,
        withAudio: Boolean,
        onStart: () -> Unit,
        onSaved: (Uri?) -> Unit,
        onError: (String) -> Unit,
    ) {
        helper.videoCaptureConfig = helper.videoCaptureConfig
            .setAudioCaptureEnable(withAudio)
            .setAudioSampleRate(48_000)
            .setAudioBitRate(128_000)
            .setBitRate(if (targetHeight >= 2160) 40_000_000 else 16_000_000)
            .setVideoFrameRate(30)
        val options = VideoCapture.OutputFileOptions.Builder(context.contentResolver, collection, values).build()
        helper.startRecording(options, object : VideoCapture.OnVideoCaptureCallback {
            override fun onStart() = onStart()
            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) = onSaved(outputFileResults.savedUri)
            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) = onError(message)
        })
    }

    fun stopRecording() {
        runCatching { helper.stopRecording() }
    }

    /** Stops using the device but keeps the driver ready for another USB camera. */
    fun close() {
        runCatching { if (helper.isRecording) helper.stopRecording() }
        runCatching { helper.closeCamera() }
        device = null
        _state.value = UvcState.Idle
    }

    fun release() {
        runCatching { if (helper.isRecording) helper.stopRecording() }
        runCatching { helper.setStateCallback(null) }
        runCatching { helper.release() }
        device = null
        _state.value = UvcState.Idle
    }

    private companion object { const val TAG = "UvcCamera" }
}
