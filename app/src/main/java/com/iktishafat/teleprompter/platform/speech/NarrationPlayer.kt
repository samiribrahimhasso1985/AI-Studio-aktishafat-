package com.iktishafat.teleprompter.platform.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.iktishafat.teleprompter.domain.text.PronunciationDictionary
import com.iktishafat.teleprompter.domain.text.Paragraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class NarrationStatus { INITIALIZING, READY, SPEAKING, PAUSED, UNAVAILABLE, LANGUAGE_MISSING }

data class NarrationVoice(val name: String, val locale: Locale, val offline: Boolean, val quality: Int)

/**
 * Paragraph-by-paragraph text-to-speech for rehearsal and narration previews, built on
 * the Android [TextToSpeech] engine installed on the device (e.g. Samsung or Google TTS).
 * Reports the word being spoken so the prompter can follow it.
 */
class NarrationPlayer(
    context: Context,
    private val onWord: (globalOffset: Int) -> Unit,
    private val onFinished: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow(NarrationStatus.INITIALIZING)
    val status: StateFlow<NarrationStatus> = _status
    private val _voices = MutableStateFlow<List<NarrationVoice>>(emptyList())
    val voices: StateFlow<List<NarrationVoice>> = _voices

    private var paragraphs: List<Paragraph> = emptyList()
    private var current = 0
    private var dictionary = PronunciationDictionary(emptyMap())
    private var locale: Locale = Locale.ENGLISH

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code ->
        main.post {
            if (code == TextToSpeech.SUCCESS) _status.value = NarrationStatus.READY
            else _status.value = NarrationStatus.UNAVAILABLE
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val i = utteranceId?.toIntOrNull() ?: return
                main.post { paragraphs.getOrNull(i)?.let { onWord(it.start) } }
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val i = utteranceId?.toIntOrNull() ?: return
                val p = paragraphs.getOrNull(i) ?: return
                // Offsets are only exact when no pronunciation replacement changed the text.
                if (!dictionary.hasMatch(p.text)) main.post { onWord(p.start + start) }
            }
            override fun onDone(utteranceId: String?) {
                val i = utteranceId?.toIntOrNull() ?: return
                main.post {
                    if (_status.value != NarrationStatus.SPEAKING) return@post
                    current = i + 1
                    if (current >= paragraphs.size) { _status.value = NarrationStatus.READY; current = 0; onFinished() }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { _status.value = NarrationStatus.READY } }
        })
    }

    /** Selects the language; returns false if no voice for it is installed. */
    fun configure(languageTag: String, voiceName: String, rate: Float, pitch: Float, pronunciations: String): Boolean {
        if (_status.value == NarrationStatus.UNAVAILABLE || _status.value == NarrationStatus.INITIALIZING) return false
        locale = Locale.forLanguageTag(languageTag)
        val result = tts.setLanguage(locale)
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val matching = all.filter { it.locale.language == locale.language && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
        _voices.value = matching.map { NarrationVoice(it.name, it.locale, !it.isNetworkConnectionRequired, it.quality) }
        matching.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        dictionary = PronunciationDictionary.parse(pronunciations)
        val ok = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ok) _status.value = NarrationStatus.LANGUAGE_MISSING else if (_status.value == NarrationStatus.LANGUAGE_MISSING) _status.value = NarrationStatus.READY
        return ok
    }

    fun play(paragraphs: List<Paragraph>, fromParagraph: Int = current) {
        if (_status.value == NarrationStatus.UNAVAILABLE || _status.value == NarrationStatus.LANGUAGE_MISSING) return
        this.paragraphs = paragraphs
        current = fromParagraph.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        tts.stop()
        _status.value = NarrationStatus.SPEAKING
        for (i in current until paragraphs.size) {
            val p = paragraphs[i]
            val spoken = dictionary.apply(if (p.isHeading) p.displayText else p.text.replace(Regex("\\[[^\\]]*]"), " "))
            tts.speak(spoken, TextToSpeech.QUEUE_ADD, Bundle(), i.toString())
            tts.playSilentUtterance(if (p.isHeading) 600 else 350, TextToSpeech.QUEUE_ADD, "gap$i")
        }
    }

    /** Stops after remembering the current paragraph; [resume] continues from it. */
    fun pause() {
        if (_status.value != NarrationStatus.SPEAKING) return
        _status.value = NarrationStatus.PAUSED
        tts.stop()
    }

    fun resume() { if (_status.value == NarrationStatus.PAUSED) play(paragraphs, current) }

    fun stop() {
        tts.stop()
        current = 0
        if (_status.value == NarrationStatus.SPEAKING || _status.value == NarrationStatus.PAUSED) _status.value = NarrationStatus.READY
    }

    fun release() {
        runCatching { tts.stop(); tts.shutdown() }
    }

    /** Speaks a single phrase, used to preview pronunciation entries. */
    fun preview(text: String) {
        tts.speak(dictionary.apply(text), TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
    }
}
