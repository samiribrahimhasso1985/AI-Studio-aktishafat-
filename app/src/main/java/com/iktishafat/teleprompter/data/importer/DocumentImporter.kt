package com.iktishafat.teleprompter.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.iktishafat.teleprompter.domain.importer.ImportException
import com.iktishafat.teleprompter.domain.importer.ImportFormat
import com.iktishafat.teleprompter.domain.importer.TextExtractors
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedDocument(val title: String, val text: String, val format: ImportFormat)

/** Reads TXT, Markdown, DOCX and PDF documents chosen through the Storage Access Framework. */
class DocumentImporter(private val context: Context) {

    @Volatile private var pdfReady = false

    suspend fun import(uri: Uri): ImportedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_BYTES) throw ImportException("File is larger than ${MAX_BYTES / 1_000_000} MB")
        val mime = resolver.getType(uri)
        val format = ImportFormat.from(name ?: uri.lastPathSegment, mime)
            ?: throw ImportException("Unsupported file type: ${name ?: mime ?: uri}")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ImportException("Cannot open file")
        if (bytes.size > MAX_BYTES) throw ImportException("File is too large")

        val text = when (format) {
            ImportFormat.PDF -> extractPdf(bytes)
            else -> TextExtractors.extract(format, bytes)
        }
        if (text.isBlank()) throw ImportException("No text found in the document")
        val title = (name ?: "Imported").substringBeforeLast('.').ifBlank { "Imported" }
        ImportedDocument(title, text, format)
    }

    private fun extractPdf(bytes: ByteArray): String {
        if (!pdfReady) { PDFBoxResourceLoader.init(context.applicationContext); pdfReady = true }
        try {
            PDDocument.load(bytes).use { doc ->
                if (doc.isEncrypted) throw ImportException("The PDF is password-protected")
                val stripper = PDFTextStripper().apply {
                    setSortByPosition(true)
                    setParagraphStart("\n")
                    setParagraphEnd("\n")
                }
                return TextExtractors.cleanup(stripper.getText(doc))
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Could not read PDF: ${e.message}", e)
        }
    }

    companion object {
        const val MAX_BYTES = 25_000_000L
        val MIME_TYPES = arrayOf(
            "text/plain", "text/markdown", "text/x-markdown", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream",
        )
    }
}
