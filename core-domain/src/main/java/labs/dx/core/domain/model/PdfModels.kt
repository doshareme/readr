package labs.dx.core.domain.model

import android.graphics.RectF

data class DocumentDescriptor(
    val documentId: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

data class PdfDocumentInfo(
    val document: DocumentDescriptor,
    val pageCount: Int,
    val isEncrypted: Boolean,
    val hasExtractableText: Boolean
)

data class PdfPageInfo(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int,
    val rotationDegrees: Int = 0
)

enum class PdfPageReadingMode {
    SINGLE_COLUMN,
    TWO_COLUMNS,
    HEADER_THEN_TWO_COLUMNS
}

data class PdfPageLayoutDebugInfo(
    val mode: PdfPageReadingMode,
    val splitRatio: Float? = null,
    val columnTopRatio: Float? = null,
    val regionCount: Int = 0
)

data class PdfWord(
    val text: String,
    val pageIndex: Int,
    val boundingBox: RectF,
    val normalizedText: String,
    val globalWordIndex: Int,
    val sentenceIndex: Int,
    val paragraphIndex: Int
)

data class PdfTextBlock(
    val pageIndex: Int,
    val paragraphIndex: Int,
    val words: List<PdfWord>
)
