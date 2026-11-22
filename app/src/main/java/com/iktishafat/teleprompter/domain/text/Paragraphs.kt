package com.iktishafat.teleprompter.domain.text

/** A block of the script separated by blank lines, with its offsets in the full text. */
data class Paragraph(val index: Int, val start: Int, val end: Int, val text: String) {
    val isHeading: Boolean get() = text.startsWith("#")
    val displayText: String get() = if (isHeading) text.trimStart('#', ' ') else text
}

fun splitParagraphs(body: String): List<Paragraph> {
    val out = ArrayList<Paragraph>()
    val sep = Regex("\\n[ \\t]*\\n\\s*")
    var start = 0
    var i = 0
    for (m in sep.findAll(body)) {
        val text = body.substring(start, m.range.first)
        if (text.isNotBlank()) out += Paragraph(i++, start, m.range.first, text)
        start = m.range.last + 1
    }
    if (start < body.length && body.substring(start).isNotBlank()) out += Paragraph(i, start, body.length, body.substring(start))
    return out
}

