package com.iktishafat.teleprompter.domain.assistant

import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.domain.text.TextNormalizer
import kotlin.math.roundToInt

data class DurationEstimate(val words: Int, val pauseSeconds: Double, val totalSeconds: Int) {
    val formatted: String get() = formatSeconds(totalSeconds)

    companion object {
        fun formatSeconds(s: Int): String = "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * Estimates spoken duration from word count, the language's typical delivery pace
 * and pause markers: `[PAUSE]` (1 s), `[BREATH]` (0.5 s), `[PAUSE 3s]` (explicit),
 * plus small natural pauses after sentences and paragraphs.
 */
object SpeechDurationEstimator {
    private val explicitPause = Regex("\\[(?:PAUSE|وقفة)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:s|ث)?]", RegexOption.IGNORE_CASE)
    private val pauseMarker = Regex("\\[(PAUSE|وقفة)]", RegexOption.IGNORE_CASE)
    private val breathMarker = Regex("\\[(BREATH|ATMEN|نفس|تنفس)]", RegexOption.IGNORE_CASE)
    private val sentenceEnd = Regex("[.!?؟。]+(\\s|$)")

    fun estimate(text: String, language: ScriptLanguage, wordsPerMinute: Int = language.defaultWordsPerMinute): DurationEstimate {
        val words = TextNormalizer.tokenize(text).size
        var pauses = 0.0
        explicitPause.findAll(text).forEach { pauses += it.groupValues[1].replace(',', '.').toDouble() }
        pauses += pauseMarker.findAll(text).count() * 1.0
        pauses += breathMarker.findAll(text).count() * 0.5
        pauses += sentenceEnd.findAll(text).count() * 0.3
        val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        pauses += (paragraphs - 1).coerceAtLeast(0) * 0.7
        val speech = if (wordsPerMinute > 0) words * 60.0 / wordsPerMinute else 0.0
        return DurationEstimate(words, pauses, (speech + pauses).roundToInt())
    }
}
