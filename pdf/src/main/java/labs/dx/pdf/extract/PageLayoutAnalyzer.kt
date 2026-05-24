package labs.dx.pdf.extract

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import labs.dx.core.domain.model.PdfPageLayoutDebugInfo
import labs.dx.core.domain.model.PdfPageReadingMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

internal data class PageReadingLayout(
    val mode: ReadingLayoutMode,
    val splitRatio: Float? = null,
    val columnTopRatio: Float? = null,
    val readingRegions: List<ReadingRegion> = emptyList()
)

internal data class ReadingRegion(
    val order: Int,
    val role: ReadingRegionRole,
    val boundsRatio: RectF
)

internal enum class ReadingRegionRole {
    HEADER,
    LEFT_COLUMN,
    RIGHT_COLUMN,
    BODY
}

internal enum class ReadingLayoutMode {
    SINGLE_COLUMN,
    TWO_COLUMNS,
    HEADER_THEN_TWO_COLUMNS
}

internal fun PageReadingLayout.toDebugInfo(): PdfPageLayoutDebugInfo {
    return PdfPageLayoutDebugInfo(
        mode = when (mode) {
            ReadingLayoutMode.SINGLE_COLUMN -> PdfPageReadingMode.SINGLE_COLUMN
            ReadingLayoutMode.TWO_COLUMNS -> PdfPageReadingMode.TWO_COLUMNS
            ReadingLayoutMode.HEADER_THEN_TWO_COLUMNS -> PdfPageReadingMode.HEADER_THEN_TWO_COLUMNS
        },
        splitRatio = splitRatio,
        columnTopRatio = columnTopRatio,
        regionCount = readingRegions.size
    )
}

@Singleton
internal class PageLayoutAnalyzer @Inject constructor() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): PageReadingLayout {
        val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val lines = text.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line -> line.boundingBox?.let(::OcrLine) }

        val elements = text.textBlocks
            .flatMap { block -> block.lines }
            .flatMap { line -> line.elements }
            .mapNotNull { element -> element.boundingBox }

        if (elements.size < 12 || lines.size < 4) {
            return singleColumnLayout(lines, bitmap.width.toFloat(), bitmap.height.toFloat())
        }

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val candidateElements = elements
            .filter { rect -> rect.width() < width * 0.42f }
            .sortedBy { it.centerX() }

        if (candidateElements.size < 12) {
            return singleColumnLayout(lines, width, height)
        }

        var bestGap = 0f
        var splitX = width * 0.5f
        for (index in 0 until candidateElements.lastIndex) {
            val current = candidateElements[index]
            val next = candidateElements[index + 1]
            val gap = (next.left - current.right).toFloat()
            val midpoint = current.right.toFloat() + (gap / 2f)
            if (gap > bestGap && midpoint in (width * 0.28f)..(width * 0.72f)) {
                bestGap = gap
                splitX = midpoint
            }
        }

        val gutterRatio = bestGap / width
        val leftElements = candidateElements.filter { it.centerX() < splitX }
        val rightElements = candidateElements.filter { it.centerX() >= splitX }
        val gutterBand = bestGap.coerceAtLeast(width * 0.08f)
        val gutterLeft = splitX - gutterBand / 2f
        val gutterRight = splitX + gutterBand / 2f
        val centerElements = elements.count { rect ->
            rect.left < gutterRight && rect.right > gutterLeft
        }
        val sideElements = leftElements.size + rightElements.size
        val centerRatio = if (sideElements == 0) 1f else centerElements.toFloat() / sideElements.toFloat()
        val verticalSpreadLeft =
            (leftElements.maxOfOrNull { it.bottom.toFloat() } ?: 0f) -
                (leftElements.minOfOrNull { it.top.toFloat() } ?: 0f)
        val verticalSpreadRight =
            (rightElements.maxOfOrNull { it.bottom.toFloat() } ?: 0f) -
                (rightElements.minOfOrNull { it.top.toFloat() } ?: 0f)
        val hasTwoDenseSides = leftElements.size >= 8 && rightElements.size >= 8
        val hasVisibleGutter = gutterRatio >= 0.10f && centerRatio < 0.16f
        val minimumColumnHeight = bitmap.height.toFloat() * 0.35f
        val hasParallelSections = verticalSpreadLeft > minimumColumnHeight && verticalSpreadRight > minimumColumnHeight

        return if (hasTwoDenseSides && hasVisibleGutter && hasParallelSections) {
            val columnTop = detectColumnTop(
                bitmapHeight = height,
                splitX = splitX,
                leftElements = leftElements,
                rightElements = rightElements,
                allElements = elements
            )
            val orderedRegions = buildTwoColumnRegions(
                lines = lines,
                pageWidth = width,
                pageHeight = height,
                splitX = splitX,
                columnTop = columnTop
            )
            PageReadingLayout(
                mode = if (columnTop != null) ReadingLayoutMode.HEADER_THEN_TWO_COLUMNS else ReadingLayoutMode.TWO_COLUMNS,
                splitRatio = (splitX / width).coerceIn(0.30f, 0.70f),
                columnTopRatio = columnTop?.div(height),
                readingRegions = orderedRegions
            )
        } else {
            singleColumnLayout(lines, width, height)
        }
    }

    private fun singleColumnLayout(
        lines: List<OcrLine>,
        pageWidth: Float,
        pageHeight: Float
    ): PageReadingLayout {
        val regions = buildParagraphRegions(
            lines = lines.sortedBy { it.bounds.top },
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            role = ReadingRegionRole.BODY,
            startOrder = 0
        )
        return PageReadingLayout(
            mode = ReadingLayoutMode.SINGLE_COLUMN,
            readingRegions = regions
        )
    }

    private fun detectColumnTop(
        bitmapHeight: Float,
        splitX: Float,
        leftElements: List<android.graphics.Rect>,
        rightElements: List<android.graphics.Rect>,
        allElements: List<android.graphics.Rect>
    ): Float? {
        val leftTop = leftElements.minOfOrNull { it.top.toFloat() } ?: return null
        val rightTop = rightElements.minOfOrNull { it.top.toFloat() } ?: return null
        val candidateTop = maxOf(leftTop, rightTop)
        if (candidateTop < bitmapHeight * 0.18f) return null

        val headerElements = allElements.filter { it.bottom < candidateTop }
        if (headerElements.size < 4) return null

        val wideHeaderElements = headerElements.count { rect ->
            rect.width() > bitmapHeight * 0.10f || kotlin.math.abs(rect.centerX() - splitX) < bitmapHeight * 0.08f
        }
        val hasMeaningfulHeader = wideHeaderElements >= 3
        return candidateTop.takeIf { hasMeaningfulHeader }
    }

    private fun buildTwoColumnRegions(
        lines: List<OcrLine>,
        pageWidth: Float,
        pageHeight: Float,
        splitX: Float,
        columnTop: Float?
    ): List<ReadingRegion> {
        val headerLines = lines.filter { line ->
            columnTop != null && line.bounds.bottom < columnTop
        }.sortedBy { it.bounds.top }
        val bodyLines = lines.filter { line ->
            columnTop == null || line.bounds.bottom >= columnTop
        }
        val leftLines = bodyLines.filter { it.bounds.centerX() < splitX }.sortedBy { it.bounds.top }
        val rightLines = bodyLines.filter { it.bounds.centerX() >= splitX }.sortedBy { it.bounds.top }

        var order = 0
        val regions = mutableListOf<ReadingRegion>()
        if (headerLines.isNotEmpty()) {
            val headerRegions = buildParagraphRegions(
                lines = headerLines,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                role = ReadingRegionRole.HEADER,
                startOrder = order
            )
            regions += headerRegions
            order += headerRegions.size
        }
        val leftRegions = buildParagraphRegions(
            lines = leftLines,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            role = ReadingRegionRole.LEFT_COLUMN,
            startOrder = order
        )
        order += leftRegions.size
        val rightRegions = buildParagraphRegions(
            lines = rightLines,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            role = ReadingRegionRole.RIGHT_COLUMN,
            startOrder = order
        )
        return regions + leftRegions + rightRegions
    }

    private fun buildParagraphRegions(
        lines: List<OcrLine>,
        pageWidth: Float,
        pageHeight: Float,
        role: ReadingRegionRole,
        startOrder: Int
    ): List<ReadingRegion> {
        if (lines.isEmpty()) return emptyList()
        val averageLineHeight = lines.map { it.bounds.height().toFloat() }.average().toFloat().coerceAtLeast(12f)
        val verticalGapThreshold = averageLineHeight * 1.25f
        val horizontalTolerance = pageWidth * 0.06f
        val regions = mutableListOf<MutableList<OcrLine>>()

        lines.forEach { line ->
            val currentRegion = regions.lastOrNull()
            if (currentRegion == null) {
                regions += mutableListOf(line)
                return@forEach
            }
            val previous = currentRegion.last()
            val verticalGap = line.bounds.top - previous.bounds.bottom
            val similarColumnAlignment =
                kotlin.math.abs(line.bounds.left - previous.bounds.left) <= horizontalTolerance ||
                    overlapRatio(line.bounds, previous.bounds) >= 0.45f
            if (verticalGap <= verticalGapThreshold && similarColumnAlignment) {
                currentRegion += line
            } else {
                regions += mutableListOf(line)
            }
        }

        return regions.mapIndexed { index, groupedLines ->
            val bounds = RectF(groupedLines.first().bounds.toRectF())
            groupedLines.drop(1).forEach { bounds.union(it.bounds.toRectF()) }
            ReadingRegion(
                order = startOrder + index,
                role = role,
                boundsRatio = RectF(
                    bounds.left / pageWidth,
                    bounds.top / pageHeight,
                    bounds.right / pageWidth,
                    bounds.bottom / pageHeight
                )
            )
        }
    }

    private fun overlapRatio(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val right = minOf(a.right, b.right)
        val overlap = (right - left).coerceAtLeast(0)
        val minWidth = minOf(a.width(), b.width()).coerceAtLeast(1)
        return overlap.toFloat() / minWidth.toFloat()
    }

    private data class OcrLine(val bounds: Rect)

    private fun Rect.toRectF(): RectF = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}
