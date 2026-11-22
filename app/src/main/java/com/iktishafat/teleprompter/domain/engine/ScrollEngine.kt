package com.iktishafat.teleprompter.domain.engine

import kotlin.math.exp

enum class PrompterState { IDLE, COUNTDOWN, PLAYING, PAUSED, FINISHED }

/**
 * Frame-driven scroll state machine, independent of Android so it can be unit-tested.
 * The UI calls [tick] once per frame with the elapsed time and renders [position].
 * All distances are in pixels.
 */
class ScrollEngine(
    speedPxPerSecond: Float,
    var countdownSeconds: Int = 3,
    private val minSpeed: Float = 5f,
    private val maxSpeed: Float = 2000f,
) {
    var state: PrompterState = PrompterState.IDLE
        private set
    var position: Float = 0f
        private set
    var maxPosition: Float = 0f
        private set
    var speedPxPerSecond: Float = speedPxPerSecond.coerceIn(minSpeed, maxSpeed)
        private set
    var countdownRemainingMs: Float = 0f
        private set

    /** Whole seconds left on the countdown, for display (3, 2, 1). */
    val countdownDisplay: Int get() = kotlin.math.ceil(countdownRemainingMs / 1000f).toInt()

    val progress: Float get() = if (maxPosition <= 0f) 0f else (position / maxPosition).coerceIn(0f, 1f)

    fun setContentLength(maxScrollPx: Float) {
        maxPosition = maxScrollPx.coerceAtLeast(0f)
        if (position > maxPosition) position = maxPosition
    }

    fun setSpeed(pxPerSecond: Float) {
        speedPxPerSecond = pxPerSecond.coerceIn(minSpeed, maxSpeed)
    }

    fun changeSpeed(factor: Float) = setSpeed(speedPxPerSecond * factor)

    fun start() {
        if (state == PrompterState.FINISHED) position = 0f
        if (countdownSeconds > 0) {
            countdownRemainingMs = countdownSeconds * 1000f
            state = PrompterState.COUNTDOWN
        } else {
            state = PrompterState.PLAYING
        }
    }

    fun pause() {
        if (state == PrompterState.PLAYING || state == PrompterState.COUNTDOWN) state = PrompterState.PAUSED
    }

    fun resume() {
        when (state) {
            PrompterState.PAUSED -> state = PrompterState.PLAYING
            PrompterState.IDLE, PrompterState.FINISHED -> start()
            else -> Unit
        }
    }

    fun togglePlayPause() {
        when (state) {
            PrompterState.PLAYING, PrompterState.COUNTDOWN -> pause()
            else -> resume()
        }
    }

    fun restart(autoStart: Boolean = true) {
        position = 0f
        state = PrompterState.IDLE
        if (autoStart) start()
    }

    fun stop() {
        state = PrompterState.IDLE
    }

    fun seekTo(px: Float) {
        position = px.coerceIn(0f, maxPosition)
        if (state == PrompterState.FINISHED && position < maxPosition) state = PrompterState.PAUSED
    }

    fun nudge(deltaPx: Float) = seekTo(position + deltaPx)

    fun tick(dtMs: Long): Boolean = tick(dtMs.toFloat())

    /** Advances the engine by [dtMs] (fractional, from frame nanos). Returns true if [position] changed. */
    fun tick(dtMs: Float): Boolean {
        if (dtMs <= 0f) return false
        when (state) {
            PrompterState.COUNTDOWN -> {
                countdownRemainingMs -= dtMs
                if (countdownRemainingMs <= 0f) {
                    countdownRemainingMs = 0f
                    state = PrompterState.PLAYING
                }
                return false
            }
            PrompterState.PLAYING -> {
                val before = position
                position = (position + speedPxPerSecond * dtMs / 1000f).coerceAtMost(maxPosition)
                if (maxPosition > 0f && position >= maxPosition) state = PrompterState.FINISHED
                return position != before
            }
            else -> return false
        }
    }

    /**
     * Voice-tracking mode: glide toward [targetPx] with an exponential ease
     * ([timeConstantMs] ≈ time to cover 63% of the gap). Never moves while paused.
     */
    fun followTarget(targetPx: Float, dtMs: Float, timeConstantMs: Float = 350f): Boolean {
        if (state == PrompterState.COUNTDOWN) return tick(dtMs)
        if (state != PrompterState.PLAYING) return false
        val target = targetPx.coerceIn(0f, maxPosition)
        val alpha = 1f - exp(-dtMs / timeConstantMs)
        val before = position
        position += (target - position) * alpha
        if (kotlin.math.abs(target - position) < 0.5f) position = target
        return position != before
    }
}
