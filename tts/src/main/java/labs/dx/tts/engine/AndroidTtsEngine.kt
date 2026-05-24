package labs.dx.tts.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.ReaderError
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {

    override val events: SharedFlow<TtsEvent> = MutableSharedFlow(extraBufferCapacity = 32)
    private val mutableEvents = events as MutableSharedFlow<TtsEvent>
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Main

    private var textToSpeech: TextToSpeech? = null
    private var currentSettings: NarrationSettings = NarrationSettings()

    override suspend fun initialize(): TtsEngineInitResult = withContext(ioDispatcher) {
        if (textToSpeech != null) {
            val existingTts = requireNotNull(textToSpeech)
            return@withContext TtsEngineInitResult.Success(
                voices = buildVoices(existingTts),
                supportsRangeCallbacks = true
            )
        }

        suspendCancellableCoroutine<TtsEngineInitResult> { continuation ->
            var createdTts: TextToSpeech? = null
            createdTts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    continuation.resume(TtsEngineInitResult.Failure(ReaderError.MissingTtsEngine))
                    return@TextToSpeech
                }
                val tts = createdTts ?: return@TextToSpeech
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mutableEvents.tryEmit(TtsEvent.Started(utteranceId.orEmpty()))
                    }

                    override fun onDone(utteranceId: String?) {
                        mutableEvents.tryEmit(TtsEvent.Done(utteranceId.orEmpty()))
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mutableEvents.tryEmit(TtsEvent.Error(utteranceId, ReaderError.Unknown("TTS error")))
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mutableEvents.tryEmit(
                            TtsEvent.Error(
                                utteranceId,
                                ReaderError.Unknown("TTS error code: $errorCode")
                            )
                        )
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        mutableEvents.tryEmit(TtsEvent.Stopped)
                    }

                    override fun onRangeStart(
                        utteranceId: String?,
                        start: Int,
                        end: Int,
                        frame: Int
                    ) {
                        mutableEvents.tryEmit(TtsEvent.Range(utteranceId.orEmpty(), start, end))
                    }
                })
                textToSpeech = tts
                applySettingsInternal(tts, currentSettings)
                continuation.resume(
                    TtsEngineInitResult.Success(
                        voices = buildVoices(tts),
                        supportsRangeCallbacks = true
                    )
                )
            }

            continuation.invokeOnCancellation {
                createdTts?.shutdown()
            }
        }
    }

    override suspend fun applySettings(settings: NarrationSettings): Result<Unit> = withContext(ioDispatcher) {
        currentSettings = settings
        val tts = textToSpeech ?: return@withContext Result.failure(IllegalStateException("TTS not initialized"))
        applySettingsInternal(tts, settings)
    }

    override suspend fun speak(request: TtsSpeakRequest): Result<Unit> = withContext(ioDispatcher) {
        val tts = textToSpeech ?: return@withContext Result.failure(IllegalStateException("TTS not initialized"))
        val bundle = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, request.utteranceId)
        }
        val status = tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, bundle, request.utteranceId)
        if (status == TextToSpeech.SUCCESS) Result.success(Unit)
        else Result.failure(IllegalStateException("speak() returned $status"))
    }

    override suspend fun stop() {
        withContext(ioDispatcher) {
            textToSpeech?.stop()
        }
    }

    override fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun applySettingsInternal(tts: TextToSpeech, settings: NarrationSettings): Result<Unit> {
        tts.setSpeechRate(settings.speechRate)
        settings.localeTag?.let { tag ->
            val result = tts.setLanguage(Locale.forLanguageTag(tag))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return Result.failure(IllegalStateException("Unsupported locale"))
            }
        }
        settings.voiceName?.let { voiceName ->
            val voice = tts.voices?.firstOrNull { it.name == voiceName }
            if (voice != null) {
                tts.voice = voice
            }
        }
        return Result.success(Unit)
    }

    private fun buildVoices(tts: TextToSpeech): List<NarrationVoice> {
        return tts.voices.orEmpty()
            .filterNot { it.isNetworkConnectionRequired }
            .sortedBy { it.locale.displayName }
            .map { voice ->
                NarrationVoice(
                    voiceName = voice.name,
                    displayName = "${voice.locale.displayName} (${voice.name})",
                    localeTag = voice.locale.toLanguageTag(),
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    isInstalled = !voice.isNetworkConnectionRequired
                )
            }
    }
}
