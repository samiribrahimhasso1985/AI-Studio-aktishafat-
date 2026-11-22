package com.iktishafat.teleprompter.platform.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VoiceStatus { OFF, STARTING, LISTENING, UNAVAILABLE, NO_PERMISSION, ERROR }

/**
 * Continuous speech recognition on top of Android's [SpeechRecognizer]. The platform
 * recognizer ends a session after each silence, so this class restarts it
 * automatically and forwards every partial and final hypothesis to [onHypothesis].
 * Must be used from the main thread.
 */
class VoiceTracker(
    private val context: Context,
    private val onHypothesis: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var languageTag: String = "en-US"
    private var running = false
    private var useOnDevice = true
    private var consecutiveErrors = 0

    private val _status = MutableStateFlow(VoiceStatus.OFF)
    val status: StateFlow<VoiceStatus> = _status
    private val _level = MutableStateFlow(0f)
    /** Microphone input level 0..1 for the UI meter. */
    val level: StateFlow<Float> = _level

    fun start(languageTag: String) {
        this.languageTag = languageTag
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = VoiceStatus.NO_PERMISSION
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !onDeviceAvailable()) {
            _status.value = VoiceStatus.UNAVAILABLE
            return
        }
        running = true
        consecutiveErrors = 0
        _status.value = VoiceStatus.STARTING
        createRecognizer()
        listen()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        _status.value = VoiceStatus.OFF
        _level.value = 0f
    }

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun createRecognizer() {
        recognizer?.let { runCatching { it.destroy() } }
        val r = if (useOnDevice && onDeviceAvailable() && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            useOnDevice = false
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        recognizer = r
    }

    private fun listen() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Ask the recognizer to wait longer before ending the session (not all engines honour this).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed", it)
            scheduleRestart(500, recreate = true)
        }
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean = false) {
        if (!running) return
        main.removeCallbacksAndMessages(null)
        main.postDelayed({
            if (!running) return@postDelayed
            if (recreate) createRecognizer()
            listen()
        }, delayMs)
    }

    private fun deliver(bundle: Bundle?) {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = list?.firstOrNull()?.trim().orEmpty()
        if (text.isNotEmpty()) onHypothesis(text)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _status.value = VoiceStatus.LISTENING
        }
        override fun onBeginningOfSpeech() { consecutiveErrors = 0 }
        override fun onRmsChanged(rmsdB: Float) { _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = deliver(partialResults)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            deliver(results)
            consecutiveErrors = 0
            scheduleRestart(50)
        }

        override fun onError(error: Int) {
            if (!running) return
            Log.d(TAG, "recognizer error $error")
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    _status.value = VoiceStatus.NO_PERMISSION
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(80)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(400, recreate = true)
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, ERROR_SERVER_DISCONNECTED -> {
                    if (useOnDevice) {
                        // On-device model missing for this language: fall back to the default service.
                        useOnDevice = false
                        scheduleRestart(100, recreate = true)
                    } else {
                        running = false
                        _status.value = VoiceStatus.UNAVAILABLE
                    }
                }
                else -> {
                    consecutiveErrors++
                    if (consecutiveErrors > 6) {
                        running = false
                        _status.value = VoiceStatus.ERROR
                    } else {
                        scheduleRestart(300L * consecutiveErrors, recreate = true)
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "VoiceTracker"
        // Constants added in API 31; declared here so minSdk 26 compiles.
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
