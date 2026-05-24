package labs.dx.pdf.session

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.DocumentFormat
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PdfPageLayoutDebugInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.model.ReaderError
import labs.dx.core.domain.repository.OpenPdfResult
import labs.dx.core.domain.repository.PdfDocumentSession
import labs.dx.core.domain.repository.PdfRepository
import labs.dx.pdf.extract.PageExtractionResult
import labs.dx.pdf.extract.PageLayoutAnalyzer
import labs.dx.pdf.extract.PageReadingLayout
import labs.dx.pdf.extract.PdfWordExtractor
import labs.dx.pdf.extract.toDebugInfo
import java.io.FileNotFoundException

@Singleton
class AndroidPdfRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfRepository {
    override suspend fun openDocument(document: DocumentDescriptor): OpenPdfResult = withContext(Dispatchers.IO) {
        val format = DocumentFormat.from(document)
        if (format != DocumentFormat.PDF) {
            return@withContext openGenericDocument(document, format)
        }
        PDFBoxResourceLoader.init(context)
        val uri = Uri.parse(document.uriString)
        val descriptor = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrElse { error ->
            return@withContext OpenPdfResult.Failure(
                when (error) {
                    is SecurityException -> ReaderError.StoragePermissionDenied
                    is FileNotFoundException -> ReaderError.DocumentNotFound
                    else -> ReaderError.Unknown("Unable to open file descriptor", error)
                }
            )
        } ?: return@withContext OpenPdfResult.Failure(ReaderError.DocumentNotFound)

        val pdDocument = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input)
            } ?: throw FileNotFoundException("Input stream was null")
        }.getOrElse { error ->
            descriptor.close()
            return@withContext OpenPdfResult.Failure(
                when (error) {
                    is SecurityException -> ReaderError.StoragePermissionDenied
                    else -> ReaderError.CorruptedPdf
                }
            )
        }

        if (pdDocument.isEncrypted) {
            pdDocument.close()
            descriptor.close()
            return@withContext OpenPdfResult.Failure(ReaderError.EncryptedPdf)
        }

        val renderer = runCatching { PdfRenderer(descriptor) }.getOrElse { error ->
            pdDocument.close()
            descriptor.close()
            return@withContext OpenPdfResult.Failure(ReaderError.Unknown("Unable to create PdfRenderer", error))
        }

        val session = PdfBoxDocumentSession(
            documentDescriptor = document,
            parcelFileDescriptor = descriptor,
            renderer = renderer,
            pdDocument = pdDocument,
            extractor = PdfWordExtractor(),
            pageLayoutAnalyzer = PageLayoutAnalyzer()
        )

        if (!session.hasAnyExtractableText()) {
            session.close()
            return@withContext OpenPdfResult.Failure(ReaderError.ImageOnlyPdf)
        }

        OpenPdfResult.Success(session)
    }

    private fun openGenericDocument(
        document: DocumentDescriptor,
        format: DocumentFormat
    ): OpenPdfResult {
        val uri = Uri.parse(document.uriString)
        return runCatching {
            when (format) {
                DocumentFormat.TXT -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::plainText)
                )
                DocumentFormat.HTML -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::html)
                )
                DocumentFormat.XML -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::xml)
                )
                DocumentFormat.DOCX -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::docx)
                )
                DocumentFormat.EPUB,
                DocumentFormat.IBOOKS -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::epub)
                )
                DocumentFormat.DOC,
                DocumentFormat.CHM -> TextDocumentSession(
                    document,
                    readBytes(uri).let(DocumentTextExtractor::binaryText)
                )
                DocumentFormat.CBZ -> ComicArchiveDocumentSession(
                    resolver = context.contentResolver,
                    documentDescriptor = document,
                    uri = uri,
                    archiveType = ComicArchiveType.ZIP
                )
                DocumentFormat.CBT -> ComicArchiveDocumentSession(
                    resolver = context.contentResolver,
                    documentDescriptor = document,
                    uri = uri,
                    archiveType = ComicArchiveType.TAR
                )
                DocumentFormat.CBR,
                DocumentFormat.CB7,
                DocumentFormat.CBA,
                DocumentFormat.UNKNOWN -> return OpenPdfResult.Failure(ReaderError.UnsupportedDocumentFormat)
                DocumentFormat.PDF -> error("PDF documents use the PDF session")
            }
        }.fold(
            onSuccess = { session -> OpenPdfResult.Success(session) },
            onFailure = { error ->
                OpenPdfResult.Failure(
                    when (error) {
                        is SecurityException -> ReaderError.StoragePermissionDenied
                        is FileNotFoundException -> ReaderError.DocumentNotFound
                        else -> ReaderError.Unknown("Unable to open ${format.displayName}", error)
                    }
                )
            }
        )
    }

    private fun readBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("Input stream was null")
    }
}

private class PdfBoxDocumentSession(
    private val documentDescriptor: DocumentDescriptor,
    private val parcelFileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val pdDocument: PDDocument,
    private val extractor: PdfWordExtractor,
    private val pageLayoutAnalyzer: PageLayoutAnalyzer
) : PdfDocumentSession {

    override val documentInfo: PdfDocumentInfo = PdfDocumentInfo(
        document = documentDescriptor,
        pageCount = renderer.pageCount,
        isEncrypted = false,
        hasExtractableText = true
    )

    private val pageInfoCache = mutableMapOf<Int, PdfPageInfo>()
    private val pageWordsCache = mutableMapOf<Int, List<PdfWord>>()
    private val pageLayoutCache = mutableMapOf<Int, PageReadingLayout>()
    private val pageLayoutDeferreds = mutableMapOf<Int, CompletableDeferred<PageReadingLayout>>()
    private val pendingAnalysisPages = mutableSetOf<Int>()
    private val indexMutex = Mutex()
    private val rendererMutex = Mutex()
    private val analysisMutex = Mutex()
    private val analysisWakeChannel = Channel<Unit>(Channel.CONFLATED)
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analysisQueue = PriorityQueue<PageAnalysisRequest>()
    private var analysisSequence = 0L
    private var analysisGeneration = 0
    private val analysisWorker: Job
    private var indexedThroughPage = -1
    private var nextGlobalWordIndex = 0
    private var nextSentenceIndex = 0
    private var nextParagraphIndex = 0
    private var researchPaperModeEnabled = false

    init {
        analysisWorker = analysisScope.launch {
            while (isActive) {
                analysisWakeChannel.receive()
                drainAnalysisQueue()
            }
        }
    }

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo = withContext(Dispatchers.IO) {
        pageInfoCache[pageIndex] ?: rendererMutex.withLock {
            pageInfoCache[pageIndex] ?: renderer.openPage(pageIndex).use { page ->
                PdfPageInfo(
                    pageIndex = pageIndex,
                    widthPoints = page.width,
                    heightPoints = page.height,
                    rotationDegrees = 0
                ).also { pageInfoCache[pageIndex] = it }
            }
        }
    }

    override suspend fun getPageLayoutDebugInfo(pageIndex: Int): PdfPageLayoutDebugInfo? {
        if (!researchPaperModeEnabled) return null
        val layout = getReadingLayout(pageIndex)
        return layout.toDebugInfo()
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        rendererMutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                val safeTargetWidth = targetWidthPx.coerceIn(480, 1280)
                val scale = safeTargetWidth.toFloat() / page.width.toFloat()
                val bitmapWidth = safeTargetWidth.coerceAtLeast(1)
                val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    override suspend fun setResearchPaperMode(enabled: Boolean) {
        if (researchPaperModeEnabled == enabled) return
        indexMutex.withLock {
            researchPaperModeEnabled = enabled
            resetIndexedWordsLocked()
        }
    }

    private fun resetIndexedWordsLocked() {
        pageWordsCache.clear()
        indexedThroughPage = -1
        nextGlobalWordIndex = 0
        nextSentenceIndex = 0
        nextParagraphIndex = 0
    }

    override suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean, resetQueue: Boolean) {
        enqueuePageAnalysis(pageIndex = pageIndex, urgent = urgent, resetQueue = resetQueue)
    }

    override suspend fun prefetchPageAnalysis(anchorPageIndex: Int) {
        enqueuePageAnalysis(anchorPageIndex, urgent = false, resetQueue = false)
        if (anchorPageIndex + 1 < documentInfo.pageCount) {
            enqueuePageAnalysis(anchorPageIndex + 1, urgent = false, resetQueue = false)
        }
    }

    override suspend fun getWordsForPage(pageIndex: Int): List<PdfWord> {
        ensureIndexedThrough(pageIndex)
        return pageWordsCache[pageIndex].orEmpty()
    }

    override suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord? {
        ensureIndexedUntilWord(globalWordIndex)
        return pageWordsCache.values.flatten().firstOrNull { it.globalWordIndex == globalWordIndex }
    }

    override suspend fun getTotalWordCount(): Int {
        ensureIndexedThrough(documentInfo.pageCount - 1)
        return nextGlobalWordIndex
    }

    override suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord? {
        return getWordsForPage(pageIndex).firstOrNull { word ->
            word.boundingBox.contains(pdfX, pdfY)
        }
    }

    suspend fun hasAnyExtractableText(maxPagesToCheck: Int = 5): Boolean {
        val lastPage = minOf(documentInfo.pageCount - 1, maxPagesToCheck - 1)
        if (lastPage < 0) return false
        for (pageIndex in 0..lastPage) {
            val result: PageExtractionResult = extractor.extractPage(
                document = pdDocument,
                pageIndex = pageIndex,
                globalWordStart = 0,
                sentenceStart = 0,
                paragraphStart = 0
            )
            if (result.words.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private suspend fun getReadingLayout(pageIndex: Int): PageReadingLayout {
        if (!researchPaperModeEnabled) {
            return PageReadingLayout(mode = labs.dx.pdf.extract.ReadingLayoutMode.SINGLE_COLUMN)
        }
        pageLayoutCache[pageIndex]?.let { return it }
        val deferred = enqueuePageAnalysis(pageIndex = pageIndex, urgent = true, resetQueue = false)
        return deferred.await()
    }

    private suspend fun ensureIndexedUntilWord(globalWordIndex: Int) {
        indexMutex.withLock {
            while (nextGlobalWordIndex <= globalWordIndex && indexedThroughPage < documentInfo.pageCount - 1) {
                extractNextPageLocked()
            }
        }
    }

    private suspend fun ensureIndexedThrough(pageIndex: Int) {
        indexMutex.withLock {
            while (indexedThroughPage < pageIndex) {
                extractNextPageLocked()
            }
        }
    }

    private suspend fun extractNextPageLocked() {
        val nextPage = indexedThroughPage + 1
        if (nextPage >= documentInfo.pageCount) return
        val layout = getReadingLayout(nextPage)
        val result: PageExtractionResult = extractor.extractPage(
            document = pdDocument,
            pageIndex = nextPage,
            globalWordStart = nextGlobalWordIndex,
            sentenceStart = nextSentenceIndex,
            paragraphStart = nextParagraphIndex,
            layout = layout
        )
        pageWordsCache[nextPage] = result.words
        nextGlobalWordIndex = result.nextGlobalWordIndex
        nextSentenceIndex = result.nextSentenceIndex
        nextParagraphIndex = result.nextParagraphIndex
        indexedThroughPage = nextPage
    }

    override fun close() {
        analysisWorker.cancel()
        runCatching { renderer.close() }
        runCatching { parcelFileDescriptor.close() }
        runCatching { pdDocument.close() }
    }

    private suspend fun enqueuePageAnalysis(
        pageIndex: Int,
        urgent: Boolean,
        resetQueue: Boolean
    ): CompletableDeferred<PageReadingLayout> {
        pageLayoutCache[pageIndex]?.let { cached ->
            return CompletableDeferred<PageReadingLayout>().apply { complete(cached) }
        }
        return analysisMutex.withLock {
            if (resetQueue) {
                analysisGeneration += 1
                analysisQueue.clear()
                pendingAnalysisPages.clear()
            }
            pageLayoutCache[pageIndex]?.let { cached ->
                return@withLock CompletableDeferred<PageReadingLayout>().apply { complete(cached) }
            }
            val existingDeferred = pageLayoutDeferreds[pageIndex]
            if (existingDeferred != null && pageIndex in pendingAnalysisPages && !resetQueue) {
                return@withLock existingDeferred
            }
            val deferred = existingDeferred ?: CompletableDeferred<PageReadingLayout>().also {
                pageLayoutDeferreds[pageIndex] = it
            }
            val priority = if (urgent) 0 else 1
            pendingAnalysisPages += pageIndex
            analysisQueue.add(
                PageAnalysisRequest(
                    pageIndex = pageIndex,
                    priority = priority,
                    generation = analysisGeneration,
                    sequence = analysisSequence++
                )
            )
            analysisWakeChannel.trySend(Unit)
            deferred
        }
    }

    private suspend fun drainAnalysisQueue() {
        while (true) {
            val next = analysisMutex.withLock {
                while (analysisQueue.isNotEmpty()) {
                    val candidate = analysisQueue.poll() ?: continue
                    if (candidate.generation == analysisGeneration && pageLayoutCache[candidate.pageIndex] == null) {
                        return@withLock candidate
                    }
                }
                null
            } ?: return

            val layout = analyzePageLayout(next.pageIndex)
            analysisMutex.withLock {
                pageLayoutCache[next.pageIndex] = layout
                pendingAnalysisPages.remove(next.pageIndex)
                pageLayoutDeferreds.remove(next.pageIndex)?.complete(layout)
            }
        }
    }

    private suspend fun analyzePageLayout(pageIndex: Int): PageReadingLayout {
        val bitmap = renderPage(pageIndex, 540)
        return try {
            runCatching { pageLayoutAnalyzer.analyze(bitmap) }
                .getOrDefault(PageReadingLayout(mode = labs.dx.pdf.extract.ReadingLayoutMode.SINGLE_COLUMN))
        } finally {
            bitmap.recycle()
        }
    }

    private data class PageAnalysisRequest(
        val pageIndex: Int,
        val priority: Int,
        val generation: Int,
        val sequence: Long
    ) : Comparable<PageAnalysisRequest> {
        override fun compareTo(other: PageAnalysisRequest): Int {
            return compareValuesBy(this, other, { it.priority }, { it.sequence })
        }
    }
}
