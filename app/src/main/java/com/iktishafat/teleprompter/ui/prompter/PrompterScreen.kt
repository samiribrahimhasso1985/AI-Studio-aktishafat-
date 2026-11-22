package com.iktishafat.teleprompter.ui.prompter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.assistant.DurationEstimate
import com.iktishafat.teleprompter.domain.engine.PrompterState
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.platform.speech.NarrationStatus
import com.iktishafat.teleprompter.platform.speech.VoiceStatus
import com.iktishafat.teleprompter.ui.common.ImmersiveMode
import com.iktishafat.teleprompter.ui.common.KeepScreenOn
import com.iktishafat.teleprompter.ui.common.rememberViewModel
import com.iktishafat.teleprompter.ui.theme.StudioColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PrompterScreen(container: AppContainer, scriptId: Long, onExit: () -> Unit) {
    val vm = rememberViewModel(key = "prompter-$scriptId") { PrompterViewModel(container, scriptId) }
    val script by vm.script.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = script
    val st = settings
    if (s == null || st == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    ImmersiveMode()
    KeepScreenOn(st.keepScreenOn)

    val runtime = rememberPrompterRuntime(
        container = container,
        script = s,
        settings = st,
        onRemoteCommand = { cmd ->
            when (cmd) {
                RemoteCommand.EXIT -> { onExit(); true }
                RemoteCommand.TOGGLE_MIRROR -> { vm.update { it.copy(mirrorHorizontal = !it.mirrorHorizontal) }; true }
                else -> false
            }
        },
        onSpeedPersist = { dp -> vm.update { it.copy(speedDpPerSecond = dp) } },
        onSaveLastRead = vm::saveLastRead,
    )
    val c = runtime.controller

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSettings by remember { mutableStateOf(false) }
    fun touch() { controlsVisible = true; lastInteraction = System.currentTimeMillis() }

    // Auto-hide controls while scrolling.
    LaunchedEffect(lastInteraction, c.state) {
        if (c.state == PrompterState.PLAYING) { delay(3500); controlsVisible = false } else controlsVisible = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PrompterSurface(
            controller = c,
            settings = st,
            backgroundOpacity = st.backgroundOpacity,
            modifier = Modifier.fillMaxSize(),
            onTap = { touch() },
            onDragEnd = { touch(); if (c.voiceMode) c.resyncVoiceToReadingLine(android.os.SystemClock.uptimeMillis()) },
        )

        if (c.state == PrompterState.COUNTDOWN) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text("${c.countdown}", fontSize = 180.sp, fontWeight = FontWeight.Bold, color = StudioColors.Gold)
            }
        }

        LinearProgressIndicator(
            progress = { c.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
            color = StudioColors.Gold,
            trackColor = Color.White.copy(alpha = 0.08f),
        )

        VoiceBanner(runtime, st, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))

        AnimatedVisibility(
            visible = controlsVisible || c.state != PrompterState.PLAYING,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar(
                runtime = runtime,
                settings = st,
                onBack = onExit,
                onSettings = { showSettings = true; touch() },
                onChange = { t -> vm.update(t); touch() },
                onInteract = { touch() },
            )
        }
    }

    if (showSettings) {
        PrompterSettingsSheet(settings = st, onChange = vm::update, onDismiss = { showSettings = false })
    }
}

@Composable
private fun VoiceBanner(runtime: PrompterRuntime, settings: PrompterSettings, modifier: Modifier) {
    val c = runtime.controller
    val status by runtime.voice.status.collectAsState()
    val level by runtime.voice.level.collectAsState()
    val narration by runtime.narration.status.collectAsState()
    val (text, color) = when {
        runtime.narrationUnavailable -> stringResource(
            if (narration == NarrationStatus.UNAVAILABLE) R.string.tts_unavailable else R.string.tts_language_missing
        ) to MaterialTheme.colorScheme.error
        runtime.narrating -> stringResource(R.string.narration_playing) to StudioColors.Gold
        !settings.voiceTracking -> return
        runtime.micPermissionDenied || status == VoiceStatus.NO_PERMISSION -> stringResource(R.string.voice_permission_needed) to MaterialTheme.colorScheme.error
        status == VoiceStatus.UNAVAILABLE -> stringResource(R.string.voice_unavailable) to MaterialTheme.colorScheme.error
        status == VoiceStatus.ERROR -> stringResource(R.string.voice_error) to MaterialTheme.colorScheme.error
        c.state == PrompterState.IDLE -> stringResource(R.string.voice_start_speaking) to StudioColors.Listening
        c.speakerPaused -> stringResource(R.string.voice_paused) to StudioColors.TextMuted
        else -> stringResource(R.string.voice_listening, c.wordsPerMinute.roundToInt()) to StudioColors.Listening
    }
    Surface(modifier, color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size((8 + level * 8).dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ControlBar(
    runtime: PrompterRuntime,
    settings: PrompterSettings,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChange: ((PrompterSettings) -> PrompterSettings) -> Unit,
    onInteract: () -> Unit,
) {
    val c = runtime.controller
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    Surface(
        color = StudioColors.Surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, colors = iconColors) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            IconButton(onClick = { c.restart(); onInteract() }, colors = iconColors) { Icon(Icons.Default.Replay, stringResource(R.string.restart)) }

            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.changeSpeed(1f / 1.1f); onInteract() }, colors = iconColors) { Icon(Icons.Default.Remove, stringResource(R.string.slower)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.speed), style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                Text("${settings.speedDpPerSecond.roundToInt()}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            IconButton(onClick = { c.changeSpeed(1.1f); onInteract() }, colors = iconColors) { Icon(Icons.Default.Add, stringResource(R.string.faster)) }

            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = { c.togglePlayPause(); onInteract() },
                modifier = Modifier.size(64.dp).testTag("play_pause"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = StudioColors.Gold, contentColor = StudioColors.OnGold),
            ) {
                val playing = c.state == PrompterState.PLAYING || c.state == PrompterState.COUNTDOWN
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), Modifier.size(34.dp))
            }
            Spacer(Modifier.width(12.dp))

            IconButton(onClick = { onChange { it.copy(fontSizeSp = it.fontSizeSp - 4f) } }, colors = iconColors) { Icon(Icons.Default.TextDecrease, stringResource(R.string.font_smaller)) }
            IconButton(onClick = { onChange { it.copy(fontSizeSp = it.fontSizeSp + 4f) } }, colors = iconColors) { Icon(Icons.Default.TextIncrease, stringResource(R.string.font_larger)) }
            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = { onChange { it.copy(mirrorHorizontal = !it.mirrorHorizontal) } },
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (settings.mirrorHorizontal) StudioColors.Gold else Color.White),
            ) { Icon(Icons.Default.Flip, stringResource(R.string.mirror_horizontal)) }
            IconButton(
                onClick = { onChange { it.copy(voiceTracking = !it.voiceTracking) } },
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (settings.voiceTracking) StudioColors.Listening else Color.White),
                modifier = Modifier.testTag("voice_toggle"),
            ) { Icon(if (settings.voiceTracking) Icons.Default.Mic else Icons.Default.MicOff, stringResource(R.string.voice_tracking)) }
            IconButton(
                onClick = { runtime.toggleNarration(); onInteract() },
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (runtime.narrating) StudioColors.Gold else Color.White),
            ) { Icon(Icons.Default.RecordVoiceOver, stringResource(R.string.narrate)) }
            IconButton(onClick = onSettings, colors = iconColors) { Icon(Icons.Default.Tune, stringResource(R.string.prompter_settings)) }
            Text(
                DurationEstimate.formatSeconds(c.remainingSeconds()),
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextMuted,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
        }
    }
}
