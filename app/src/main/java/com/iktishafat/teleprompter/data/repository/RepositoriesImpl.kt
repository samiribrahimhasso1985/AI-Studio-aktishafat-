package com.iktishafat.teleprompter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iktishafat.teleprompter.data.db.ScriptDao
import com.iktishafat.teleprompter.data.db.toDomain
import com.iktishafat.teleprompter.data.db.toEntity
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.TextColorOption
import com.iktishafat.teleprompter.domain.model.VideoQualityOption
import com.iktishafat.teleprompter.domain.repository.ScriptRepository
import com.iktishafat.teleprompter.domain.repository.SettingsRepository
import com.iktishafat.teleprompter.platform.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ScriptRepositoryImpl(
    private val dao: ScriptDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScriptRepository {

    override fun observeScripts(query: String): Flow<List<Script>> {
        val source = if (query.isBlank()) dao.observeAll() else dao.search(query.trim())
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeScript(id: Long): Flow<Script?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getScript(id: Long): Script? = dao.getById(id)?.toDomain()

    override suspend fun save(script: Script): Long {
        val now = clock()
        return if (script.id == 0L) {
            val created = if (script.createdAt == 0L) now else script.createdAt
            dao.insert(script.copy(createdAt = created, updatedAt = now).toEntity())
        } else {
            dao.update(script.copy(updatedAt = now).toEntity())
            script.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun setLastReadOffset(id: Long, offset: Int) = dao.setLastReadOffset(id, offset)

    override suspend fun duplicate(id: Long, copySuffix: String): Long? {
        val original = dao.getById(id)?.toDomain() ?: return null
        return save(original.copy(id = 0, title = "${original.title} $copySuffix", pinned = false, createdAt = 0))
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "prompter_settings")

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsStore

    private object K {
        val fontSize = floatPreferencesKey("font_size")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val paragraphSpacing = floatPreferencesKey("paragraph_spacing")
        val speed = floatPreferencesKey("speed")
        val mirrorH = booleanPreferencesKey("mirror_h")
        val mirrorV = booleanPreferencesKey("mirror_v")
        val countdown = intPreferencesKey("countdown")
        val bgOpacity = floatPreferencesKey("bg_opacity")
        val textColor = stringPreferencesKey("text_color")
        val readingLine = floatPreferencesKey("reading_line")
        val margin = floatPreferencesKey("margin")
        val showReadingLine = booleanPreferencesKey("show_reading_line")
        val tapToControl = booleanPreferencesKey("tap_to_control")
        val voice = booleanPreferencesKey("voice_tracking")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val frontCamera = booleanPreferencesKey("front_camera")
        val recordAudio = booleanPreferencesKey("record_audio")
        val videoQuality = stringPreferencesKey("video_quality")
        val overlayOpacity = floatPreferencesKey("overlay_opacity")
        val aiCloud = booleanPreferencesKey("ai_cloud")
        val aiKey = stringPreferencesKey("ai_key")
        val aiModel = stringPreferencesKey("ai_model")
        val ttsRate = floatPreferencesKey("tts_rate")
        val ttsPitch = floatPreferencesKey("tts_pitch")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val pronunciations = stringPreferencesKey("pronunciations")
    }

    private fun read(p: Preferences): PrompterSettings {
        val d = PrompterSettings()
        return PrompterSettings(
            fontSizeSp = p[K.fontSize] ?: d.fontSizeSp,
            lineSpacing = p[K.lineSpacing] ?: d.lineSpacing,
            paragraphSpacingDp = p[K.paragraphSpacing] ?: d.paragraphSpacingDp,
            speedDpPerSecond = p[K.speed] ?: d.speedDpPerSecond,
            mirrorHorizontal = p[K.mirrorH] ?: d.mirrorHorizontal,
            mirrorVertical = p[K.mirrorV] ?: d.mirrorVertical,
            countdownSeconds = p[K.countdown] ?: d.countdownSeconds,
            backgroundOpacity = p[K.bgOpacity] ?: d.backgroundOpacity,
            textColor = p[K.textColor]?.let { n -> TextColorOption.entries.firstOrNull { it.name == n } } ?: d.textColor,
            readingLineFraction = p[K.readingLine] ?: d.readingLineFraction,
            horizontalMarginFraction = p[K.margin] ?: d.horizontalMarginFraction,
            showReadingLine = p[K.showReadingLine] ?: d.showReadingLine,
            tapToControl = p[K.tapToControl] ?: d.tapToControl,
            voiceTracking = p[K.voice] ?: d.voiceTracking,
            volumeKeysControlPrompter = p[K.volumeKeys] ?: d.volumeKeysControlPrompter,
            keepScreenOn = p[K.keepScreenOn] ?: d.keepScreenOn,
            useFrontCamera = p[K.frontCamera] ?: d.useFrontCamera,
            recordAudio = p[K.recordAudio] ?: d.recordAudio,
            videoQuality = p[K.videoQuality]?.let { n -> VideoQualityOption.entries.firstOrNull { it.name == n } } ?: d.videoQuality,
            overlayOpacity = p[K.overlayOpacity] ?: d.overlayOpacity,
            aiCloudEnabled = p[K.aiCloud] ?: d.aiCloudEnabled,
            aiApiKey = p[K.aiKey]?.let { SecretCipher.decrypt(it) } ?: d.aiApiKey,
            aiModel = p[K.aiModel] ?: d.aiModel,
            ttsRate = p[K.ttsRate] ?: d.ttsRate,
            ttsPitch = p[K.ttsPitch] ?: d.ttsPitch,
            ttsVoice = p[K.ttsVoice] ?: d.ttsVoice,
            pronunciations = p[K.pronunciations] ?: d.pronunciations,
        )
    }

    override val settings: Flow<PrompterSettings> = store.data.map { read(it) }.distinctUntilChanged()

    override suspend fun update(transform: (PrompterSettings) -> PrompterSettings) {
        store.edit { p ->
            val current = read(p)
            val n = transform(current)
            p[K.fontSize] = n.fontSizeSp.coerceIn(PrompterSettings.MIN_FONT, PrompterSettings.MAX_FONT)
            p[K.lineSpacing] = n.lineSpacing.coerceIn(1f, 3f)
            p[K.paragraphSpacing] = n.paragraphSpacingDp.coerceIn(0f, 120f)
            p[K.speed] = n.speedDpPerSecond.coerceIn(PrompterSettings.MIN_SPEED, PrompterSettings.MAX_SPEED)
            p[K.mirrorH] = n.mirrorHorizontal
            p[K.mirrorV] = n.mirrorVertical
            p[K.countdown] = n.countdownSeconds.coerceIn(0, 10)
            p[K.bgOpacity] = n.backgroundOpacity.coerceIn(0f, 1f)
            p[K.textColor] = n.textColor.name
            p[K.readingLine] = n.readingLineFraction.coerceIn(0.1f, 0.6f)
            p[K.margin] = n.horizontalMarginFraction.coerceIn(0f, 0.3f)
            p[K.showReadingLine] = n.showReadingLine
            p[K.tapToControl] = n.tapToControl
            p[K.voice] = n.voiceTracking
            p[K.volumeKeys] = n.volumeKeysControlPrompter
            p[K.keepScreenOn] = n.keepScreenOn
            p[K.frontCamera] = n.useFrontCamera
            p[K.recordAudio] = n.recordAudio
            p[K.videoQuality] = n.videoQuality.name
            p[K.overlayOpacity] = n.overlayOpacity.coerceIn(0f, 1f)
            p[K.aiCloud] = n.aiCloudEnabled
            p[K.aiKey] = SecretCipher.encrypt(n.aiApiKey.trim())
            p[K.aiModel] = n.aiModel.trim().ifEmpty { PrompterSettings.DEFAULT_AI_MODEL }
            p[K.ttsRate] = n.ttsRate.coerceIn(0.3f, 2.5f)
            p[K.ttsPitch] = n.ttsPitch.coerceIn(0.5f, 2f)
            p[K.ttsVoice] = n.ttsVoice
            p[K.pronunciations] = n.pronunciations
        }
    }
}
