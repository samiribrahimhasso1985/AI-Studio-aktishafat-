package com.iktishafat.teleprompter.domain.text

/** A word of the script with its character range in the original text. */
data class Token(val normalized: String, val start: Int, val end: Int)

/**
 * Normalisation shared by the script tokenizer and the speech recognizer output so
 * that both sides compare equal regardless of diacritics, letter variants or case.
 */
object TextNormalizer {

    private fun isArabicDiacritic(c: Char): Boolean {
        val code = c.code
        return code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 ||
            code in 0x06D6..0x06ED
    }

    fun normalizeWord(word: String): String {
        val sb = StringBuilder(word.length)
        for (raw in word.lowercase()) {
            if (isArabicDiacritic(raw)) continue
            val c = when (raw) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                in '٠'..'٩' -> '0' + (raw - '٠')
                in '۰'..'۹' -> '0' + (raw - '۰')
                else -> raw
            }
            when {
                c == 'ß' -> sb.append("ss")
                c.isLetterOrDigit() -> sb.append(c)
                else -> Unit // drop punctuation, apostrophes, joiners
            }
        }
        return sb.toString()
    }

    private fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || isArabicDiacritic(c) || c == '\'' || c == '’' ||
            Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    /**
     * Splits [text] into words. Anything inside square brackets (stage directions such
     * as `[PAUSE]`) and heading lines starting with `#` are skipped: they are never spoken.
     */
    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '#' && (i == 0 || text[i - 1] == '\n')) {
                // Scene headings ("## Scene 2") are shown but not spoken.
                val eol = text.indexOf('\n', i)
                i = if (eol < 0) n else eol + 1
                continue
            }
            if (c == '[') {
                val close = text.indexOf(']', i + 1)
                if (close > i) { i = close + 1; continue }
            }
            if (isWordChar(c)) {
                val start = i
                while (i < n && isWordChar(text[i])) i++
                val norm = normalizeWord(text.substring(start, i))
                if (norm.isNotEmpty()) tokens.add(Token(norm, start, i))
            } else {
                i++
            }
        }
        return tokens
    }

    fun words(text: String): List<String> = tokenize(text).map { it.normalized }

    fun levenshtein(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Similarity in [0,1] tolerant to recognition errors and cut-off partial words. */
    fun similarity(spoken: String, script: String): Float {
        if (spoken.isEmpty() || script.isEmpty()) return 0f
        if (spoken == script) return 1f
        val shorter = minOf(spoken.length, script.length)
        if (shorter >= 4 && (script.startsWith(spoken) || spoken.startsWith(script))) return 0.85f
        val allowed = when {
            shorter >= 8 -> 2
            shorter >= 4 -> 1
            else -> 0
        }
        if (allowed == 0) return 0f
        val d = levenshtein(spoken, script, allowed)
        return if (d <= allowed) 0.8f - 0.1f * (d - 1) else 0f
    }
}
