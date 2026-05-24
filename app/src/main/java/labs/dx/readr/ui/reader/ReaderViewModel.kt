package labs.dx.readr.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.PdfPageLayoutDebugInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError
import labs.dx.core.domain.model.ReadingPosition
import labs.dx.core.domain.repository.NarrationController
import labs.dx.core.domain.repository.OpenPdfResult
import labs.dx.core.domain.repository.PdfDocumentSession
import labs.dx.core.domain.repository.StorageRepository
import labs.dx.core.domain.repository.StorageResult
import labs.dx.core.domain.usecase.ObserveReadingProgressUseCase
import labs.dx.core.domain.usecase.OpenPdfDocumentUseCase
import labs.dx.core.domain.usecase.SaveReadingProgressUseCase
import labs.dx.core.domain.usecase.UpdatePdfHistoryCoverUseCase

@HiltViewModel
class ReaderViewModel @Inject constructor(
    app: Application,
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
    private val openPdfDocument: OpenPdfDocumentUseCase,
    private val observeReadingProgress: ObserveReadingProgressUseCase,
    private val saveReadingProgress: SaveReadingProgressUseCase,
    private val updatePdfHistoryCover: UpdatePdfHistoryCoverUseCase,
    private val narrationController: NarrationController
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val pageBitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }
    private val renderRequestMutex = Mutex()
    private val renderRequests = mutableMapOf<String, kotlinx.coroutines.Deferred<Bitmap?>>()
    private var totalWordCountJob: Job? = null
    private var session: PdfDocumentSession? = null
    private val documentUri: String = checkNotNull(savedStateHandle["uri"])

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val permission = storageRepository.persistReadPermission(documentUri)) {
                is StorageResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = permission.error)
                    return@launch
                }
                is StorageResult.Success -> Unit
            }

            val document = when (val result = storageRepository.describeDocument(documentUri)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.error)
                    return@launch
                }
            }

            when (val openResult = openPdfDocument(document)) {
                is OpenPdfResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        document = document,
                        error = openResult.error
                    )
                }
                is OpenPdfResult.Success -> {
                    session = openResult.session
                    saveDocumentCover(document.documentId, openResult.session)
                    openResult.session.setResearchPaperMode(_uiState.value.settings.researchPaperMode)
                    openResult.session.requestPageAnalysis(pageIndex = 0, urgent = true)
                    openResult.session.prefetchPageAnalysis(anchorPageIndex = 0)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        document = document,
                        documentInfo = openResult.session.documentInfo
                    )
                    if (openResult.session.documentInfo.hasExtractableText) {
                        when (val ttsResult = narrationController.initialize()) {
                            is labs.dx.core.domain.repository.TtsInitializationResult.Failure -> {
                                _uiState.value = _uiState.value.copy(error = ttsResult.error)
                            }
                            is labs.dx.core.domain.repository.TtsInitializationResult.Success -> {
                                val normalizedSettings = narrationController.settings.value
                                narrationController.updateSettings(normalizedSettings)
                                narrationController.prepare(openResult.session)
                                _uiState.value = _uiState.value.copy(
                                    voices = ttsResult.voices,
                                    settings = normalizedSettings
                                )
                            }
                        }
                    }
                    observeProgress(document.documentId)
                    bindPlaybackState()
                }
            }
        }
    }

    suspend fun loadPageInfo(pageIndex: Int): PdfPageInfo? = session?.getPageInfo(pageIndex)

    suspend fun loadPageLayoutDebugInfo(pageIndex: Int): PdfPageLayoutDebugInfo? {
        return session?.getPageLayoutDebugInfo(pageIndex)
    }

    suspend fun loadPageBitmap(pageIndex: Int, widthPx: Int): Bitmap? {
        val effectiveWidth = widthPx.coerceIn(480, 1280)
        val cacheKey = "$pageIndex-$effectiveWidth"
        pageBitmapCache.get(cacheKey)?.let { return it }
        session?.prefetchPageAnalysis(anchorPageIndex = pageIndex)
        val deferred = renderRequestMutex.withLock {
            renderRequests[cacheKey] ?: viewModelScope.async(Dispatchers.IO) {
                session?.renderPage(pageIndex, effectiveWidth)
            }.also { renderRequests[cacheKey] = it }
        }
        val bitmap = try {
            deferred.await()
        } finally {
            renderRequestMutex.withLock {
                if (renderRequests[cacheKey] == deferred) {
                    renderRequests.remove(cacheKey)
                }
            }
        } ?: return null
        pageBitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    suspend fun loadWords(pageIndex: Int): List<PdfWord> {
        return session?.getWordsForPage(pageIndex).orEmpty()
    }

    fun onPlayPauseTapped() {
        viewModelScope.launch {
            when (val playbackState = _uiState.value.playbackState) {
                is PlaybackState.Playing -> narrationController.pause()
                is PlaybackState.Preparing -> narrationController.stop()
                is PlaybackState.Paused,
                is PlaybackState.Stopped -> narrationController.resume()
                is PlaybackState.Completed -> narrationController.play(0)
                else -> narrationController.play(_uiState.value.savedWordIndex)
            }
        }
    }

    fun onStopTapped() {
        viewModelScope.launch {
            narrationController.stop()
        }
    }

    fun onSeekSentence(direction: Int) {
        seekByBoundary(direction, useParagraphs = false)
    }

    fun onSeekParagraph(direction: Int) {
        seekByBoundary(direction, useParagraphs = true)
    }

    fun onWordDoubleTapped(pageIndex: Int, normalizedTapX: Float, normalizedTapY: Float) {
        viewModelScope.launch {
            val pageInfo = session?.getPageInfo(pageIndex) ?: return@launch
            val pdfX = normalizedTapX * pageInfo.widthPoints
            val pdfY = normalizedTapY * pageInfo.heightPoints
            val word = session?.findWordAt(pageIndex, pdfX, pdfY) ?: return@launch
            session?.requestPageAnalysis(pageIndex = word.pageIndex, urgent = true, resetQueue = true)
            session?.prefetchPageAnalysis(anchorPageIndex = word.pageIndex)
            narrationController.interruptAndPlayFromWord(word.globalWordIndex)
        }
    }

    fun updateSpeechRate(rate: Float) {
        viewModelScope.launch {
            narrationController.updateSettings(_uiState.value.settings.copy(speechRate = rate))
        }
    }

    fun updateVoice(voiceName: String) {
        viewModelScope.launch {
            val voice = _uiState.value.voices.firstOrNull { it.voiceName == voiceName } ?: return@launch
            narrationController.updateSettings(
                _uiState.value.settings.copy(
                    voiceName = voice.voiceName,
                    localeTag = voice.localeTag
                )
            )
        }
    }

    fun updateCloudTts(enabled: Boolean) {
        viewModelScope.launch {
            val updatedSettings = _uiState.value.settings.copy(
                useCloudTts = enabled,
                voiceName = null,
                localeTag = null
            )
            narrationController.updateSettings(updatedSettings)
            _uiState.value = _uiState.value.copy(settings = updatedSettings)
        }
    }

    fun updateResearchPaperMode(enabled: Boolean) {
        viewModelScope.launch {
            session?.setResearchPaperMode(enabled)
            val updatedSettings = _uiState.value.settings.copy(researchPaperMode = enabled)
            narrationController.updateSettings(updatedSettings)
            _uiState.value = _uiState.value.copy(
                settings = updatedSettings,
                savedWordIndex = 0,
                currentHighlight = null,
                playbackState = PlaybackState.Stopped(0)
            )
            narrationController.stop()
            session?.requestPageAnalysis(pageIndex = 0, urgent = enabled, resetQueue = enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearReaderMemory()
    }

    private fun observeProgress(documentId: String) {
        observeReadingProgress(documentId)
            .onEach { progress ->
                val wordIndex = progress?.globalWordIndex ?: 0
                _uiState.value = _uiState.value.copy(savedWordIndex = wordIndex)
            }
            .launchIn(viewModelScope)
    }

    private fun bindPlaybackState() {
        narrationController.playbackState
            .combine(narrationController.currentHighlight) { playback, highlight ->
                playback to highlight
            }
            .onEach { (playback, highlight) ->
                _uiState.value = _uiState.value.copy(
                    playbackState = playback,
                    currentHighlight = highlight,
                    settings = narrationController.settings.value,
                    voices = narrationController.availableVoices.value
                )
                val currentWord = highlight?.word ?: return@onEach
                session?.requestPageAnalysis(pageIndex = currentWord.pageIndex, urgent = true)
                session?.prefetchPageAnalysis(anchorPageIndex = currentWord.pageIndex)
                saveReadingProgress(
                    ReadingPosition(
                        documentId = _uiState.value.document?.documentId ?: return@onEach,
                        globalWordIndex = currentWord.globalWordIndex,
                        pageIndex = currentWord.pageIndex,
                        sentenceIndex = currentWord.sentenceIndex,
                        paragraphIndex = currentWord.paragraphIndex,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    private suspend fun saveDocumentCover(documentId: String, session: PdfDocumentSession) {
        withContext(Dispatchers.IO) {
            val bitmap = runCatching { session.renderPage(0, 420) }.getOrNull() ?: return@withContext
            try {
                val coversDir = File(getApplication<Application>().filesDir, "document-covers")
                coversDir.mkdirs()
                val file = File(coversDir, "$documentId.png")
                file.outputStream().use { output ->
                    bitmap.compress(CompressFormat.PNG, 92, output)
                }
                updatePdfHistoryCover(documentId, file.absolutePath)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun clearReaderMemory() {
        totalWordCountJob?.cancel()
        totalWordCountJob = null
        viewModelScope.launch {
            renderRequestMutex.withLock {
                renderRequests.values.forEach { it.cancel() }
                renderRequests.clear()
            }
        }
        pageBitmapCache.evictAll()
        runCatching { session?.close() }
        session = null
        narrationController.release()
        _uiState.value = ReaderUiState()
    }

    private fun seekByBoundary(direction: Int, useParagraphs: Boolean) {
        viewModelScope.launch {
            val startIndex = _uiState.value.currentHighlight?.word?.globalWordIndex ?: _uiState.value.savedWordIndex
            var candidate = session?.getWordByGlobalIndex(startIndex) ?: return@launch
            val sourceBoundary = if (useParagraphs) candidate.paragraphIndex else candidate.sentenceIndex
            var probeIndex = startIndex

            while (true) {
                probeIndex += direction
                val nextWord = session?.getWordByGlobalIndex(probeIndex) ?: break
                val targetBoundary = if (useParagraphs) nextWord.paragraphIndex else nextWord.sentenceIndex
                if (targetBoundary != sourceBoundary) {
                    candidate = nextWord
                    if (direction < 0) {
                        var rewind = candidate
                        while (true) {
                            val previous = session?.getWordByGlobalIndex(rewind.globalWordIndex - 1) ?: break
                            val previousBoundary = if (useParagraphs) previous.paragraphIndex else previous.sentenceIndex
                            if (previousBoundary != targetBoundary) break
                            rewind = previous
                        }
                        candidate = rewind
                    }
                    break
                }
            }

            narrationController.seekToWord(candidate.globalWordIndex, autoPlay = true)
        }
    }
}
