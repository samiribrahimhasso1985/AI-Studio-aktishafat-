package com.iktishafat.teleprompter.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.assistant.SpeechDurationEstimator
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.domain.repository.ScriptRepository
import com.iktishafat.teleprompter.data.importer.DocumentImporter
import com.iktishafat.teleprompter.ui.common.Pill
import com.iktishafat.teleprompter.ui.common.languageLabel
import com.iktishafat.teleprompter.ui.common.rememberViewModel
import com.iktishafat.teleprompter.ui.theme.StudioColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryEvent {
    data class Open(val id: Long) : LibraryEvent
    data class Error(val message: String) : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repo: ScriptRepository,
    private val importer: DocumentImporter,
) : ViewModel() {
    val query = MutableStateFlow("")
    val scripts: StateFlow<List<Script>?> = query
        .flatMapLatest { repo.observeScripts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val importing = MutableStateFlow(false)

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun create(untitled: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = repo.save(Script(title = untitled, body = "", language = ScriptLanguage.ENGLISH, createdAt = now, updatedAt = now))
        _events.send(LibraryEvent.Open(id))
    }

    fun import(uri: Uri) = viewModelScope.launch {
        importing.value = true
        runCatching {
            val doc = importer.import(uri)
            val now = System.currentTimeMillis()
            repo.save(Script(title = doc.title, body = doc.text, language = ScriptLanguage.detect(doc.text), createdAt = now, updatedAt = now))
        }.onSuccess { _events.send(LibraryEvent.Open(it)) }
            .onFailure { _events.send(LibraryEvent.Error(it.message ?: it.javaClass.simpleName)) }
        importing.value = false
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun duplicate(id: Long, suffix: String) = viewModelScope.launch { repo.duplicate(id, suffix) }
    fun togglePin(script: Script) = viewModelScope.launch { repo.save(script.copy(pinned = !script.pinned)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpen: (Long) -> Unit,
    onPrompt: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val vm = rememberViewModel { LibraryViewModel(container.scripts, container.importer) }
    val scripts by vm.scripts.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Script?>(null) }
    val untitled = stringResource(R.string.untitled)
    val copySuffix = stringResource(R.string.copy_suffix)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is LibraryEvent.Open -> onOpen(e.id)
                is LibraryEvent.Error -> snackbar.showSnackbar(context.getString(R.string.import_failed, e.message))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).background(StudioColors.Gold, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("ا", color = StudioColors.OnGold, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.library_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    TextButton(onClick = { picker.launch(DocumentImporter.MIME_TYPES) }, modifier = Modifier.testTag("import")) {
                        Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.import_action))
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.create(untitled) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_script)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_script"),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val list = scripts
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyLibrary(searching = query.isNotBlank())
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            onOpen = { onOpen(script.id) },
                            onPrompt = { onPrompt(script.id) },
                            onDuplicate = { vm.duplicate(script.id, copySuffix) },
                            onPin = { vm.togglePin(script) },
                            onDelete = { pendingDelete = script },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, s.title)) },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); pendingDelete = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EmptyLibrary(searching: Boolean) {
    Column(Modifier.fillMaxSize().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (searching) R.string.no_results else R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        if (!searching) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScriptCard(
    script: Script,
    onOpen: () -> Unit,
    onPrompt: () -> Unit,
    onDuplicate: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val duration = remember(script.body, script.language) { SpeechDurationEstimator.estimate(script.body, script.language) }
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(184.dp),
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (script.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    script.title,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (script.pinned) R.string.unpin else R.string.pin)) },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { menu = false; onPin() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(
                script.body.ifBlank { stringResource(R.string.empty_script) },
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(languageLabel(script.language))
                Text(
                    stringResource(R.string.words_and_duration, script.wordCount, duration.formatted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = onPrompt, enabled = script.body.isNotBlank()) {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.open_prompter))
                }
            }
        }
    }
}
