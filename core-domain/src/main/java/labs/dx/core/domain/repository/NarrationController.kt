package labs.dx.core.domain.repository

import kotlinx.coroutines.flow.StateFlow
import labs.dx.core.domain.model.NarrationHighlight
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError

sealed interface TtsInitializationResult {
    data class Success(
        val voices: List<NarrationVoice>,
        val supportsRangeCallbacks: Boolean
    ) : TtsInitializationResult

    data class Failure(val error: ReaderError) : TtsInitializationResult
}

interface NarrationController {
    val playbackState: StateFlow<PlaybackState>
    val currentHighlight: StateFlow<NarrationHighlight?>
    val currentWordIndex: StateFlow<Int?>
    val availableVoices: StateFlow<List<NarrationVoice>>
    val settings: StateFlow<NarrationSettings>

    suspend fun initialize(): TtsInitializationResult
    suspend fun prepare(session: PdfDocumentSession)
    suspend fun play(startWordIndex: Int)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seekToWord(globalWordIndex: Int, autoPlay: Boolean)
    suspend fun interruptAndPlayFromWord(globalWordIndex: Int)
    suspend fun updateSettings(settings: NarrationSettings)
    fun release()
}
