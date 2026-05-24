package labs.dx.pdf.extract

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.util.normalizeWord
import kotlin.math.roundToInt

internal data class PageExtractionResult(
    val words: List<PdfWord>,
    val nextGlobalWordIndex: Int,
    val nextSentenceIndex: Int,
    val nextParagraphIndex: Int
)

internal class PdfWordExtractor {

    fun extractPage(
        document: PDDocument,
        pageIndex: Int,
        globalWordStart: Int,
        sentenceStart: Int,
        paragraphStart: Int,
        layout: PageReadingLayout = PageReadingLayout(ReadingLayoutMode.SINGLE_COLUMN)
    ): PageExtractionResult {
        val collector = BoundingBoxStripper(pageIndex)
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(document)

        val words = mutableListOf<PdfWord>()
        var globalWordIndex = globalWordStart
        var sentenceIndex = sentenceStart
        var paragraphIndex = paragraphStart
        var previousTop = Float.NaN
        var previousHeight = 0f

        val orderedWords = reorderWords(collector.words, layout)

        orderedWords.forEach { raw ->
            if (raw.text.isBlank()) return@forEach
            if (!previousTop.isNaN()) {
                val verticalDelta = kotlin.math.abs(raw.bounds.top - previousTop)
                if (verticalDelta > (previousHeight * 1.4f).coerceAtLeast(12f)) {
                    paragraphIndex += 1
                }
            }
            val normalized = normalizeWord(raw.text)
            if (normalized.isNotBlank()) {
                words += PdfWord(
                    text = raw.text,
                    pageIndex = pageIndex,
                    boundingBox = raw.bounds,
                    normalizedText = normalized,
                    globalWordIndex = globalWordIndex,
                    sentenceIndex = sentenceIndex,
                    paragraphIndex = paragraphIndex
                )
                globalWordIndex += 1
                if (raw.text.lastOrNull() in sentenceTerminators) {
                    sentenceIndex += 1
                }
                previousTop = raw.bounds.top
                previousHeight = raw.bounds.height()
            }
        }

        return PageExtractionResult(
            words = words,
            nextGlobalWordIndex = globalWordIndex,
            nextSentenceIndex = sentenceIndex,
            nextParagraphIndex = paragraphIndex
        )
    }

    private data class RawWord(val text: String, val bounds: RectF)

    private fun reorderWords(
        words: List<RawWord>,
        layout: PageReadingLayout
    ): List<RawWord> {
        if (words.isEmpty()) return emptyList()
        val pageRight = words.maxOf { it.bounds.right }
        val pageBottom = words.maxOf { it.bounds.bottom }
        val splitX = pageRight * (layout.splitRatio ?: 0.5f)
        val columnTop = pageBottom * (layout.columnTopRatio ?: 0f)
        val lineHeight = words.map { it.bounds.height() }.average().toFloat().coerceAtLeast(10f)
        val lineTolerance = lineHeight * 0.75f
        val regionBounds = layout.readingRegions.map { region ->
            region.order to RectF(
                region.boundsRatio.left * pageRight,
                region.boundsRatio.top * pageBottom,
                region.boundsRatio.right * pageRight,
                region.boundsRatio.bottom * pageBottom
            )
        }

        return when (layout.mode) {
            ReadingLayoutMode.SINGLE_COLUMN -> words.sortedWith(
                compareBy<RawWord>(
                    { locateRegionOrder(it.bounds, regionBounds) },
                    { (it.bounds.top / lineTolerance).roundToInt() },
                    { it.bounds.left }
                )
            )

            ReadingLayoutMode.TWO_COLUMNS -> words.sortedWith(
                compareBy<RawWord>(
                    { locateRegionOrder(it.bounds, regionBounds).coerceAtMost(1_000_000) },
                    { if (it.bounds.centerX() < splitX) 0 else 1 },
                    { (it.bounds.top / lineTolerance).roundToInt() },
                    { it.bounds.left }
                )
            )

            ReadingLayoutMode.HEADER_THEN_TWO_COLUMNS -> words.sortedWith(
                compareBy<RawWord>(
                    {
                        locateRegionOrder(it.bounds, regionBounds).coerceAtMost(1_000_000)
                    },
                    {
                        when {
                            it.bounds.bottom <= columnTop -> 0
                            it.bounds.centerX() < splitX -> 1
                            else -> 2
                        }
                    },
                    { (it.bounds.top / lineTolerance).roundToInt() },
                    { it.bounds.left }
                )
            )
        }
    }

    private fun locateRegionOrder(
        wordBounds: RectF,
        orderedRegions: List<Pair<Int, RectF>>
    ): Int {
        val centerX = wordBounds.centerX()
        val centerY = wordBounds.centerY()
        return orderedRegions.firstOrNull { (_, region) ->
            region.contains(centerX, centerY)
        }?.first ?: Int.MAX_VALUE
    }

    private class BoundingBoxStripper(
        private val targetPageIndex: Int
    ) : PDFTextStripper() {
        val words = mutableListOf<RawWord>()

        init {
            sortByPosition = true
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrEmpty() || textPositions.isNullOrEmpty()) return
            var builder = StringBuilder()
            var currentBounds: RectF? = null

            textPositions.forEach { position ->
                val unicode = position.unicode ?: return@forEach
                if (unicode.isBlank()) {
                    flush(builder, currentBounds)
                    builder = StringBuilder()
                    currentBounds = null
                } else {
                    builder.append(unicode)
                    val charRect = RectF(
                        position.xDirAdj,
                        position.yDirAdj - position.heightDir,
                        position.xDirAdj + position.widthDirAdj,
                        position.yDirAdj
                    )
                    currentBounds = currentBounds?.apply { union(charRect) } ?: charRect
                }
            }
            flush(builder, currentBounds)
        }

        private fun flush(builder: StringBuilder, bounds: RectF?) {
            if (builder.isNotBlank() && bounds != null) {
                words += RawWord(builder.toString(), RectF(bounds))
            }
        }

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) {
            if (currentPageNo - 1 != targetPageIndex) return
            super.startPage(page)
        }
    }

    private companion object {
        val sentenceTerminators = setOf('.', '!', '?', ';', ':')
    }
}
