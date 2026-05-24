package labs.dx.readr.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.repository.PdfDocumentSession

internal class SummaryDocumentSession(
    private val sourceDocument: DocumentDescriptor,
    text: String
) : PdfDocumentSession {
    private val words: List<PdfWord> = text.toSummaryWords()

    override val documentInfo: PdfDocumentInfo = PdfDocumentInfo(
        document = sourceDocument.copy(displayName = "${sourceDocument.displayName} brief"),
        pageCount = 1,
        isEncrypted = false,
        hasExtractableText = words.isNotEmpty()
    )

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo = PdfPageInfo(pageIndex, PageWidth, PageHeight)

    override suspend fun getPageLayoutDebugInfo(pageIndex: Int) = null

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidthPx.coerceAtLeast(1), 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText("Executive brief", 24f, 64f, placeholderPaint)
        return bitmap
    }

    override suspend fun setResearchPaperMode(enabled: Boolean) = Unit
    override suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean, resetQueue: Boolean) = Unit
    override suspend fun prefetchPageAnalysis(anchorPageIndex: Int) = Unit
    override suspend fun getWordsForPage(pageIndex: Int): List<PdfWord> = if (pageIndex == 0) words else emptyList()
    override suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord? {
        return words.getOrNull(globalWordIndex)
    }

    override suspend fun getTotalWordCount(): Int = words.size
    override suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord? = null
    override fun close() = Unit

    private fun String.toSummaryWords(): List<PdfWord> {
        val result = mutableListOf<PdfWord>()
        var sentenceIndex = 0
        var paragraphIndex = 0
        var x = Margin
        var y = Margin

        lines().forEach { line ->
            val lineWords = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (lineWords.isEmpty()) {
                paragraphIndex += 1
                return@forEach
            }
            lineWords.forEach { word ->
                val width = (word.length * 8f).coerceAtLeast(12f)
                if (x + width > PageWidth - Margin) {
                    x = Margin
                    y += LineHeight
                }
                result += PdfWord(
                    text = word,
                    pageIndex = 0,
                    boundingBox = RectF(x, y, x + width, y + 18f),
                    normalizedText = word.lowercase(Locale.US).trim('.', ',', ';', ':', '!', '?', '"', '\''),
                    globalWordIndex = result.size,
                    sentenceIndex = sentenceIndex,
                    paragraphIndex = paragraphIndex
                )
                if (word.lastOrNull() in setOf('.', '!', '?')) sentenceIndex += 1
                x += width + 8f
            }
            paragraphIndex += 1
            x = Margin
            y += LineHeight
        }
        return result
    }

    private companion object {
        const val PageWidth = 612
        const val PageHeight = 792
        const val Margin = 48f
        const val LineHeight = 27f
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 28)
            textSize = 24f
        }
    }
}
