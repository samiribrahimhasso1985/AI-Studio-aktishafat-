package com.iktishafat.teleprompter.ui.prompter

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLayoutResult
import com.iktishafat.teleprompter.domain.engine.PrompterState
import com.iktishafat.teleprompter.domain.engine.ScrollEngine
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.domain.remote.RemoteCommand
import com.iktishafat.teleprompter.domain.text.Paragraph
import com.iktishafat.teleprompter.domain.text.TextNormalizer
import com.iktishafat.teleprompter.domain.text.splitParagraphs
import com.iktishafat.teleprompter.domain.voice.SpeechPaceTracker
import com.iktishafat.teleprompter.domain.voice.VoiceAligner
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * State holder that drives one prompter view: frame-based scrolling, voice tracking,
 * remote/tap commands and the mapping between words and on-screen positions.
 */
@Stable
class PrompterController(
    val body: String,
    val language: ScriptLanguage,
    speedPxPerSecond: Float,
    countdownSeconds: Int,
) {
    val paragraphs: List<Paragraph> = splitParagraphs(body)
    val engine = ScrollEngine(speedPxPerSecond, countdownSeconds)
    private val tokens = TextNormalizer.tokenize(body)
    private val aligner = VoiceAligner(tokens)
    private val pace = SpeechPaceTracker(language.defaultWordsPerMinute)

    // Layout, filled in by the view.
    private val layouts = arrayOfNulls<TextLayoutResult>(paragraphs.size)
    private val tops = FloatArray(paragraphs.size)
    private val heights = FloatArray(paragraphs.size)
    var viewportHeight = 0f
        private set
    var contentHeight = 0f
        private set
    var readingLineFraction = 0.3f

    // Observable state for Compose.
    var position by mutableFloatStateOf(0f)
        private set
    var state by mutableStateOf(PrompterState.IDLE)
        private set
    var countdown by mutableIntStateOf(0)
        private set
    var speed by mutableFloatStateOf(engine.speedPxPerSecond)
        private set
    /** Character offset up to which the speaker has read (voice mode), -1 if unknown. */
    var spokenUntil by mutableIntStateOf(-1)
        private set
    /** Character range of the word currently being spoken (voice mode). */
    var currentWordStart by mutableIntStateOf(-1)
        private set
    var voiceMode by mutableStateOf(false)
    var voiceActive by mutableStateOf(false) // recognizer is actually listening
    var wordsPerMinute by mutableFloatStateOf(language.defaultWordsPerMinute.toFloat())
        private set
    var speakerPaused by mutableStateOf(true)
        private set

    var onSpeedChanged: (Float) -> Unit = {}

    val readingLinePx: Float get() = viewportHeight * readingLineFraction
    val progress: Float get() = engine.progress

    fun setViewport(height: Float) {
        viewportHeight = height
        updateBounds()
    }

    fun setContentHeight(height: Float) {
        contentHeight = height
        updateBounds()
    }

    private fun updateBounds() {
        engine.setContentLength((contentHeight - viewportHeight).coerceAtLeast(0f))
        sync()
    }

    fun onParagraphLayout(index: Int, layout: TextLayoutResult) { layouts[index] = layout }
    fun onParagraphPlaced(index: Int, top: Float, height: Float) { tops[index] = top; heights[index] = height }

    private fun sync() {
        position = engine.position
        state = engine.state
        countdown = engine.countdownDisplay
        speed = engine.speedPxPerSecond
    }

    // ---- frame loop ------------------------------------------------------------------

    fun onFrame(dtMs: Float, nowMs: Long) {
        val usingVoice = voiceMode && voiceActive
        if (usingVoice && engine.state == PrompterState.PLAYING) {
            speakerPaused = pace.isPaused(nowMs)
            val predicted = pace.predictedIndex(nowMs)
            if (predicted >= 0f) {
                val y = yForTokenIndex(predicted)
                if (y != null) engine.followTarget(y - readingLinePx, dtMs)
            }
        } else {
            engine.tick(dtMs)
        }
        sync()
    }

    // ---- voice -----------------------------------------------------------------------

    fun onHypothesis(text: String, nowMs: Long) {
        if (!voiceMode) return
        val result = aligner.onHypothesis(text) ?: return
        val idx = result.tokenIndex
        pace.onAdvance(idx, nowMs)
        wordsPerMinute = pace.wordsPerMinute
        spokenUntil = tokens[idx].end
        currentWordStart = tokens[idx].start
        // Automatic speed: keep the fallback scroll speed matched to the speaker's pace.
        val pxPerWord = if (tokens.isNotEmpty()) (contentHeight - viewportHeight).coerceAtLeast(1f) / tokens.size else 0f
        if (pxPerWord > 0f) {
            engine.setSpeed(pace.wordsPerMinute / 60f * pxPerWord)
            speed = engine.speedPxPerSecond
        }
        if (engine.state == PrompterState.IDLE || engine.state == PrompterState.FINISHED) {
            // Speaking starts the prompter (no countdown needed when following the voice).
            engine.countdownSeconds = 0
            engine.resume()
        }
        sync() // publish the state change so the frame loop starts
    }

    /**
     * Narration (text-to-speech rehearsal): the engine reports the exact word it is
     * speaking, so the prompter follows it the same way it follows a live speaker.
     */
    fun onExternalWord(globalOffset: Int, nowMs: Long) {
        if (tokens.isEmpty()) return
        val idx = aligner.tokenIndexAtOffset(globalOffset).coerceIn(0, tokens.lastIndex)
        aligner.reset(idx)
        pace.onAdvance(idx, nowMs)
        wordsPerMinute = pace.wordsPerMinute
        spokenUntil = tokens[idx].end
        currentWordStart = tokens[idx].start
        if (engine.state != PrompterState.PLAYING) { engine.countdownSeconds = 0; engine.resume() }
        sync()
    }

    /** Re-anchors voice tracking to the line under the reading line (after manual moves). */
    fun resyncVoiceToReadingLine(nowMs: Long) {
        val offset = offsetAtContentY(position + readingLinePx) ?: return
        val idx = aligner.tokenIndexAtOffset(offset) - 1
        aligner.reset(idx)
        pace.reset(idx, nowMs)
        spokenUntil = if (idx >= 0) tokens[idx].end else -1
        currentWordStart = if (idx >= 0) tokens[idx].start else -1
    }

    /** Character offset at the reading line, saved as the "last reading position". */
    fun offsetAtReadingLine(): Int = offsetAtContentY(position + readingLinePx) ?: 0

    /** Restores a saved reading position once layout is available. Returns true when done. */
    fun restoreOffset(offset: Int, nowMs: Long): Boolean {
        if (offset <= 0) return true
        val idx = aligner.tokenIndexAtOffset(offset)
        val y = yForTokenIndex(idx.toFloat()) ?: return false
        engine.seekTo(y - readingLinePx)
        aligner.reset(idx - 1)
        pace.reset(idx - 1, nowMs)
        sync()
        return true
    }

    private fun tokenTop(i: Int): Float? {
        val t = tokens.getOrNull(i) ?: return null
        val p = paragraphIndexForOffset(t.start) ?: return null
        val layout = layouts[p] ?: return null
        val local = (t.start - paragraphs[p].start).coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        val line = layout.getLineForOffset(local)
        return tops[p] + layout.getLineTop(line)
    }

    fun yForTokenIndex(index: Float): Float? {
        if (tokens.isEmpty()) return null
        val i = floor(index).toInt().coerceIn(0, tokens.lastIndex)
        val a = tokenTop(i) ?: return null
        val frac = index - i
        if (frac <= 0f || i >= tokens.lastIndex) return a
        val b = tokenTop(i + 1) ?: return a
        return a + (b - a) * frac
    }

    private fun paragraphIndexForOffset(offset: Int): Int? {
        var lo = 0
        var hi = paragraphs.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val p = paragraphs[mid]
            when {
                offset < p.start -> hi = mid - 1
                offset >= p.end -> lo = mid + 1
                else -> return mid
            }
        }
        return null
    }

    private fun offsetAtContentY(y: Float): Int? {
        for (i in paragraphs.indices) {
            val layout = layouts[i] ?: continue
            if (y < tops[i] + heights[i] || i == paragraphs.lastIndex) {
                if (paragraphs[i].isHeading) return paragraphs[i].end
                val localY = (y - tops[i]).coerceIn(0f, layout.size.height.toFloat())
                val line = layout.getLineForVerticalPosition(localY)
                return paragraphs[i].start + layout.getLineStart(line)
            }
        }
        return null
    }

    // ---- commands --------------------------------------------------------------------

    fun togglePlayPause() { engine.togglePlayPause(); sync() }
    fun restart() {
        engine.restart(autoStart = !voiceMode)
        aligner.reset(); pace.reset(-1, 0); spokenUntil = -1; currentWordStart = -1
        sync()
    }
    fun stop() { engine.pause(); sync() }

    fun changeSpeed(factor: Float) {
        engine.changeSpeed(factor)
        sync()
        onSpeedChanged(engine.speedPxPerSecond)
    }

    fun applySpeed(pxPerSecond: Float) { engine.setSpeed(pxPerSecond); sync() }

    fun applyCountdown(seconds: Int) { engine.countdownSeconds = seconds }

    fun jump(byFraction: Float, nowMs: Long) {
        engine.nudge(viewportHeight * byFraction)
        if (voiceMode) resyncVoiceToReadingLine(nowMs)
        sync()
    }

    fun drag(deltaPx: Float) { engine.nudge(deltaPx); sync() }

    fun startWithoutCountdown() {
        val c = engine.countdownSeconds
        engine.countdownSeconds = 0
        engine.start()
        engine.countdownSeconds = c
        sync()
    }

    fun handle(command: RemoteCommand, nowMs: Long) {
        when (command) {
            RemoteCommand.PLAY_PAUSE -> togglePlayPause()
            RemoteCommand.SPEED_UP -> changeSpeed(1.1f)
            RemoteCommand.SPEED_DOWN -> changeSpeed(1f / 1.1f)
            RemoteCommand.FORWARD -> jump(0.25f, nowMs)
            RemoteCommand.BACKWARD -> jump(-0.25f, nowMs)
            RemoteCommand.RESTART -> restart()
            RemoteCommand.TOGGLE_MIRROR, RemoteCommand.EXIT -> Unit // handled by the screen
        }
    }

    /** Seconds left at the current pace. */
    fun remainingSeconds(): Int {
        if (voiceMode && voiceActive && tokens.isNotEmpty()) {
            val done = tokens.indexOfFirst { it.end > spokenUntil }.let { if (it < 0) tokens.size else it }
            return ((tokens.size - done) * 60f / wordsPerMinute.coerceAtLeast(1f)).roundToInt()
        }
        val remainingPx = engine.maxPosition - position
        return if (engine.speedPxPerSecond > 0) (remainingPx / engine.speedPxPerSecond).roundToInt() else 0
    }
}
