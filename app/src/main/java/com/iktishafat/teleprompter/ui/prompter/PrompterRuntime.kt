package com.iktishafat.teleprompter.ui.prompter

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.platform.speech.NarrationPlayer
import com.iktishafat.teleprompter.platform.speech.NarrationStatus
import com.iktishafat.teleprompter.platform.speech.VoiceStatus
import com.iktishafat.teleprompter.platform.speech.VoiceTracker

@Stable
class PrompterRuntime(
    val controller: PrompterController,
    val voice: VoiceTracker,
    val narration: NarrationPlayer,
) {
    var narrating by mutableStateOf(false)
    var micPermissionDenied by mutableStateOf(false)
    /** Set when the user asked for narration but no TTS voice exists for the script language. */
    var narrationUnavailable by mutableStateOf(false)
}

private fun now() = SystemClock.uptimeMillis()

/**
 * Wires a [PrompterController] to everything around it: settings, speech recognition,
 * text-to-speech narration, the Bluetooth remote bus and the saved reading position.
 * Shared by the full-screen prompter and the camera studio overlay.
 */
@Composable
fun rememberPrompterRuntime(
    container: AppContainer,
    script: Script,
    settings: PrompterSettings,
    onRemoteCommand: (RemoteCommand) -> Boolean = { false },
    onSpeedPersist: (Float) -> Unit,
    onSaveLastRead: (Int) -> Unit,
): PrompterRuntime {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val runtime = remember(script.id, script.body) {
        val controller = PrompterController(script.body, script.language, settings.speedDpPerSecond * density, settings.countdownSeconds)
        PrompterRuntime(
            controller = controller,
            voice = VoiceTracker(context) { text -> controller.onHypothesis(text, now()) },
            narration = NarrationPlayer(context, onWord = { off -> controller.onExternalWord(off, now()) }),
        )
    }
    val c = runtime.controller
    SideEffect { c.onSpeedChanged = { px -> onSpeedPersist(px / density) } }

    // Settings → controller
    LaunchedEffect(settings.speedDpPerSecond, density) {
        if (!(c.voiceMode && c.voiceActive)) c.applySpeed(settings.speedDpPerSecond * density)
    }
    LaunchedEffect(settings.countdownSeconds) { c.applyCountdown(settings.countdownSeconds) }

    // Microphone permission for voice tracking
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        runtime.micPermissionDenied = !granted
    }
    val wantsVoice = settings.voiceTracking && !runtime.narrating
    LaunchedEffect(wantsVoice) {
        if (wantsVoice && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val micGranted = !runtime.micPermissionDenied
    LifecycleStartEffect(wantsVoice, micGranted, script.language) {
        if (wantsVoice) runtime.voice.start(script.language.speechTag)
        onStopOrDispose { runtime.voice.stop() }
    }
    val voiceStatus by runtime.voice.status.collectAsState()
    SideEffect {
        c.voiceMode = wantsVoice || runtime.narrating
        c.voiceActive = runtime.narrating || voiceStatus == VoiceStatus.LISTENING || voiceStatus == VoiceStatus.STARTING
    }

    // Narration finished or paused → stop following it.
    val narrationStatus by runtime.narration.status.collectAsState()
    LaunchedEffect(narrationStatus) {
        if (runtime.narrating && narrationStatus != NarrationStatus.SPEAKING) {
            runtime.narrating = false
            c.stop()
        }
    }
    LaunchedEffect(settings.ttsVoice, settings.ttsRate, settings.ttsPitch, settings.pronunciations, narrationStatus == NarrationStatus.INITIALIZING) {
        runtime.narration.configure(script.language.speechTag, settings.ttsVoice, settings.ttsRate, settings.ttsPitch, settings.pronunciations)
    }
    DisposableEffect(runtime) { onDispose { runtime.narration.release() } }

    // Bluetooth remote / keyboard
    LaunchedEffect(runtime) {
        container.remote.commands.collect { cmd ->
            if (!onRemoteCommand(cmd)) c.handle(cmd, now())
        }
    }

    // Resume where the presenter stopped last time, once text has been laid out.
    LaunchedEffect(runtime) {
        var tries = 0
        while (tries++ < 120) {
            withFrameNanos { }
            if (c.viewportHeight > 0f && c.restoreOffset(script.lastReadOffset, now())) break
        }
    }
    DisposableEffect(runtime) { onDispose { onSaveLastRead(c.offsetAtReadingLine()) } }

    return runtime
}

/** Starts or stops text-to-speech narration from the paragraph at the reading line. */
fun PrompterRuntime.toggleNarration() {
    if (narrating) {
        narration.pause()
        narrating = false
        controller.stop()
    } else {
        val st = narration.status.value
        if (st == NarrationStatus.LANGUAGE_MISSING || st == NarrationStatus.UNAVAILABLE) {
            narrationUnavailable = true
            return
        }
        narrationUnavailable = false
        val offset = controller.offsetAtReadingLine()
        val from = controller.paragraphs.indexOfFirst { offset < it.end }.coerceAtLeast(0)
        voice.stop()
        narrating = true
        narration.play(controller.paragraphs, from)
    }
}
