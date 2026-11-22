package com.iktishafat.teleprompter.domain

import com.iktishafat.teleprompter.domain.text.PronunciationDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationDictionaryTest {
    private val dict = PronunciationDictionary.parse(
        """
        # historical names
        Ashurbanipal = Aschur-bani-pal
        Nebukadnezar II = Nebukadnezar der Zweite
        Nebukadnezar = Ne-bu-kad-ne-zar
        أوروك = أُوروك
        """.trimIndent()
    )

    @Test
    fun parsesAndIgnoresComments() = assertEquals(4, dict.size)

    @Test
    fun replacesWholeWordsCaseInsensitively() {
        assertEquals("King Aschur-bani-pal ruled.", dict.apply("King ashurbanipal ruled."))
        assertEquals("Ashurbanipals", dict.apply("Ashurbanipals")) // not a whole word
    }

    @Test
    fun longestEntryWins() {
        assertEquals("Nebukadnezar der Zweite und Ne-bu-kad-ne-zar", dict.apply("Nebukadnezar II und Nebukadnezar"))
    }

    @Test
    fun arabicMatchesWithDiacriticsInScriptAndInsideSentence() {
        assertEquals("مدينة أُوروك القديمة", dict.apply("مدينة أوروك القديمة"))
        assertEquals("في أُوروك.", dict.apply("في أُورُوك."))
        assertTrue(dict.hasMatch("زرنا أوروك"))
        assertFalse(dict.hasMatch("زرنا بابل"))
    }
}

class ParagraphSplitTest {
    @Test
    fun splitsOnBlankLinesAndKeepsOffsets() {
        val body = "## المشهد 1\n\nأول فقرة\nسطر ثانٍ\n\n\n  \nSecond paragraph [PAUSE]"
        val ps = com.iktishafat.teleprompter.domain.text.splitParagraphs(body)
        assertEquals(3, ps.size)
        assertTrue(ps[0].isHeading)
        assertEquals("المشهد 1", ps[0].displayText)
        assertEquals("أول فقرة\nسطر ثانٍ", ps[1].text)
        assertEquals(ps[2].text, body.substring(ps[2].start, ps[2].end))
        assertEquals("Second paragraph [PAUSE]", ps[2].text)
    }
}
