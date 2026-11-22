package com.iktishafat.teleprompter.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.domain.assistant.AssistantResult
import com.iktishafat.teleprompter.domain.assistant.AssistantTask
import com.iktishafat.teleprompter.domain.assistant.DurationEstimate
import com.iktishafat.teleprompter.domain.assistant.SpeechDurationEstimator
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SaveState { SAVED, PENDING, SAVING }

data class AssistantUiState(
    val running: Boolean = false,
    val task: AssistantTask? = null,
    val result: AssistantResult? = null,
    val error: String? = null,
    val cloud: Boolean = false,
)

@OptIn(FlowPreview::class)
class EditorViewModel(private val container: AppContainer, private val scriptId: Long) : ViewModel() {
    private val repo = container.scripts
    private var original: Script? = null
    private var languageChosenManually = false

    var loaded by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var language by mutableStateOf(ScriptLanguage.ENGLISH)
        private set
    var saveState by mutableStateOf(SaveState.SAVED)
        private set
    var duration by mutableStateOf(DurationEstimate(0, 0.0, 0))
        private set
    var assistant by mutableStateOf(AssistantUiState())
        private set
    /** Body before the last applied assistant result, for one-step undo. */
    var undoBody by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val s = repo.getScript(scriptId)
            original = s
            if (s != null) {
                title = s.title
                body = TextFieldValue(s.body)
                language = s.language
                languageChosenManually = s.body.isNotBlank()
            }
            recompute()
            loaded = true
            // Autosave: 700 ms after the last keystroke.
            snapshotFlow { Triple(title, body.text, language) }
                .drop(1)
                .debounce(700)
                .collect { save() }
        }
        viewModelScope.launch {
            assistant = assistant.copy(cloud = container.settings.settings.first().let { it.aiCloudEnabled && it.aiApiKey.isNotBlank() })
        }
    }

    private fun recompute() {
        duration = SpeechDurationEstimator.estimate(body.text, language)
    }

    fun onTitle(value: String) { title = value; saveState = SaveState.PENDING }

    fun onBody(value: TextFieldValue) {
        val textChanged = value.text != body.text
        body = value
        if (textChanged) {
            saveState = SaveState.PENDING
            if (!languageChosenManually && value.text.length >= 20) language = ScriptLanguage.detect(value.text)
            recompute()
        }
    }

    fun onLanguage(l: ScriptLanguage) {
        languageChosenManually = true
        language = l
        saveState = SaveState.PENDING
        recompute()
    }

    /** Inserts a marker such as `[PAUSE]` or a scene heading at the cursor. */
    fun insert(marker: String) {
        val v = body
        val start = v.selection.min
        val end = v.selection.max
        val before = v.text.substring(0, start)
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() && !marker.startsWith("\n")
        val insertion = (if (needsSpace) " " else "") + marker
        val text = before + insertion + v.text.substring(end)
        onBody(TextFieldValue(text, TextRange(start + insertion.length)))
    }

    private fun current(): Script? {
        val o = original ?: return null
        return o.copy(title = title.ifBlank { o.title }, body = body.text, language = language)
    }

    private suspend fun save() {
        val s = current() ?: return
        saveState = SaveState.SAVING
        repo.save(s)
        original = s
        saveState = SaveState.SAVED
    }

    fun saveNow() = viewModelScope.launch { save() }

    fun runAssistant(task: AssistantTask) {
        val text = body.text
        if (text.isBlank() || assistant.running) return
        assistant = assistant.copy(running = true, task = task, result = null, error = null)
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            val engine = container.assistants.pick(settings)
            runCatching { engine.run(task, text, language) }
                .onSuccess { assistant = assistant.copy(running = false, result = it) }
                .onFailure { assistant = assistant.copy(running = false, error = it.message ?: it.javaClass.simpleName) }
        }
    }

    fun applyAssistant() {
        val r = assistant.result ?: return
        undoBody = body.text
        onBody(TextFieldValue(r.text, TextRange(r.text.length)))
        assistant = assistant.copy(result = null, task = null)
    }

    fun discardAssistant() { assistant = assistant.copy(result = null, task = null, error = null) }

    fun undoAssistant() {
        val u = undoBody ?: return
        undoBody = null
        onBody(TextFieldValue(u))
    }

    override fun onCleared() {
        // Make sure the last edits are written even if the debounce has not fired yet.
        val s = current()
        if (s != null && saveState != SaveState.SAVED) container.appScope.launch { repo.save(s) }
    }
}
