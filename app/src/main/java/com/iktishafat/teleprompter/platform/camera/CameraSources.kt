package com.iktishafat.teleprompter.platform.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A camera the studio can record from, or a USB video device the system has not exposed as a camera. */
sealed interface CameraSource {
    val key: String

    /** Front or rear camera of the tablet. */
    data class Builtin(val front: Boolean, val cameraId: String) : CameraSource {
        override val key = "builtin:$cameraId"
    }

    /**
     * External camera that Android itself exposes to apps (LENS_FACING_EXTERNAL), e.g. a UVC
     * webcam or an HDMI capture card on devices whose system supports external cameras.
     * Recording works through CameraX exactly like a built-in camera.
     */
    data class External(val cameraId: String, val label: String?) : CameraSource {
        override val key = "external:$cameraId"
    }

    /**
     * A USB video-class device (HDMI capture card, UVC camera, or a camera in USB streaming mode)
     * that is plugged in but NOT exposed as a camera by this tablet's system. It cannot be used
     * until a dedicated UVC driver module is added; the UI says so instead of offering it.
     */
    data class UsbVideoOnly(val deviceName: String, val label: String, val vendorId: Int, val productId: Int) : CameraSource {
        override val key = "usb:$vendorId:$productId:$deviceName"
    }
}

/** Finds the cameras available right now. Safe to call repeatedly (e.g. while a USB cable is plugged in). */
object CameraSourceDetector {

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun detect(context: Context): List<CameraSource> = withContext(Dispatchers.IO) {
        val result = ArrayList<CameraSource>()
        val provider = runCatching { ProcessCameraProvider.getInstance(context).get() }.getOrNull()
        provider?.availableCameraInfos?.forEach { info ->
            val c2 = runCatching { Camera2CameraInfo.from(info) }.getOrNull() ?: return@forEach
            when (c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> result += CameraSource.Builtin(true, c2.cameraId)
                CameraCharacteristics.LENS_FACING_BACK -> result += CameraSource.Builtin(false, c2.cameraId)
                CameraCharacteristics.LENS_FACING_EXTERNAL -> result += CameraSource.External(c2.cameraId, null)
            }
        }
        // Keep only the first front and first back camera (multi-lens phones list several).
        val builtins = result.filterIsInstance<CameraSource.Builtin>()
        val keep = listOfNotNull(builtins.firstOrNull { it.front }, builtins.firstOrNull { !it.front })
        result.removeAll { it is CameraSource.Builtin && it !in keep }

        val externals = result.filterIsInstance<CameraSource.External>()
        val usbVideo = usbVideoDevices(context)
        // Give exposed external cameras a readable name when exactly one USB video device is present.
        if (externals.size == 1 && usbVideo.size == 1) {
            val i = result.indexOf(externals[0])
            result[i] = externals[0].copy(label = usbVideo[0].label)
        } else if (externals.isEmpty()) {
            // USB video devices that the system did not turn into cameras.
            result += usbVideo
        }
        result.sortedBy { when (it) { is CameraSource.External -> 0; is CameraSource.Builtin -> if (it.front) 1 else 2; is CameraSource.UsbVideoOnly -> 3 } }
    }

    @SuppressLint("MissingPermission")
    private fun usbVideoDevices(context: Context): List<CameraSource.UsbVideoOnly> {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return usb.deviceList.values.filter(::isVideoDevice).map { d ->
            // Product names may need USB permission on Android 10+; fall back to vendor/product IDs.
            val name = runCatching { listOfNotNull(d.manufacturerName, d.productName).joinToString(" ") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "USB %04X:%04X".format(d.vendorId, d.productId)
            CameraSource.UsbVideoOnly(d.deviceName, name, d.vendorId, d.productId)
        }
    }

    private fun isVideoDevice(d: UsbDevice): Boolean =
        d.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }

    /** CameraX selector for a usable source (null for USB devices the system does not expose). */
    @OptIn(ExperimentalCamera2Interop::class)
    fun selectorFor(source: CameraSource): CameraSelector? {
        val id = when (source) {
            is CameraSource.Builtin -> source.cameraId
            is CameraSource.External -> source.cameraId
            is CameraSource.UsbVideoOnly -> return null
        }
        return CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { runCatching { Camera2CameraInfo.from(it).cameraId }.getOrNull() == id } }
            .build()
    }
}
