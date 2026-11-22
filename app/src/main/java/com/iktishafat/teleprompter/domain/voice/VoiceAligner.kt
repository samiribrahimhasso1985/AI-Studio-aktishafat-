package com.iktishafat.teleprompter.domain.voice

import com.iktishafat.teleprompter.domain.text.TextNormalizer
import com.iktishafat.teleprompter.domain.text.Token
import kotlin.math.abs

data class AlignmentResult(val tokenIndex: Int, val confidence: Float)

/**
 * Follows a speaker through a script. Each speech-recognizer hypothesis (partial or
 * final) is reduced to its last few words, and the aligner searches a window around
 * the current position for the place in the script where those words end.
 *
 * The search is anchored on the last spoken word, tolerates misrecognised or skipped
 * words and strongly prefers moving forward, so repeated phrases elsewhere in the
 * script do not make the prompter jump.
 */
class VoiceAligner(
    val tokens: List<Token>,
    private val tailWords: Int = 6,
    private val lookAhead: Int = 40,
    private val lookBehind: Int = 8,
) {
    /** Index of the last token the speaker has said, -1 before the first word. */
    var currentIndex: Int = -1
        private set

    fun reset(toIndex: Int = -1) {
        currentIndex = toIndex.coerceIn(-1, tokens.lastIndex)
    }

    /** Index of the first token starting at or after [charOffset]. */
    fun tokenIndexAtOffset(charOffset: Int): Int {
        if (tokens.isEmpty()) return -1
        var lo = 0
        var hi = tokens.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (tokens[mid].end <= charOffset) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun onHypothesis(hypothesis: String): AlignmentResult? {
        if (tokens.isEmpty()) return null
        val spoken = TextNormalizer.words(hypothesis)
        if (spoken.isEmpty()) return null
        val tail = spoken.takeLast(tailWords)
        val m = tail.size

        val from = (currentIndex - lookBehind).coerceAtLeast(0)
        val to = (currentIndex + lookAhead).coerceAtMost(tokens.lastIndex)
        var bestIndex = -1
        var bestScore = Float.NEGATIVE_INFINITY
        var bestMatches = 0f

        for (j in from..to) {
            val anchor = TextNormalizer.similarity(tail[m - 1], tokens[j].normalized)
            if (anchor <= 0f) continue
            var matches = anchor
            var skips = 0
            var k = m - 2
            var s = j - 1
            val floor = j - (m + 3)
            while (k >= 0 && s >= 0 && s > floor) {
                val sim = TextNormalizer.similarity(tail[k], tokens[s].normalized)
                if (sim > 0f) {
                    matches += sim; k--; s--
                } else {
                    // Either the recogniser inserted a word, or the speaker skipped one.
                    if (k - 1 >= 0 && TextNormalizer.similarity(tail[k - 1], tokens[s].normalized) > 0f) k-- else s--
                    skips++
                }
            }
            val expected = currentIndex + 1
            var score = matches - 0.15f * skips - 0.01f * abs(j - expected)
            if (j < currentIndex) score -= 0.6f // moving backwards needs strong evidence
            if (score > bestScore) {
                bestScore = score; bestIndex = j; bestMatches = matches
            }
        }
        if (bestIndex < 0) return null

        val accepted = if (m == 1) {
            // A single word only counts if it is an exact, non-trivial word right ahead.
            bestMatches >= 1f && tokens[bestIndex].normalized.length >= 3 &&
                bestIndex in (currentIndex + 1)..(currentIndex + 4)
        } else {
            val needed = if (bestIndex < currentIndex) 2.6f else 1.6f
            bestMatches >= needed
        }
        if (!accepted) return null
        if (bestIndex == currentIndex) return AlignmentResult(bestIndex, bestMatches / m)
        currentIndex = bestIndex
        return AlignmentResult(bestIndex, (bestMatches / m).coerceAtMost(1f))
    }
}

/**
 * Measures the speaker's pace from aligned positions and detects pauses. Between two
 * recognizer callbacks the prompter keeps gliding at the measured pace, so the text
 * moves continuously instead of jumping word by word.
 */
class SpeechPaceTracker(
    defaultWordsPerMinute: Int,
    private val pauseThresholdMs: Long = 1600,
    private val windowMs: Long = 10_000,
    private val maxLeadWords: Float = 3f,
) {
    private data class Sample(val timeMs: Long, val index: Int)

    private val samples = ArrayDeque<Sample>()
    private var lastAdvanceMs: Long = -1
    private var lastIndex: Int = -1
    var wordsPerMinute: Float = defaultWordsPerMinute.toFloat()
        private set

    fun reset(index: Int, nowMs: Long) {
        samples.clear()
        lastIndex = index
        lastAdvanceMs = nowMs
    }

    fun onAdvance(index: Int, nowMs: Long) {
        if (index <= lastIndex) return
        lastIndex = index
        lastAdvanceMs = nowMs
        samples.addLast(Sample(nowMs, index))
        while (samples.size > 2 && nowMs - samples.first().timeMs > windowMs) samples.removeFirst()
        if (samples.size >= 2) {
            val first = samples.first()
            val dt = nowMs - first.timeMs
            if (dt >= 1500) {
                val measured = (index - first.index) * 60_000f / dt
                // Smooth and clamp to a humanly plausible range.
                wordsPerMinute = (wordsPerMinute * 0.6f + measured.coerceIn(40f, 260f) * 0.4f)
            }
        }
    }

    fun isPaused(nowMs: Long): Boolean = lastAdvanceMs < 0 || nowMs - lastAdvanceMs > pauseThresholdMs

    /** Fractional token index where the speaker probably is right now. */
    fun predictedIndex(nowMs: Long): Float {
        if (lastIndex < 0) return -1f
        if (isPaused(nowMs)) return lastIndex.toFloat()
        val lead = wordsPerMinute / 60_000f * (nowMs - lastAdvanceMs)
        return lastIndex + lead.coerceAtMost(maxLeadWords)
    }
}
