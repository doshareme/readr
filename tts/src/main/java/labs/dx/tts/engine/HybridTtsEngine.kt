package labs.dx.tts.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.ReaderError

@Singleton
class HybridTtsEngine @Inject constructor(
    private val localEngine: AndroidTtsEngine,
    private val cloudEngine: RumikCloudTtsEngine
) : TtsEngine {
    override val events: SharedFlow<TtsEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    private val mutableEvents = events as MutableSharedFlow<TtsEvent>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentSettings = NarrationSettings()
    private var activeEngine: TtsEngine = localEngine

    init {
        localEngine.events.onEach { event -> mutableEvents.emit(event) }.launchIn(scope)
        cloudEngine.events.onEach { event -> mutableEvents.emit(event) }.launchIn(scope)
    }

    override suspend fun initialize(): TtsEngineInitResult {
        val selected = selectEngine(currentSettings)
        return when (val result = selected.initialize()) {
            is TtsEngineInitResult.Failure -> result
            is TtsEngineInitResult.Success -> {
                activeEngine = selected
                result
            }
        }
    }

    override suspend fun applySettings(settings: NarrationSettings): Result<Unit> {
        val previousSettings = currentSettings
        val previousEngine = activeEngine
        val selected = selectEngine(settings)
        if (selected !== activeEngine) {
            when (val result = selected.initialize()) {
                is TtsEngineInitResult.Failure -> return Result.failure(
                    IllegalStateException(result.error.toDisplayMessage())
                )
                is TtsEngineInitResult.Success -> Unit
            }
            activeEngine.stop()
            activeEngine = selected
        }
        val result = activeEngine.applySettings(settings)
        if (result.isFailure) {
            if (activeEngine !== previousEngine) {
                activeEngine.stop()
                activeEngine = previousEngine
            }
            currentSettings = previousSettings
            return result
        }
        currentSettings = settings
        return Result.success(Unit)
    }

    override suspend fun speak(request: TtsSpeakRequest): Result<Unit> {
        return activeEngine.speak(request)
    }

    override suspend fun stop() {
        activeEngine.stop()
    }

    override fun shutdown() {
        localEngine.shutdown()
        cloudEngine.shutdown()
        activeEngine = localEngine
    }

    private fun selectEngine(settings: NarrationSettings): TtsEngine {
        return if (settings.useCloudTts) cloudEngine else localEngine
    }

    private fun ReaderError.toDisplayMessage(): String {
        return when (this) {
            is ReaderError.Unknown -> message
            ReaderError.CorruptedPdf -> "This PDF appears to be corrupted and could not be opened."
            ReaderError.DocumentNotFound -> "The selected document is no longer available."
            ReaderError.EncryptedPdf -> "Encrypted PDFs are not supported without the password."
            ReaderError.ImageOnlyPdf -> "This looks like a scanned PDF without extractable text."
            ReaderError.MissingTtsEngine -> "No on-device Text-to-Speech engine is available."
            ReaderError.StoragePermissionDenied -> "Read permission for the selected document was denied."
            ReaderError.UnsupportedDocumentFormat -> "This document format is recognized but cannot be rendered on this device yet."
            ReaderError.UnsupportedLanguage -> "The selected TTS language or voice is not supported on this device."
        }
    }
}
