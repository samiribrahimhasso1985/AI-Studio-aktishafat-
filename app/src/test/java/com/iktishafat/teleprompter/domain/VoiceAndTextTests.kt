package com.iktishafat.teleprompter.domain

import com.iktishafat.teleprompter.domain.assistant.LocalScriptAssistant
import com.iktishafat.teleprompter.domain.assistant.SpeechDurationEstimator
import com.iktishafat.teleprompter.domain.importer.ImportFormat
import com.iktishafat.teleprompter.domain.importer.TextExtractors
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import com.iktishafat.teleprompter.domain.text.TextNormalizer
import com.iktishafat.teleprompter.domain.voice.SpeechPaceTracker
import com.iktishafat.teleprompter.domain.voice.VoiceAligner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextNormalizerTest {
    @Test
    fun arabicDiacriticsAndLetterVariantsAreNormalised() {
        assertEquals(TextNormalizer.normalizeWord("مدينة"), TextNormalizer.normalizeWord("مَدِينَةٌ"))
        assertEquals("احمد", TextNormalizer.normalizeWord("أحمد"))
        assertEquals("الي", TextNormalizer.normalizeWord("إلى"))
        assertEquals("2026", TextNormalizer.normalizeWord("٢٠٢٦"))
    }

    @Test
    fun germanAndEnglishNormalisation() {
        assertEquals("strasse", TextNormalizer.normalizeWord("Straße"))
        assertEquals("dont", TextNormalizer.normalizeWord("Don't"))
        assertEquals("hello", TextNormalizer.normalizeWord("Hello,"))
    }

    @Test
    fun tokenizerSkipsStageDirectionsAndHeadingsAndKeepsOffsets() {
        val text = "## Scene 1\nHello [PAUSE] world, again."
        val tokens = TextNormalizer.tokenize(text)
        assertEquals(listOf("hello", "world", "again"), tokens.map { it.normalized })
        assertEquals("world", text.substring(tokens[1].start, tokens[1].end))
    }

    @Test
    fun similarityToleratesSmallErrorsButNotUnrelatedWords() {
        assertEquals(1f, TextNormalizer.similarity("mesopotamia", "mesopotamia"), 0f)
        assertTrue(TextNormalizer.similarity("mesopotamya", "mesopotamia") > 0f)
        assertTrue(TextNormalizer.similarity("mesopo", "mesopotamia") > 0f) // partial result cut-off
        assertEquals(0f, TextNormalizer.similarity("cat", "car"), 0f) // short words must match exactly
        assertEquals(0f, TextNormalizer.similarity("river", "temple"), 0f)
    }
}

class VoiceAlignerTest {
    private val english = "In the heart of Mesopotamia a city rose that changed history. " +
        "Babylon was not just a capital, it was an idea about order and law. " +
        "Today we return to its stones to ask what remains."

    @Test
    fun followsSpeakerThroughPartialResults() {
        val a = VoiceAligner(TextNormalizer.tokenize(english))
        assertNotNull(a.onHypothesis("in the heart"))
        assertEquals(2, a.currentIndex)
        a.onHypothesis("in the heart of mesopotamia a city")
        assertEquals(6, a.currentIndex)
        a.onHypothesis("rose that changed history babylon")
        assertEquals(11, a.currentIndex)
    }

    @Test
    fun toleratesRecognitionErrorsAndSkippedWords() {
        val a = VoiceAligner(TextNormalizer.tokenize(english))
        a.reset(10) // speaker is at "history"
        // "babylon was just a capital" – speaker skipped "not", recognizer wrote "babilon"
        val r = a.onHypothesis("babilon was just a capital")
        assertNotNull(r)
        assertEquals("capital", a.tokens[a.currentIndex].normalized)
    }

    @Test
    fun ignoresNoiseAndDoesNotJumpBackOnWeakEvidence() {
        val a = VoiceAligner(TextNormalizer.tokenize(english))
        a.reset(20)
        assertNull(a.onHypothesis("um okay pizza"))
        assertEquals(20, a.currentIndex)
        // "a city" also appears early; a weak two-word match must not jump back.
        a.onHypothesis("a")
        assertEquals(20, a.currentIndex)
    }

    @Test
    fun worksForArabicWithAndWithoutDiacritics() {
        val script = "في قَلبِ بلادِ الرافدين، قامت مدينةٌ غيّرت وجه التاريخ. بابل لم تكن مجرد عاصمة."
        val a = VoiceAligner(TextNormalizer.tokenize(script))
        a.onHypothesis("في قلب بلاد الرافدين قامت مدينه")
        assertEquals("مدينه", a.tokens[a.currentIndex].normalized)
        a.onHypothesis("غيرت وجه التاريخ بابل لم تكن")
        assertEquals("تكن", a.tokens[a.currentIndex].normalized)
    }

    @Test
    fun worksForGerman() {
        val script = "Zwischen zwei Polen des Bewusstseins steht die Frage der Integration im Landkreis Konstanz."
        val a = VoiceAligner(TextNormalizer.tokenize(script))
        a.onHypothesis("zwischen zwei polen des bewusstseins")
        assertEquals("bewusstseins", a.tokens[a.currentIndex].normalized)
        a.onHypothesis("steht die frage der integration")
        assertEquals("integration", a.tokens[a.currentIndex].normalized)
    }

    @Test
    fun tokenIndexAtOffset() {
        val tokens = TextNormalizer.tokenize(english)
        val a = VoiceAligner(tokens)
        val off = english.indexOf("Babylon")
        assertEquals("babylon", tokens[a.tokenIndexAtOffset(off)].normalized)
    }
}

class SpeechPaceTrackerTest {
    @Test
    fun measuresPaceAndDetectsPauses() {
        val t = SpeechPaceTracker(defaultWordsPerMinute = 150, pauseThresholdMs = 1500)
        assertTrue(t.isPaused(0))
        var now = 0L
        t.reset(0, now)
        // 2 words per second = 120 wpm, for 6 seconds
        for (i in 1..12) { now += 500; t.onAdvance(i, now) }
        assertTrue(t.wordsPerMinute in 115f..150f)
        assertFalse(t.isPaused(now + 1000))
        assertTrue(t.predictedIndex(now + 1000) > 12f)
        assertTrue(t.isPaused(now + 2000))
        assertEquals(12f, t.predictedIndex(now + 2000), 0.001f) // no gliding during a pause
    }
}

class DurationAndAssistantTest {
    @Test
    fun durationCountsWordsAndPauses() {
        val words = (1..150).joinToString(" ") { "word" }
        val d = SpeechDurationEstimator.estimate(words, ScriptLanguage.ENGLISH)
        assertEquals(60, d.totalSeconds)
        val withPauses = SpeechDurationEstimator.estimate("$words [PAUSE] [PAUSE 3s] [BREATH]", ScriptLanguage.ENGLISH)
        assertEquals(65, withPauses.totalSeconds) // 60 + 1 + 3 + 0.5 → 64.5 rounds to 65 (banker-free)
        assertEquals("1:05", withPauses.formatted)
    }

    @Test
    fun improveSplitsLongSentencesAndPutsOneSentencePerLine() {
        val long = "The city was built on the river and it grew quickly over many centuries, " +
            "but its people never forgot the floods that destroyed the old walls and temples near the water."
        val r = LocalScriptAssistant().improveForSpeech("$long Short one. (Note) 50% & more.", ScriptLanguage.ENGLISH)
        val lines = r.text.lines()
        assertTrue("expected the long sentence to be split: ${r.text}", lines.size >= 4)
        assertTrue(r.text.contains("50 percent"))
        assertTrue(r.text.contains(" and more"))
        assertFalse(r.text.contains("("))
    }

    @Test
    fun addPausesBetweenParagraphsInArabic() {
        val r = LocalScriptAssistant().addPauses("الفقرة الأولى.\n\nلكن الفقرة الثانية؟ نعم.", ScriptLanguage.ARABIC)
        assertTrue(r.text.contains("[وقفة]"))
        assertTrue(r.text.contains("[نفس]"))
    }

    @Test
    fun splitIntoScenesByDuration() {
        val para = (1..60).joinToString(" ") { "Wort" } + "."
        val text = List(6) { para }.joinToString("\n\n") // 6 × ~28 s German
        val r = LocalScriptAssistant(sceneTargetSeconds = 45).splitIntoScenes(text, ScriptLanguage.GERMAN)
        val headers = r.text.lines().count { it.startsWith("## Szene") }
        assertTrue("headers=$headers\n${r.text}", headers in 3..4)
        // Scene headings are not spoken, so voice tracking ignores them.
        assertEquals(360, TextNormalizer.tokenize(r.text).size)
    }
}

class TextExtractorsTest {
    @Test
    fun formatDetection() {
        assertEquals(ImportFormat.MARKDOWN, ImportFormat.from("a.md", null))
        assertEquals(ImportFormat.DOCX, ImportFormat.from("Skript.DOCX", null))
        assertEquals(ImportFormat.PDF, ImportFormat.from(null, "application/pdf"))
        assertEquals(ImportFormat.TXT, ImportFormat.from("x", "text/plain"))
        assertNull(ImportFormat.from("x.png", "image/png"))
    }

    @Test
    fun decodesUtf8Utf16AndWindows1256() {
        val ar = "مرحبا بالعالم"
        assertEquals(ar, TextExtractors.decodeText(ar.toByteArray(Charsets.UTF_8)))
        val bom16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + ar.toByteArray(Charsets.UTF_16LE)
        assertEquals(ar, TextExtractors.decodeText(bom16))
        assertEquals(ar, TextExtractors.decodeText(ar.toByteArray(charset("windows-1256"))))
        assertEquals("Grüße", TextExtractors.decodeText("Grüße".toByteArray(charset("windows-1252"))))
    }

    @Test
    fun markdownBecomesPlainWithSceneHeadings() {
        val md = "# Intro\n\nThis is **bold** and *italic* with a [link](http://x).\n\n- item one\n> quote\n```\ncode\n```"
        val plain = TextExtractors.markdownToPlain(md)
        assertEquals("## Intro\n\nThis is bold and italic with a link.\n\n• item one\nquote", plain)
    }

    @Test
    fun docxParagraphsAndHeadings() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="x"><w:body>
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>المشهد الأول</w:t></w:r></w:p>
            <w:p><w:r><w:t xml:space="preserve">Hello </w:t></w:r><w:r><w:t>world &amp; more</w:t></w:r></w:p>
            <w:p/><w:p><w:r><w:t>Line</w:t><w:tab/><w:t>two</w:t></w:r></w:p></w:body></w:document>"""
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<x/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml")); z.write(xml.toByteArray()); z.closeEntry()
        }
        val text = TextExtractors.docxToPlain(bos.toByteArray())
        assertEquals("## المشهد الأول\n\nHello world & more\n\nLine two", text)
    }
}
