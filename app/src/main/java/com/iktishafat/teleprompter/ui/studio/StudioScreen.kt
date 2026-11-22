package com.iktishafat.teleprompter.ui.studio

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.engine.PrompterState
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.VideoQualityOption
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.platform.camera.CameraSource
import com.iktishafat.teleprompter.platform.camera.CameraSourceDetector
import com.iktishafat.teleprompter.platform.camera.UvcCamera
import com.iktishafat.teleprompter.platform.camera.UvcState
import com.iktishafat.teleprompter.ui.common.ImmersiveMode
import com.iktishafat.teleprompter.ui.common.KeepScreenOn
import com.iktishafat.teleprompter.ui.common.rememberViewModel
import com.iktishafat.teleprompter.ui.prompter.PrompterSettingsSheet
import com.iktishafat.teleprompter.ui.prompter.PrompterSurface
import com.iktishafat.teleprompter.ui.prompter.PrompterViewModel
import com.iktishafat.teleprompter.ui.prompter.rememberPrompterRuntime
import com.iktishafat.teleprompter.ui.theme.StudioColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun qualityOf(o: VideoQualityOption): Quality = when (o) {
    VideoQualityOption.HD -> Quality.HD
    VideoQualityOption.FHD -> Quality.FHD
    VideoQualityOption.UHD -> Quality.UHD
}

private fun freeGigabytes(): Float = runCatching {
    StatFs(Environment.getExternalStorageDirectory().path).availableBytes / 1_000_000_000f
}.getOrDefault(-1f)

@Composable
fun StudioScreen(container: AppContainer, scriptId: Long, onExit: () -> Unit) {
    val vm = rememberViewModel(key = "studio-$scriptId") { PrompterViewModel(container, scriptId) }
    val script by vm.script.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        micGranted = result[Manifest.permission.RECORD_AUDIO] ?: micGranted
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted || !micGranted) launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val s = script
    val st = settings
    if (s == null || st == null) { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    ImmersiveMode()

    if (!cameraGranted) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.camera_permission_needed), color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text(stringResource(R.string.grant)) }
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
        }
        return
    }
    StudioContent(container, vm, s, st, micGranted, onExit)
}

private fun hasPermission(context: Context, p: String) =
    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
private fun StudioContent(
    container: AppContainer,
    vm: PrompterViewModel,
    script: com.iktishafat.teleprompter.domain.model.Script,
    settings: PrompterSettings,
    micGranted: Boolean,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val camera = remember { LifecycleCameraController(context) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var preRollCountdown by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<CameraSource>?>(null) }
    var selected by remember { mutableStateOf<CameraSource?>(null) }
    var showPicker by remember { mutableStateOf(true) }
    var uvcRecording by remember { mutableStateOf(false) }
    val isRecording = recording != null || uvcRecording
    val front = (selected as? CameraSource.Builtin)?.front == true
    val useUvc = selected is CameraSource.UsbVideoOnly
    val uvc = remember { UvcCamera(context.applicationContext) }
    val uvcState by uvc.state.collectAsStateWithLifecycle()
    DisposableEffect(uvc) { onDispose { uvc.release() } }

    KeepScreenOn(true)

    val runtime = rememberPrompterRuntime(
        container = container,
        script = script,
        settings = settings.copy(countdownSeconds = 0), // the studio runs its own pre-roll countdown
        onRemoteCommand = { cmd ->
            when (cmd) {
                RemoteCommand.EXIT -> { if (!isRecording) onExit(); true }
                RemoteCommand.RESTART -> false
                RemoteCommand.TOGGLE_MIRROR -> true
                else -> false
            }
        },
        onSpeedPersist = { dp -> vm.update { it.copy(speedDpPerSecond = dp) } },
        onSaveLastRead = vm::saveLastRead,
    )
    val c = runtime.controller

    // Bind camera
    // Ask which camera to use every time the studio opens; refresh while the picker is open
    // so a USB camera or HDMI capture card appears as soon as it is plugged in.
    LaunchedEffect(showPicker) {
        while (showPicker) {
            sources = CameraSourceDetector.detect(context)
            delay(2000)
        }
    }
    val cameraChosen = selected != null && !useUvc
    DisposableEffect(lifecycleOwner, cameraChosen) {
        if (cameraChosen) {
            camera.setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            camera.bindToLifecycle(lifecycleOwner)
        }
        onDispose { camera.unbind() }
    }
    LaunchedEffect(selected) {
        val src = selected ?: return@LaunchedEffect
        if (src is CameraSource.UsbVideoOnly) {
            // Direct USB driver: Android shows its USB permission dialog the first time.
            val height = when (settings.videoQuality) { VideoQualityOption.HD -> 720; VideoQualityOption.FHD -> 1080; VideoQualityOption.UHD -> 2160 }
            uvc.open(src.deviceName, height)
            torch = false
            return@LaunchedEffect
        }
        uvc.close()
        CameraSourceDetector.selectorFor(src)?.let { sel ->
            runCatching { camera.cameraSelector = sel }.onFailure {
                Toast.makeText(context, context.getString(R.string.camera_open_failed), Toast.LENGTH_LONG).show()
                selected = null; showPicker = true
            }
        }
        if (src !is CameraSource.Builtin || src.front) torch = false
    }
    LaunchedEffect(settings.videoQuality, isRecording) {
        if (isRecording) return@LaunchedEffect
        val q = qualityOf(settings.videoQuality)
        camera.videoCaptureQualitySelector = QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q))
    }
    LaunchedEffect(torch) { runCatching { camera.enableTorch(torch) } }

    fun startRecording() {
        val name = "IKTISHAFAT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Iktishafat")
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        val withAudio = settings.recordAudio && micGranted
        if (useUvc) {
            uvc.startRecording(
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values = values,
                withAudio = withAudio,
                onStart = { uvcRecording = true; recordingStartedAt = System.currentTimeMillis(); elapsed = 0; c.startWithoutCountdown() },
                onSaved = {
                    uvcRecording = false; c.stop()
                    Toast.makeText(context, context.getString(R.string.recording_saved), Toast.LENGTH_LONG).show()
                },
                onError = { msg ->
                    uvcRecording = false; c.stop()
                    Toast.makeText(context, context.getString(R.string.recording_failed, msg), Toast.LENGTH_LONG).show()
                },
            )
            return
        }
        recording = camera.startRecording(output, AudioConfig.create(withAudio), ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> { recordingStartedAt = System.currentTimeMillis(); c.startWithoutCountdown() }
                is VideoRecordEvent.Status -> elapsed = event.recordingStats.recordedDurationNanos / 1_000_000
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    c.stop()
                    val msg = if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                        context.getString(R.string.recording_failed, event.cause?.message ?: event.error.toString())
                    } else context.getString(R.string.recording_saved)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val toggleRecord: () -> Unit = {
        val r = recording
        if (uvcRecording) uvc.stopRecording()
        else if (r != null) r.stop()
        else if (preRollCountdown == 0) preRollCountdown = settings.countdownSeconds.coerceAtLeast(0).let { if (it == 0) -1 else it }
    }
    // Pre-roll countdown, then start recording and prompting together.
    LaunchedEffect(preRollCountdown) {
        when {
            preRollCountdown < 0 -> { preRollCountdown = 0; startRecording() }
            preRollCountdown > 0 -> { delay(1000); preRollCountdown -= 1; if (preRollCountdown == 0) startRecording() }
        }
    }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }
    // Recording timer for the USB driver (CameraX reports its own duration).
    LaunchedEffect(uvcRecording) {
        while (uvcRecording) { elapsed = System.currentTimeMillis() - recordingStartedAt; delay(500) }
    }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (useUvc) {
                UvcPreview(uvc, uvcState, Modifier.fillMaxSize())
            } else {
                AndroidView(
                    factory = { ctx -> PreviewView(ctx).apply { controller = camera; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Prompter overlay at the top edge, next to the front camera on Galaxy Tab S7 Ultra (landscape).
            PrompterSurface(
                controller = c,
                settings = settings,
                backgroundOpacity = settings.overlayOpacity,
                mirror = false,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f).align(Alignment.TopCenter),
            )
            if (preRollCountdown > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$preRollCountdown", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = StudioColors.Gold)
                }
            }
            StatusStrip(isRecording, elapsed, settings, micGranted, runtime.controller.voiceMode, Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
        SideControls(
            isRecording = isRecording,
            canSwitch = true,
            front = front || selected !is CameraSource.Builtin,
            torch = torch,
            playing = c.state == PrompterState.PLAYING,
            voice = settings.voiceTracking,
            onBack = { if (!isRecording) onExit() },
            onSwitch = { showPicker = true },
            onTorch = { torch = !torch },
            onRecord = { toggleRecord() },
            onPlayPause = { c.togglePlayPause() },
            onRestart = { c.restart() },
            onVoice = { vm.update { it.copy(voiceTracking = !it.voiceTracking) } },
            onSettings = { showSettings = true },
        )
    }

    if (showSettings) {
        StudioSettingsSheet(settings, isRecording, onChange = vm::update, onDismiss = { showSettings = false })
    }
    if (showPicker && !isRecording) {
        CameraPicker(
            sources = sources,
            current = selected,
            preferFront = settings.useFrontCamera,
            onPick = { src ->
                if (src is CameraSource.Builtin) vm.update { it.copy(useFrontCamera = src.front) }
                selected = src
                showPicker = false
            },
            onDismiss = { if (selected == null) onExit() else showPicker = false },
        )
    }
}

@Composable
private fun CameraPicker(
    sources: List<CameraSource>?,
    current: CameraSource?,
    preferFront: Boolean,
    onPick: (CameraSource) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(color = StudioColors.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 560.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.choose_camera), style = MaterialTheme.typography.titleLarge, color = Color.White)
                when {
                    sources == null -> Text(stringResource(R.string.camera_searching), color = StudioColors.TextMuted)
                    sources.isEmpty() -> Text(stringResource(R.string.camera_none_found), color = StudioColors.TextMuted)
                    else -> sources.forEach { src ->
                        val usable = true
                        val highlighted = src == current || (current == null && src is CameraSource.Builtin && src.front == preferFront)
                        val (title, subtitle) = when (src) {
                            is CameraSource.Builtin -> stringResource(if (src.front) R.string.camera_front else R.string.camera_back) to stringResource(R.string.camera_builtin_desc)
                            is CameraSource.External -> (src.label ?: stringResource(R.string.camera_external)) to stringResource(R.string.camera_external_desc)
                            is CameraSource.UsbVideoOnly -> src.label to stringResource(R.string.camera_usb_direct)
                        }
                        Surface(
                            color = if (highlighted) StudioColors.SurfaceHighest else StudioColors.SurfaceHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                                .border(if (highlighted) 1.dp else 0.dp, if (highlighted) StudioColors.Gold else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable(enabled = usable) { onPick(src) },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (src) {
                                        is CameraSource.Builtin -> if (src.front) Icons.Default.CameraFront else Icons.Default.CameraRear
                                        is CameraSource.External -> Icons.Default.Videocam
                                        is CameraSource.UsbVideoOnly -> Icons.Default.Usb
                                    },
                                    null,
                                    tint = if (usable) StudioColors.Gold else StudioColors.TextMuted,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, color = if (usable) Color.White else StudioColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                                    Text(subtitle, color = StudioColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.camera_fx3_hint), color = StudioColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusStrip(recording: Boolean, elapsedMs: Long, settings: PrompterSettings, mic: Boolean, voiceMode: Boolean, modifier: Modifier) {
    var free by remember { mutableStateOf(freeGigabytes()) }
    LaunchedEffect(recording) { while (true) { free = freeGigabytes(); delay(10_000) } }
    Surface(modifier, color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recording) {
                Box(Modifier.size(10.dp).background(StudioColors.Live, CircleShape))
                val sec = elapsedMs / 1000
                Text("REC %02d:%02d".format(sec / 60, sec % 60), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Text(settings.videoQuality.name, color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(if (settings.recordAudio && mic) R.string.audio_on else R.string.audio_off), color = Color.White, style = MaterialTheme.typography.labelLarge)
            if (free >= 0f) Text(stringResource(R.string.free_storage, "%.1f".format(free)), color = if (free < 2f) StudioColors.Live else Color.White, style = MaterialTheme.typography.labelLarge)
            if (voiceMode && settings.recordAudio && recording) {
                Text(stringResource(R.string.voice_while_recording_warning), color = StudioColors.Gold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SideControls(
    isRecording: Boolean, canSwitch: Boolean, front: Boolean, torch: Boolean, playing: Boolean, voice: Boolean,
    onBack: () -> Unit, onSwitch: () -> Unit, onTorch: () -> Unit, onRecord: () -> Unit,
    onPlayPause: () -> Unit, onRestart: () -> Unit, onVoice: () -> Unit, onSettings: () -> Unit,
) {
    val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.3f))
    Column(
        Modifier.fillMaxHeight().width(96.dp).background(StudioColors.Surface).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onBack, enabled = !isRecording, colors = white) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        IconButton(onClick = onSwitch, enabled = canSwitch && !isRecording, colors = white) { Icon(Icons.Default.Cameraswitch, stringResource(R.string.switch_camera)) }
        IconButton(onClick = onTorch, enabled = !front, colors = white) { Icon(if (torch) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, stringResource(R.string.torch)) }
        // Record button
        Box(
            Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
                .background(StudioColors.Live, if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .clickable(onClickLabel = null, onClick = onRecord),
        )
        Text(stringResource(if (isRecording) R.string.stop_recording else R.string.record), color = Color.White, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onPlayPause, colors = white) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play)) }
        IconButton(onClick = onRestart, colors = white) { Icon(Icons.Default.Replay, stringResource(R.string.restart)) }
        IconButton(onClick = onVoice, colors = IconButtonDefaults.iconButtonColors(contentColor = if (voice) StudioColors.Listening else Color.White)) {
            Icon(if (voice) Icons.Default.Mic else Icons.Default.MicOff, stringResource(R.string.voice_tracking))
        }
        IconButton(onClick = onSettings, colors = white) { Icon(Icons.Default.Tune, stringResource(R.string.camera_settings)) }
    }
}

@Composable
private fun StudioSettingsSheet(
    settings: PrompterSettings,
    isRecording: Boolean,
    onChange: ((PrompterSettings) -> PrompterSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    PrompterSettingsSheet(settings = settings, onChange = onChange, onDismiss = onDismiss, showOverlayOpacity = true) {
        // Camera options are locked while recording.
        Text(stringResource(R.string.video_quality), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoQualityOption.entries.forEach { q ->
                FilterChip(
                    selected = settings.videoQuality == q,
                    enabled = !isRecording,
                    onClick = { onChange { it.copy(videoQuality = q) } },
                    label = { Text(when (q) { VideoQualityOption.HD -> "720p"; VideoQualityOption.FHD -> "1080p"; VideoQualityOption.UHD -> "4K" }) },
                )
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = settings.recordAudio,
                enabled = !isRecording,
                onClick = { onChange { it.copy(recordAudio = !it.recordAudio) } },
                label = { Text(stringResource(R.string.record_audio)) },
            )
        }
    }
}

/** Live picture from the direct USB driver, letterboxed to the camera's aspect ratio. */
@Composable
private fun UvcPreview(uvc: UvcCamera, state: UvcState, modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = (state as? UvcState.Previewing)?.let { if (it.height > 0) it.width.toFloat() / it.height else null } ?: (16f / 9f)
        AndroidView(
            factory = { ctx ->
                android.view.SurfaceView(ctx).apply {
                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(h: android.view.SurfaceHolder) = uvc.attachSurface(h.surface)
                        override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) = Unit
                        override fun surfaceDestroyed(h: android.view.SurfaceHolder) = uvc.detachSurface(h.surface)
                    })
                }
            },
            modifier = Modifier.fillMaxSize().wrapContentSize().aspectRatio(ratio),
        )
        val message = when (state) {
            UvcState.Idle, UvcState.Opening -> stringResource(R.string.uvc_opening)
            UvcState.WaitingForPermission -> stringResource(R.string.uvc_permission)
            UvcState.PermissionDenied -> stringResource(R.string.uvc_permission_denied)
            UvcState.Disconnected -> stringResource(R.string.uvc_disconnected)
            is UvcState.Failed -> stringResource(R.string.uvc_failed, state.message)
            is UvcState.Previewing -> null
        }
        if (message != null) {
            Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
            }
        }
        if (state is UvcState.Previewing && state.width > 0) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Text("USB ${state.width}×${state.height} · ${state.fps} fps", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}
