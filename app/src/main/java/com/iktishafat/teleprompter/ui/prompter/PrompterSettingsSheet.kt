package com.iktishafat.teleprompter.ui.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.TextColorOption
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrompterSettingsSheet(
    settings: PrompterSettings,
    onChange: ((PrompterSettings) -> PrompterSettings) -> Unit,
    onDismiss: () -> Unit,
    showOverlayOpacity: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.prompter_settings), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            extra()
            LabeledSlider(stringResource(R.string.speed), settings.speedDpPerSecond, PrompterSettings.MIN_SPEED..PrompterSettings.MAX_SPEED, "${settings.speedDpPerSecond.roundToInt()}") { v -> onChange { it.copy(speedDpPerSecond = v) } }
            LabeledSlider(stringResource(R.string.font_size), settings.fontSizeSp, PrompterSettings.MIN_FONT..PrompterSettings.MAX_FONT, "${settings.fontSizeSp.roundToInt()} sp") { v -> onChange { it.copy(fontSizeSp = v) } }
            LabeledSlider(stringResource(R.string.line_spacing), settings.lineSpacing, 1f..2.5f, "%.2f×".format(settings.lineSpacing)) { v -> onChange { it.copy(lineSpacing = v) } }
            LabeledSlider(stringResource(R.string.paragraph_spacing), settings.paragraphSpacingDp, 0f..100f, "${settings.paragraphSpacingDp.roundToInt()} dp") { v -> onChange { it.copy(paragraphSpacingDp = v) } }
            LabeledSlider(stringResource(R.string.margins), settings.horizontalMarginFraction, 0f..0.3f, "${(settings.horizontalMarginFraction * 100).roundToInt()}%") { v -> onChange { it.copy(horizontalMarginFraction = v) } }
            LabeledSlider(stringResource(R.string.reading_line_position), settings.readingLineFraction, 0.1f..0.6f, "${(settings.readingLineFraction * 100).roundToInt()}%") { v -> onChange { it.copy(readingLineFraction = v) } }
            if (showOverlayOpacity) {
                LabeledSlider(stringResource(R.string.overlay_opacity), settings.overlayOpacity, 0f..1f, "${(settings.overlayOpacity * 100).roundToInt()}%") { v -> onChange { it.copy(overlayOpacity = v) } }
            } else {
                LabeledSlider(stringResource(R.string.background_opacity), settings.backgroundOpacity, 0f..1f, "${(settings.backgroundOpacity * 100).roundToInt()}%") { v -> onChange { it.copy(backgroundOpacity = v) } }
            }
            LabeledSlider(stringResource(R.string.countdown), settings.countdownSeconds.toFloat(), 0f..10f, "${settings.countdownSeconds} s", steps = 9) { v -> onChange { it.copy(countdownSeconds = v.roundToInt()) } }

            Text(stringResource(R.string.text_color), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                TextColorOption.entries.forEach { c ->
                    val selected = settings.textColor == c
                    Box(
                        Modifier.size(40.dp)
                            .background(Color(c.argb), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onChange { it.copy(textColor = c) } }
                            .semantics { contentDescription = c.name },
                    )
                }
            }
            SwitchRow(stringResource(R.string.mirror_horizontal), settings.mirrorHorizontal) { v -> onChange { it.copy(mirrorHorizontal = v) } }
            SwitchRow(stringResource(R.string.mirror_vertical), settings.mirrorVertical) { v -> onChange { it.copy(mirrorVertical = v) } }
            SwitchRow(stringResource(R.string.show_reading_line), settings.showReadingLine) { v -> onChange { it.copy(showReadingLine = v) } }
            SwitchRow(stringResource(R.string.tap_to_control), settings.tapToControl) { v -> onChange { it.copy(tapToControl = v) } }
            SwitchRow(stringResource(R.string.voice_tracking), settings.voiceTracking) { v -> onChange { it.copy(voiceTracking = v) } }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onValue: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
