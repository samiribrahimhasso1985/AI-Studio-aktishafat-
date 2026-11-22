package com.iktishafat.teleprompter.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iktishafat.teleprompter.AppContainer
import com.iktishafat.teleprompter.BuildConfig
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.platform.speech.NarrationPlayer
import com.iktishafat.teleprompter.platform.speech.NarrationStatus
import com.iktishafat.teleprompter.ui.common.languageLabel
import com.iktishafat.teleprompter.ui.prompter.LabeledSlider
import com.iktishafat.teleprompter.ui.prompter.SwitchRow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle<PrompterSettings?>(initialValue = null)
    val scope = rememberCoroutineScope()
    fun update(t: (PrompterSettings) -> PrompterSettings) { scope.launch { container.settings.update(t) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.settings)) },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 860.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InterfaceLanguageSection()
                Section(stringResource(R.string.section_prompter)) {
                    SwitchRow(stringResource(R.string.keep_screen_on), s.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
                    SwitchRow(stringResource(R.string.voice_tracking), s.voiceTracking) { v -> update { it.copy(voiceTracking = v) } }
                    Text(stringResource(R.string.voice_tracking_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section(stringResource(R.string.section_remote)) {
                    SwitchRow(stringResource(R.string.volume_keys), s.volumeKeysControlPrompter) { v -> update { it.copy(volumeKeysControlPrompter = v) } }
                    Text(stringResource(R.string.remote_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NarrationSection(s, ::update)
                PronunciationSection(s, ::update)
                AiSection(s, ::update)
                Section(stringResource(R.string.about)) {
                    Text("IKTISHAFAT Teleprompter ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceLanguageSection() {
    // Interface language is independent of each script's language.
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    Section(stringResource(R.string.app_language)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to stringResource(R.string.system_default), "ar" to "العربية", "de" to "Deutsch", "en" to "English").forEach { (tag, label) ->
                FilterChip(
                    selected = current == tag,
                    onClick = { AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)) },
                    label = { Text(label) },
                )
            }
        }
        Text(stringResource(R.string.app_language_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NarrationSection(s: PrompterSettings, update: ((PrompterSettings) -> PrompterSettings) -> Unit) {
    val context = LocalContext.current
    val player = remember { NarrationPlayer(context, onWord = {}) }
    DisposableEffect(player) { onDispose { player.release() } }
    val status by player.status.collectAsState()
    val voices by player.voices.collectAsState()
    var testLanguage by rememberSaveable { mutableStateOf(ScriptLanguage.ARABIC) }
    LaunchedEffect(status, testLanguage, s.ttsVoice, s.ttsRate, s.ttsPitch, s.pronunciations) {
        if (status != NarrationStatus.INITIALIZING) player.configure(testLanguage.speechTag, s.ttsVoice, s.ttsRate, s.ttsPitch, s.pronunciations)
    }
    val sample = when (testLanguage) {
        ScriptLanguage.ARABIC -> stringResource(R.string.tts_sample_ar)
        ScriptLanguage.GERMAN -> stringResource(R.string.tts_sample_de)
        ScriptLanguage.ENGLISH -> stringResource(R.string.tts_sample_en)
    }
    Section(stringResource(R.string.section_narration)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScriptLanguage.entries.forEach { l -> FilterChip(selected = testLanguage == l, onClick = { testLanguage = l }, label = { Text(languageLabel(l)) }) }
        }
        when (status) {
            NarrationStatus.UNAVAILABLE -> Text(stringResource(R.string.tts_unavailable), color = MaterialTheme.colorScheme.error)
            NarrationStatus.LANGUAGE_MISSING -> Text(stringResource(R.string.tts_language_missing), color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        if (voices.isNotEmpty()) {
            Text(stringResource(R.string.tts_voice), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = s.ttsVoice.isEmpty(), onClick = { update { it.copy(ttsVoice = "") } }, label = { Text(stringResource(R.string.system_default)) })
                voices.take(12).forEach { v ->
                    val tag = v.locale.toLanguageTag() + if (v.offline) "" else " ☁"
                    FilterChip(selected = s.ttsVoice == v.name, onClick = { update { it.copy(ttsVoice = v.name) } }, label = { Text("$tag · ${v.name.takeLast(18)}") })
                }
            }
            Text(stringResource(R.string.tts_voice_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LabeledSlider(stringResource(R.string.tts_rate), s.ttsRate, 0.5f..2f, "%.2f×".format(s.ttsRate)) { v -> update { it.copy(ttsRate = v) } }
        LabeledSlider(stringResource(R.string.tts_pitch), s.ttsPitch, 0.5f..2f, "%.2f".format(s.ttsPitch)) { v -> update { it.copy(ttsPitch = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { player.preview(sample) }, enabled = status == NarrationStatus.READY) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Text("  " + stringResource(R.string.tts_test))
            }
            TextButton(onClick = {
                try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
            }) { Text(stringResource(R.string.tts_install_voices)) }
        }
    }
}

@Composable
private fun PronunciationSection(s: PrompterSettings, update: ((PrompterSettings) -> PrompterSettings) -> Unit) {
    var text by rememberSaveable(s.pronunciations.isEmpty()) { mutableStateOf(s.pronunciations) }
    Section(stringResource(R.string.section_pronunciation)) {
        Text(stringResource(R.string.pronunciation_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(textDirection = TextDirection.Content),
            placeholder = { Text("أوروك = أُوروك\nAshurbanipal = Aschur-bani-pal") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { update { it.copy(pronunciations = text) } }, enabled = text != s.pronunciations) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun AiSection(s: PrompterSettings, update: ((PrompterSettings) -> PrompterSettings) -> Unit) {
    var key by rememberSaveable { mutableStateOf(s.aiApiKey) }
    var model by rememberSaveable { mutableStateOf(s.aiModel) }
    Section(stringResource(R.string.section_ai)) {
        Text(stringResource(R.string.ai_local_always), style = MaterialTheme.typography.bodyMedium)
        SwitchRow(stringResource(R.string.ai_cloud_enable), s.aiCloudEnabled) { v -> update { it.copy(aiCloudEnabled = v) } }
        if (s.aiCloudEnabled) {
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.ai_api_key)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { update { it.copy(aiApiKey = key, aiModel = model) } }, enabled = key != s.aiApiKey || model != s.aiModel) { Text(stringResource(R.string.save)) }
        }
        Text(stringResource(R.string.ai_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
