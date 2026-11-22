package com.iktishafat.teleprompter.domain.text

/**
 * User-maintained pronunciation corrections for text-to-speech, e.g.
 * `Ashurbanipal = Ashur-bani-pal` or `أوروك = أُوروك`. Matching is whole-word and
 * case-insensitive; Arabic words match regardless of diacritics in the script.
 */
class PronunciationDictionary(entries: Map<String, String>) {
    // Longest first so "Nebukadnezar II" wins over "Nebukadnezar"; one combined regex so
    // that a replacement is never rewritten again by a later rule.
    private val spokenForms: List<String>
    private val combined: Regex?

    init {
        val sorted = entries.filterKeys { it.isNotBlank() }.entries.sortedByDescending { it.key.length }
        spokenForms = sorted.map { it.value }
        combined = if (sorted.isEmpty()) null
        else Regex(sorted.joinToString("|") { "(${pattern(it.key)})" }, RegexOption.IGNORE_CASE)
    }

    val size: Int get() = spokenForms.size

    fun apply(text: String): String {
        val re = combined ?: return text
        return re.replace(text) { m ->
            val group = (1..spokenForms.size).first { m.groups[it] != null }
            spokenForms[group - 1]
        }
    }

    fun hasMatch(text: String): Boolean = combined?.containsMatchIn(text) == true

    private fun pattern(word: String): String {
        val diacritics = "[\\u064B-\\u065F\\u0670\\u0640]*"
        val body = word.trim().map { c ->
            if (TextNormalizer.normalizeWord(c.toString()).isEmpty() && !c.isWhitespace()) "" // drop diacritics in key
            else Regex.escape(c.toString()) + diacritics
        }.joinToString("")
        return "(?<![\\p{L}\\p{M}\\p{N}])$body(?![\\p{L}\\p{N}])"
    }

    companion object {
        /** Parses `word = spoken` lines; `#` starts a comment. */
        fun parse(source: String): PronunciationDictionary {
            val map = LinkedHashMap<String, String>()
            source.lines().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val k = line.substring(0, eq).trim()
                    val v = line.substring(eq + 1).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
                }
            }
            return PronunciationDictionary(map)
        }
    }
}
