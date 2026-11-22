package com.iktishafat.teleprompter.domain.model

/**
 * Languages supported by the script editor, the AI assistant and voice tracking.
 * [speechTag] is the BCP-47 tag passed to Android's SpeechRecognizer.
 */
enum class ScriptLanguage(val speechTag: String, val isRtl: Boolean, val defaultWordsPerMinute: Int) {
    ARABIC("ar-SA", true, 110),
    GERMAN("de-DE", false, 130),
    ENGLISH("en-US", false, 150);

    companion object {
        private val germanHints = setOf(
            "der", "die", "das", "und", "ist", "nicht", "ich", "sie", "mit", "ein", "eine",
            "auf", "für", "wir", "auch", "sich", "dem", "den", "zu", "es"
        )
        private val englishHints = setOf(
            "the", "and", "is", "you", "of", "to", "in", "that", "it", "we", "this", "for",
            "are", "with", "was", "on", "be"
        )

        fun isArabicChar(c: Char): Boolean {
            val code = c.code
            return code in 0x0600..0x06FF || code in 0x0750..0x077F ||
                code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF
        }

        /** Heuristic language detection, good enough to preselect a default. */
        fun detect(text: String): ScriptLanguage {
            var letters = 0
            var arabic = 0
            var umlauts = 0
            for (c in text) {
                if (c.isLetter()) {
                    letters++
                    if (isArabicChar(c)) arabic++
                    if (c in "äöüÄÖÜß") umlauts++
                }
            }
            if (letters == 0) return ENGLISH
            if (arabic.toDouble() / letters > 0.3) return ARABIC
            val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
            val de = words.count { it in germanHints } + umlauts * 2
            val en = words.count { it in englishHints }
            return if (de > en) GERMAN else ENGLISH
        }

        fun fromName(name: String?): ScriptLanguage =
            entries.firstOrNull { it.name == name } ?: ENGLISH
    }
}

data class Script(
    val id: Long = 0,
    val title: String,
    val body: String,
    val language: ScriptLanguage,
    val folder: String = "",
    val pinned: Boolean = false,
    /** Character offset where the presenter last stopped reading. */
    val lastReadOffset: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val wordCount: Int get() = body.split(Regex("\\s+")).count { it.isNotBlank() }
}

enum class TextColorOption(val argb: Long) {
    WHITE(0xFFFFFFFF), WARM(0xFFFFF4D6), YELLOW(0xFFFFD54F), GREEN(0xFF8EF5B5), CYAN(0xFF7FE7FF)
}

enum class VideoQualityOption { HD, FHD, UHD }

/** All user-adjustable teleprompter settings. Persisted locally. */
data class PrompterSettings(
    val fontSizeSp: Float = 44f,
    val lineSpacing: Float = 1.35f,
    val paragraphSpacingDp: Float = 28f,
    val speedDpPerSecond: Float = 60f,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val countdownSeconds: Int = 3,
    val backgroundOpacity: Float = 1f,
    val textColor: TextColorOption = TextColorOption.WHITE,
    val readingLineFraction: Float = 0.3f,
    val horizontalMarginFraction: Float = 0.08f,
    val showReadingLine: Boolean = true,
    val tapToControl: Boolean = true,
    val voiceTracking: Boolean = false,
    val volumeKeysControlPrompter: Boolean = true,
    val keepScreenOn: Boolean = true,
    // Camera studio
    val useFrontCamera: Boolean = true,
    val recordAudio: Boolean = true,
    val videoQuality: VideoQualityOption = VideoQualityOption.FHD,
    val overlayOpacity: Float = 0.55f,
    // Optional cloud AI
    val aiCloudEnabled: Boolean = false,
    val aiApiKey: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    // Text-to-speech narration (rehearsal / preview)
    val ttsRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsVoice: String = "",
    /** Pronunciation dictionary, one `word = spoken form` per line (e.g. Akkadian names). */
    val pronunciations: String = "",
) {
    companion object {
        const val MIN_SPEED = 10f
        const val MAX_SPEED = 400f
        const val MIN_FONT = 18f
        const val MAX_FONT = 140f
        const val DEFAULT_AI_MODEL = "claude-sonnet-5-5"
    }
}
