package labs.dx.playback.controller

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import labs.dx.core.domain.model.HighlightMode
import labs.dx.core.domain.model.NarrationHighlight
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError
import labs.dx.core.domain.repository.NarrationController
import labs.dx.core.domain.repository.PdfDocumentSession
import labs.dx.core.domain.repository.TtsInitializationResult
import labs.dx.tts.engine.TtsEngine
import labs.dx.tts.engine.TtsEngineInitResult
import labs.dx.tts.engine.TtsEvent
import labs.dx.tts.engine.TtsSpeakRequest

@Singleton
class NarrationPlaybackController @Inject constructor(
    private val ttsEngine: TtsEngine
) : NarrationController {

    override val playbackState: StateFlow<PlaybackState> get() = _playbackState.asStateFlow()
    override val currentHighlight: StateFlow<NarrationHighlight?> get() = _currentHighlight.asStateFlow()
    override val currentWordIndex: StateFlow<Int?> get() = _currentWordIndex.asStateFlow()
    override val availableVoices: StateFlow<List<NarrationVoice>> get() = _availableVoices.asStateFlow()
    override val settings: StateFlow<NarrationSettings> get() = _settings.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val _currentHighlight = MutableStateFlow<NarrationHighlight?>(null)
    private val _currentWordIndex = MutableStateFlow<Int?>(null)
    private val _availableVoices = MutableStateFlow(emptyList<NarrationVoice>())
    private val _settings = MutableStateFlow(NarrationSettings())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chunker = UtteranceChunker()
    private val playbackMutex = Mutex()
    private var session: PdfDocumentSession? = null
    private var activeChunk: UtteranceChunk? = null
    private var queuedChunk: UtteranceChunk? = null
    private var lastStableWordIndex: Int = 0
    private var supportsRangeCallbacks: Boolean = false
    private var eventJob: Job? = null
    private var nextParagraphPrefetchJob: Job? = null
    private var suppressNextStoppedEvent: Boolean = false

    override suspend fun initialize(): TtsInitializationResult {
        if (eventJob == null) {
            eventJob = ttsEngine.events.onEach(::handleTtsEvent).launchIn(scope)
        }
        return when (val result = ttsEngine.initialize()) {
            is TtsEngineInitResult.Success -> {
                supportsRangeCallbacks = result.supportsRangeCallbacks
                _availableVoices.value = result.voices
                TtsInitializationResult.Success(result.voices, result.supportsRangeCallbacks)
            }
            is TtsEngineInitResult.Failure -> {
                _playbackState.value = PlaybackState.Error(result.error)
                TtsInitializationResult.Failure(result.error)
            }
        }
    }

    override suspend fun prepare(session: PdfDocumentSession) {
        playbackMutex.withLock {
            this.session = session
            activeChunk = null
            queuedChunk = null
            _currentHighlight.value = null
            _currentWordIndex.value = null
            lastStableWordIndex = 0
            nextParagraphPrefetchJob?.cancel()
            nextParagraphPrefetchJob = null
            _playbackState.value = PlaybackState.Idle
        }
    }

    override suspend fun play(startWordIndex: Int) {
        playbackMutex.withLock {
            val activeSession = session ?: run {
                _playbackState.value = PlaybackState.Error(ReaderError.Unknown("PDF session was not prepared"))
                return
            }
            val chunk = chunker.buildChunk(startWordIndex) { index ->
                activeSession.getWordByGlobalIndex(index)
            } ?: run {
                _playbackState.value = PlaybackState.Completed
                return
            }
            lastStableWordIndex = chunk.words.first().globalWordIndex
            activeChunk = chunk
            queuedChunk = null
            _currentWordIndex.value = lastStableWordIndex
            _currentHighlight.value = NarrationHighlight(chunk.words.first(), HighlightMode.CHUNK)
            _playbackState.value = PlaybackState.Preparing
            val speakResult = ttsEngine.speak(TtsSpeakRequest(chunk.utteranceId, chunk.text))
            if (speakResult.isFailure) {
                _playbackState.value = PlaybackState.Error(
                    speakResult.exceptionOrNull().toReaderError("Unable to start TTS")
                )
            } else {
                prefetchNextParagraph(chunk.words.last().globalWordIndex + 1)
            }
        }
    }

    override suspend fun pause() {
        playbackMutex.withLock {
            ttsEngine.stop()
            nextParagraphPrefetchJob?.cancel()
            queuedChunk = null
            _playbackState.value = PlaybackState.Paused(lastStableWordIndex)
        }
    }

    override suspend fun resume() {
        play(lastStableWordIndex)
    }

    override suspend fun stop() {
        playbackMutex.withLock {
            ttsEngine.stop()
            nextParagraphPrefetchJob?.cancel()
            queuedChunk = null
            _playbackState.value = PlaybackState.Stopped(lastStableWordIndex)
        }
    }

    override suspend fun seekToWord(globalWordIndex: Int, autoPlay: Boolean) {
        playbackMutex.withLock {
            lastStableWordIndex = globalWordIndex
            ttsEngine.stop()
            nextParagraphPrefetchJob?.cancel()
            queuedChunk = null
            val word = session?.getWordByGlobalIndex(globalWordIndex)
            _currentWordIndex.value = globalWordIndex
            _currentHighlight.value = word?.let { NarrationHighlight(it, HighlightMode.CHUNK) }
            _playbackState.value = PlaybackState.Paused(globalWordIndex)
        }
        if (autoPlay) play(globalWordIndex)
    }

    override suspend fun interruptAndPlayFromWord(globalWordIndex: Int) {
        playbackMutex.withLock {
            lastStableWordIndex = globalWordIndex
            suppressNextStoppedEvent = true
            ttsEngine.stop()
            nextParagraphPrefetchJob?.cancel()
            nextParagraphPrefetchJob = null
            activeChunk = null
            queuedChunk = null
            val word = session?.getWordByGlobalIndex(globalWordIndex)
            _currentWordIndex.value = globalWordIndex
            _currentHighlight.value = word?.let { NarrationHighlight(it, HighlightMode.CHUNK) }
            _playbackState.value = PlaybackState.Paused(globalWordIndex)
        }
        play(globalWordIndex)
    }

    override suspend fun updateSettings(settings: NarrationSettings) {
        val previousSettings = _settings.value
        val engineModeChanged = previousSettings.useCloudTts != settings.useCloudTts
        val result = ttsEngine.applySettings(settings)
        if (result.isFailure) {
            _settings.value = previousSettings
            _playbackState.value = PlaybackState.Error(
                result.exceptionOrNull().toReaderError("Unable to update Text-to-Speech settings")
            )
            return
        }
        _settings.value = settings
        if (engineModeChanged) {
            when (val initResult = ttsEngine.initialize()) {
                is TtsEngineInitResult.Failure -> {
                    _playbackState.value = PlaybackState.Error(initResult.error)
                }
                is TtsEngineInitResult.Success -> {
                    supportsRangeCallbacks = initResult.supportsRangeCallbacks
                    _availableVoices.value = initResult.voices
                }
            }
        }
    }

    private fun Throwable?.toReaderError(prefix: String): ReaderError.Unknown {
        val detail = this?.message?.takeIf { it.isNotBlank() }
        val message = if (detail == null) prefix else "$prefix: $detail"
        return ReaderError.Unknown(message, this)
    }

    override fun release() {
        scope.launch {
            ttsEngine.stop()
        }
        nextParagraphPrefetchJob?.cancel()
        session = null
        activeChunk = null
        queuedChunk = null
        ttsEngine.shutdown()
    }

    private fun handleTtsEvent(event: TtsEvent) {
        scope.launch {
            when (event) {
                is TtsEvent.Started -> {
                    if (activeChunk?.utteranceId != event.utteranceId) {
                        queuedChunk
                            ?.takeIf { it.utteranceId == event.utteranceId }
                            ?.let { nextChunk ->
                                activeChunk = nextChunk
                                queuedChunk = null
                            }
                    }
                    val firstWord = activeChunk
                        ?.takeIf { it.utteranceId == event.utteranceId }
                        ?.words
                        ?.firstOrNull()
                        ?: return@launch
                    lastStableWordIndex = firstWord.globalWordIndex
                    _currentHighlight.value = NarrationHighlight(firstWord, HighlightMode.CHUNK)
                    _currentWordIndex.value = firstWord.globalWordIndex
                    _playbackState.value = PlaybackState.Playing(firstWord.globalWordIndex)
                }

                is TtsEvent.NeedsNext -> {
                    val chunk = activeChunk?.takeIf { it.utteranceId == event.utteranceId } ?: return@launch
                    queueNextChunkAfter(chunk)
                }

                is TtsEvent.Range -> {
                    val chunk = activeChunk ?: return@launch
                    val index = chunk.offsets.indexOfFirst { event.start in it || event.end in it || event.start == it.first }
                    val word = chunk.words.getOrNull(index.takeIf { it >= 0 } ?: 0) ?: return@launch
                    lastStableWordIndex = word.globalWordIndex
                    _currentHighlight.value = NarrationHighlight(
                        word = word,
                        mode = if (supportsRangeCallbacks) HighlightMode.WORD else HighlightMode.CHUNK
                    )
                    _currentWordIndex.value = word.globalWordIndex
                    _playbackState.value = PlaybackState.Playing(word.globalWordIndex)
                }

                is TtsEvent.Done -> {
                    val nextWordIndex = activeChunk?.words?.lastOrNull()?.globalWordIndex?.plus(1)
                    if (queuedChunk != null) {
                        return@launch
                    }
                    activeChunk = null
                    if (nextWordIndex == null) {
                        _playbackState.value = PlaybackState.Completed
                        return@launch
                    }
                    val nextWord = session?.getWordByGlobalIndex(nextWordIndex)
                    if (nextWord == null) {
                        _playbackState.value = PlaybackState.Completed
                    } else {
                        play(nextWord.globalWordIndex)
                    }
                }

                is TtsEvent.Error -> {
                    _playbackState.value = PlaybackState.Error(event.error)
                }

                TtsEvent.Stopped -> {
                    if (suppressNextStoppedEvent) {
                        suppressNextStoppedEvent = false
                        return@launch
                    }
                    if (_playbackState.value !is PlaybackState.Paused && _playbackState.value !is PlaybackState.Stopped) {
                        _playbackState.value = PlaybackState.Paused(lastStableWordIndex)
                    }
                }
            }
        }
    }

    private fun prefetchNextParagraph(startWordIndex: Int) {
        val activeSession = session ?: return
        nextParagraphPrefetchJob?.cancel()
        nextParagraphPrefetchJob = scope.launch(Dispatchers.Default) {
            chunker.buildChunk(startWordIndex) { index ->
                activeSession.getWordByGlobalIndex(index)
            }
        }
    }

    private suspend fun queueNextChunkAfter(chunk: UtteranceChunk) {
        if (!_settings.value.useCloudTts || queuedChunk != null) return
        val activeSession = session ?: return
        val nextChunk = chunker.buildChunk(chunk.words.last().globalWordIndex + 1) { index ->
            activeSession.getWordByGlobalIndex(index)
        } ?: return
        queuedChunk = nextChunk
        val speakResult = ttsEngine.speak(TtsSpeakRequest(nextChunk.utteranceId, nextChunk.text))
        if (speakResult.isFailure) {
            queuedChunk = null
            _playbackState.value = PlaybackState.Error(
                speakResult.exceptionOrNull().toReaderError("Unable to queue TTS")
            )
        } else {
            prefetchNextParagraph(nextChunk.words.last().globalWordIndex + 1)
        }
    }
}
