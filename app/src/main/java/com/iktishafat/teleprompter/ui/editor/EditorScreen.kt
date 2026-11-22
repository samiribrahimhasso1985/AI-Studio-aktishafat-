package com.iktishafat.teleprompter.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.assistant.AssistantTask
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.ui.common.languageLabel
import com.iktishafat.teleprompter.ui.common.rememberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    container: AppContainer,
    scriptId: Long,
    onBack: () -> Unit,
    onPrompter: () -> Unit,
    onStudio: () -> Unit,
) {
    val vm = rememberViewModel(key = "editor-$scriptId") { EditorViewModel(container, scriptId) }
    var showAssistant by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = { vm.saveNow(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    Text(
                        when (vm.saveState) {
                            SaveState.SAVED -> stringResource(R.string.saved)
                            SaveState.PENDING, SaveState.SAVING -> stringResource(R.string.saving)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    if (vm.undoBody != null) {
                        IconButton(onClick = vm::undoAssistant) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
                    }
                    TextButton(onClick = { showAssistant = true }, enabled = vm.body.text.isNotBlank()) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.assistant))
                    }
                    TextButton(onClick = { vm.saveNow(); onStudio() }, enabled = vm.body.text.isNotBlank()) {
                        Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_studio))
                    }
                    Button(
                        onClick = { vm.saveNow(); onPrompter() },
                        enabled = vm.body.text.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp).testTag("start_prompter"),
                    ) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_prompter))
                    }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().imePadding()) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    EditorPane(vm, Modifier.weight(1f).fillMaxSize())
                    Spacer(Modifier.width(20.dp))
                    SidePanel(vm, Modifier.width(300.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    SidePanel(vm, Modifier.fillMaxWidth(), compact = true)
                    Spacer(Modifier.height(8.dp))
                    EditorPane(vm, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    if (showAssistant) {
        AssistantSheet(vm, onDismiss = { vm.discardAssistant(); showAssistant = false }, onApplied = { showAssistant = false })
    }
}

@Composable
private fun EditorPane(vm: EditorViewModel, modifier: Modifier) {
    val scroll = rememberScrollState()
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.verticalScroll(scroll).padding(24.dp)) {
            BasicTextField(
                value = vm.title,
                onValueChange = vm::onTitle,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("title_field"),
                decorationBox = { inner ->
                    if (vm.title.isEmpty()) Text(stringResource(R.string.title_hint), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            BasicTextField(
                value = vm.body,
                onValueChange = vm::onBody,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp, lineHeight = 32.sp,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).testTag("body_field"),
                decorationBox = { inner ->
                    Box {
                        if (vm.body.text.isEmpty()) Text(stringResource(R.string.body_hint), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidePanel(vm: EditorViewModel, modifier: Modifier, compact: Boolean = false) {
    val pause = if (vm.language == ScriptLanguage.ARABIC) "[وقفة]" else "[PAUSE]"
    val breath = if (vm.language == ScriptLanguage.ARABIC) "[نفس]" else "[BREATH]"
    val scene = "\n\n## " + stringResource(R.string.scene_heading) + "\n"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.duration.formatted, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.estimated_duration_words, vm.duration.words), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!compact) Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScriptLanguage.entries.forEach { l ->
                FilterChip(selected = vm.language == l, onClick = { vm.onLanguage(l) }, label = { Text(languageLabel(l)) })
            }
            if (compact) {
                AssistChip(onClick = { vm.insert(pause) }, label = { Text(pause) })
                AssistChip(onClick = { vm.insert(breath) }, label = { Text(breath) })
                AssistChip(onClick = { vm.insert(scene) }, label = { Text("## " + stringResource(R.string.scene_heading)) })
            }
        }
        if (!compact) {
            Text(stringResource(R.string.markers), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.insert(pause) }, label = { Text(pause) })
                AssistChip(onClick = { vm.insert(breath) }, label = { Text(breath) })
                AssistChip(onClick = { vm.insert(scene) }, label = { Text("## " + stringResource(R.string.scene_heading)) })
            }
            Text(stringResource(R.string.markers_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSheet(vm: EditorViewModel, onDismiss: () -> Unit, onApplied: () -> Unit) {
    val state = vm.assistant
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.assistant), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(if (state.cloud) Icons.Default.CloudQueue else Icons.Default.OfflineBolt, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(if (state.cloud) R.string.ai_cloud_badge else R.string.ai_offline_badge)) },
                )
            }
            Spacer(Modifier.height(16.dp))
            val result = state.result
            when {
                state.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.ai_running))
                }
                result != null -> {
                    Text(stringResource(R.string.ai_preview_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        Text(
                            result.text,
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        )
                    }
                    if (result.notes.isNotEmpty()) {
                        Text(result.notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.applyAssistant(); onApplied() }) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ai_apply)) }
                        OutlinedButton(onClick = vm::discardAssistant) { Text(stringResource(R.string.ai_discard)) }
                    }
                }
                else -> {
                    state.error?.let {
                        Text(stringResource(R.string.ai_error, it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    TaskCard(stringResource(R.string.ai_improve), stringResource(R.string.ai_improve_desc)) { vm.runAssistant(AssistantTask.IMPROVE_FOR_SPEECH) }
                    TaskCard(stringResource(R.string.ai_pauses), stringResource(R.string.ai_pauses_desc)) { vm.runAssistant(AssistantTask.ADD_PAUSES) }
                    TaskCard(stringResource(R.string.ai_scenes), stringResource(R.string.ai_scenes_desc)) { vm.runAssistant(AssistantTask.SPLIT_SCENES) }
                    Text(stringResource(R.string.ai_optional_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
