package com.iktishafat.teleprompter.domain.importer

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

enum class ImportFormat { TXT, MARKDOWN, DOCX, PDF;
    companion object {
        fun from(fileName: String?, mimeType: String?): ImportFormat? {
            val name = fileName?.lowercase().orEmpty()
            return when {
                name.endsWith(".md") || name.endsWith(".markdown") || mimeType == "text/markdown" -> MARKDOWN
                name.endsWith(".docx") || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                name.endsWith(".pdf") || mimeType == "application/pdf" -> PDF
                name.endsWith(".txt") || mimeType?.startsWith("text/") == true -> TXT
                else -> null
            }
        }
    }
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Pure-JVM extractors for plain text, Markdown and Word (.docx). PDF lives in the data layer. */
object TextExtractors {

    /** Decodes text bytes: BOM detection, strict UTF-8, then legacy Arabic/Western code pages. */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { /* fall through to legacy encodings */ }
        val arabic = runCatching { String(bytes, Charset.forName("windows-1256")) }.getOrNull()
        if (arabic != null) {
            val ar = arabic.count { it.code in 0x0600..0x06FF }
            val letters = arabic.count { it.isLetter() }.coerceAtLeast(1)
            if (ar.toDouble() / letters > 0.3) return arabic
        }
        return String(bytes, Charset.forName("windows-1252"))
    }

    fun cleanup(text: String): String = text
        .replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[ \\t\\u00A0]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    /** Converts Markdown to prompter-friendly text. Headings are kept as `#` lines (scene markers). */
    fun markdownToPlain(md: String): String {
        val out = StringBuilder()
        var inFence = false
        for (raw in md.replace("\r\n", "\n").split("\n")) {
            var line = raw
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) { inFence = !inFence; continue }
            if (inFence) continue // code is not spoken
            run {
                val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$").find(line)
                line = if (heading != null) {
                    "## " + inline(heading.groupValues[2])
                } else {
                    var l = line
                    if (Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$").matches(l)) l = ""
                    l = l.replace(Regex("^\\s{0,3}>\\s?"), "")
                    l = l.replace(Regex("^\\s*[-*+]\\s+\\[[ xX]]\\s+"), "• ")
                    l = l.replace(Regex("^\\s*[-*+]\\s+"), "• ")
                    inline(l)
                }
            }
            out.append(line).append('\n')
        }
        return cleanup(out.toString())
    }

    private fun inline(s: String): String = s
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
        .replace(Regex("(?<![\\w*])[*_](\\S(?:.*?\\S)?)[*_](?![\\w*])"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")

    /** Extracts paragraphs from a .docx (word/document.xml) without external libraries. */
    fun docxToPlain(bytes: ByteArray): String {
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") { xml = zip.readBytes().toString(Charsets.UTF_8); break }
            }
        }
        val doc = xml ?: throw ImportException("Not a valid .docx file (word/document.xml missing)")
        val body = doc.substringAfter("<w:body", doc)
        val sb = StringBuilder()
        val para = Regex("<w:p[ >].*?</w:p>|<w:p/>", RegexOption.DOT_MATCHES_ALL)
        val piece = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab/>|<w:br/>|<w:br [^>]*/>|<w:cr/>", RegexOption.DOT_MATCHES_ALL)
        for (p in para.findAll(body)) {
            val isHeading = Regex("<w:pStyle w:val=\"(Heading|berschrift|Title)[^\"]*\"").containsMatchIn(p.value)
            val line = StringBuilder()
            for (m in piece.findAll(p.value)) {
                when {
                    m.value.startsWith("<w:tab") -> line.append(' ')
                    m.value.startsWith("<w:br") || m.value.startsWith("<w:cr") -> line.append('\n')
                    else -> line.append(unescapeXml(m.groupValues[1]))
                }
            }
            val text = line.toString().trim()
            if (isHeading && text.isNotEmpty()) sb.append("## ")
            sb.append(text).append("\n\n")
        }
        return cleanup(sb.toString())
    }

    fun unescapeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("&amp;", "&")

    fun extract(format: ImportFormat, bytes: ByteArray): String = when (format) {
        ImportFormat.TXT -> cleanup(decodeText(bytes))
        ImportFormat.MARKDOWN -> markdownToPlain(decodeText(bytes))
        ImportFormat.DOCX -> docxToPlain(bytes)
        ImportFormat.PDF -> throw ImportException("PDF extraction requires the Android PDF extractor")
    }
}
