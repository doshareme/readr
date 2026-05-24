package labs.dx.tts.engine

import kotlinx.coroutines.flow.SharedFlow
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.ReaderError

data class TtsSpeakRequest(
    val utteranceId: String,
    val text: String
)

sealed interface TtsEvent {
    data class Started(val utteranceId: String) : TtsEvent
    data class Range(val utteranceId: String, val start: Int, val end: Int) : TtsEvent
    data class Done(val utteranceId: String) : TtsEvent
    data class Error(val utteranceId: String?, val error: ReaderError) : TtsEvent
    data object Stopped : TtsEvent
}

sealed interface TtsEngineInitResult {
    data class Success(
        val voices: List<NarrationVoice>,
        val supportsRangeCallbacks: Boolean
    ) : TtsEngineInitResult

    data class Failure(val error: ReaderError) : TtsEngineInitResult
}

interface TtsEngine {
    val events: SharedFlow<TtsEvent>

    suspend fun initialize(): TtsEngineInitResult
    suspend fun applySettings(settings: NarrationSettings): Result<Unit>
    suspend fun speak(request: TtsSpeakRequest): Result<Unit>
    suspend fun stop()
    fun shutdown()
}
