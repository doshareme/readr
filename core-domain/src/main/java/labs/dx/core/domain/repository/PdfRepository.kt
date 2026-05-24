package labs.dx.core.domain.repository

import android.graphics.Bitmap
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PdfPageLayoutDebugInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.model.ReaderError
import java.io.Closeable

interface PdfDocumentSession : Closeable {
    val documentInfo: PdfDocumentInfo

    suspend fun getPageInfo(pageIndex: Int): PdfPageInfo
    suspend fun getPageLayoutDebugInfo(pageIndex: Int): PdfPageLayoutDebugInfo?
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap
    suspend fun setResearchPaperMode(enabled: Boolean)
    suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean = false, resetQueue: Boolean = false)
    suspend fun prefetchPageAnalysis(anchorPageIndex: Int)
    suspend fun getWordsForPage(pageIndex: Int): List<PdfWord>
    suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord?
    suspend fun getTotalWordCount(): Int
    suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord?
}

sealed interface OpenPdfResult {
    data class Success(val session: PdfDocumentSession) : OpenPdfResult
    data class Failure(val error: ReaderError) : OpenPdfResult
}

interface PdfRepository {
    suspend fun openDocument(document: DocumentDescriptor): OpenPdfResult
}
