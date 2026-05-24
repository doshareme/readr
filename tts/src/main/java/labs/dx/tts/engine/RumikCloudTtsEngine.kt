package labs.dx.tts.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import labs.dx.tts.BuildConfig
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.ReaderError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

@Singleton
class RumikCloudTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {
    override val events: SharedFlow<TtsEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    private val mutableEvents = events as MutableSharedFlow<TtsEvent>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackMutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var currentSettings = NarrationSettings(useCloudTts = true)
    private var activeWebSocket: WebSocket? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeAudioTrackReleaser: (() -> Unit)? = null
    private var activeJob: Job? = null
    private var queuedSpeech: QueuedSpeech? = null
    private var activeStoryPlayback: StoryPlayback? = null

    override suspend fun initialize(): TtsEngineInitResult {
        return TtsEngineInitResult.Success(
            voices = listOf(
                NarrationVoice(
                    voiceName = CloudVoiceName,
                    displayName = "Rumik Cloud - Muga",
                    localeTag = "en",
                    requiresNetwork = true,
                    isInstalled = true
                )
            ),
            supportsRangeCallbacks = false
        )
    }

    override suspend fun applySettings(settings: NarrationSettings): Result<Unit> {
        currentSettings = settings
        return Result.success(Unit)
    }

    override suspend fun speak(request: TtsSpeakRequest): Result<Unit> {
        if (apiKey().isBlank()) {
            return Result.failure(IllegalStateException(MissingApiKeyMessage))
        }
        val queued = playbackMutex.withLock {
            if (activeJob?.isActive == true) {
                if (queuedSpeech == null) {
                    Log.d(Tag, "Queueing Rumik cloud TTS request ${request.utteranceId}")
                    queuedSpeech = QueuedSpeech(
                        request = request,
                        audio = scope.async { fetchSpeechAssets(request) }
                    )
                }
                true
            } else {
                false
            }
        }
        if (queued) return Result.success(Unit)

        stopActivePlayback(emitStopped = false)
        Log.d(Tag, "Starting Rumik cloud TTS request ${request.utteranceId}")
        activeJob = scope.launch {
            playQueuedSpeech(request)
        }
        return Result.success(Unit)
    }

    override suspend fun stop() {
        stopActivePlayback(emitStopped = true)
    }

    override fun shutdown() {
        scope.launch {
            stopActivePlayback(emitStopped = false)
        }
    }

    private suspend fun playQueuedSpeech(firstRequest: TtsSpeakRequest) {
        var nextSpeech: QueuedSpeech? = QueuedSpeech(firstRequest, null)
        try {
            while (currentCoroutineContext().isActive && nextSpeech != null) {
                val speech = nextSpeech
                val assets = speech.audio?.await() ?: fetchSpeechAssets(speech.request)
                playAudio(speech.request, assets.audio, assets.storySoundFile)
                mutableEvents.tryEmit(TtsEvent.Done(speech.request.utteranceId))
                nextSpeech = takeQueuedSpeech()
                if (nextSpeech == null) {
                    delay(QueuedSpeechGraceMs)
                    nextSpeech = takeQueuedSpeech()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(Tag, "Unable to stream Rumik speech", error)
            mutableEvents.tryEmit(
                TtsEvent.Error(
                    utteranceId = nextSpeech?.request?.utteranceId,
                    error = ReaderError.Unknown(error.toRumikMessage("Unable to stream Rumik speech"), error)
                )
            )
        } finally {
            playbackMutex.withLock {
                queuedSpeech?.audio?.cancel()
                queuedSpeech = null
                activeJob = null
                activeWebSocket?.cancel()
                activeWebSocket = null
                activeAudioTrackReleaser?.invoke() ?: activeAudioTrack?.let(::releaseAudioTrack)
                activeAudioTrackReleaser = null
                activeAudioTrack = null
                activeStoryPlayback?.stop()
                activeStoryPlayback = null
            }
        }
    }

    private suspend fun takeQueuedSpeech(): QueuedSpeech? = playbackMutex.withLock {
        queuedSpeech.also { queuedSpeech = null }
    }

    private suspend fun fetchSpeechAssets(request: TtsSpeakRequest): SpeechAssets {
        val audio = fetchSpeechAudio(request)
        val storySound = if (currentSettings.storyMode) {
            fetchStorySound(request.text)
        } else {
            null
        }
        return SpeechAssets(audio, storySound)
    }

    private suspend fun fetchSpeechAudio(request: TtsSpeakRequest): ByteArray {
        var lastRetryableError: RetryableRumikWebSocketException? = null
        repeat(MaxWebSocketAttempts) { attemptIndex ->
            try {
                return fetchSpeechAudioOnce(request, attemptIndex + 1)
            } catch (error: CancellationException) {
                throw error
            } catch (error: RetryableRumikWebSocketException) {
                lastRetryableError = error
                Log.w(
                    Tag,
                    "Rumik websocket attempt ${attemptIndex + 1}/$MaxWebSocketAttempts failed for ${request.utteranceId}; reconnecting",
                    error
                )
            }
        }
        throw lastRetryableError ?: IOException("Rumik websocket failed without retryable error")
    }

    private suspend fun fetchSpeechAudioOnce(request: TtsSpeakRequest, attempt: Int): ByteArray {
        val done = CompletableDeferred<Unit>()
        val receivedDone = AtomicBoolean(false)
        val audioBuffer = ByteArrayOutputStream()
        var streamWebSocket: WebSocket? = null
        try {
            val session = mintSession(request.text)
            Log.d(Tag, "Rumik websocket session minted for ${request.utteranceId} attempt=$attempt")
            val wsRequest = Request.Builder()
                .url(session.urlWithToken())
                .build()
            val webSocket = client.newWebSocket(
                wsRequest,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(Tag, "Rumik websocket opened")
                        if (!webSocket.send(synthesisPayload(request.text))) {
                            done.completeExceptionally(
                                RetryableRumikWebSocketException("Rumik websocket failed to send synthesis request")
                            )
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        synchronized(audioBuffer) {
                            audioBuffer.write(bytes.toByteArray())
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val json = runCatching { JSONObject(text) }.getOrNull()
                        val eventType = json?.optString("type")
                        when (eventType) {
                            "done" -> {
                                receivedDone.set(true)
                                done.complete(Unit)
                                webSocket.close(1000, null)
                            }
                            "error" -> {
                                val message = json.toWebSocketMessage(text)
                                Log.e(Tag, "Rumik websocket error frame: $message")
                                done.completeExceptionally(IOException("Rumik websocket error: $message"))
                                webSocket.close(1002, "Rumik error")
                            }
                            else -> {
                                Log.d(Tag, "Rumik websocket event: ${eventType ?: "text"}")
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val responseMessage = response?.message?.takeIf { it.isNotBlank() }
                        Log.e(Tag, "Rumik websocket failure", t)
                        done.completeExceptionally(
                            RetryableRumikWebSocketException(
                                message = if (responseMessage == null) {
                                    "Rumik websocket failed"
                                } else {
                                    "Rumik websocket failed: $responseMessage"
                                },
                                cause = t
                            )
                        )
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(Tag, "Rumik websocket closed: $code $reason")
                        if (!done.isCompleted) {
                            if (receivedDone.get()) {
                                done.complete(Unit)
                            } else {
                                done.completeExceptionally(
                                    RetryableRumikWebSocketException(
                                        "Rumik websocket closed before audio completed: $code $reason"
                                    )
                                )
                            }
                        }
                    }
                }
            )
            streamWebSocket = webSocket
            activeWebSocket = webSocket
            done.await()
            val audio = synchronized(audioBuffer) { audioBuffer.toByteArray() }
            Log.d(Tag, "Rumik audio fetch complete utterance=${request.utteranceId} attempt=$attempt bytes=${audio.size}")
            return audio
        } finally {
            if (activeWebSocket === streamWebSocket) {
                activeWebSocket = null
            }
        }
    }

    private suspend fun playAudio(request: TtsSpeakRequest, audio: ByteArray, storySoundFile: File?) {
        if (audio.isEmpty()) throw IOException("Rumik returned empty audio")
        val audioTrack = createAudioTrack()
        val released = AtomicBoolean(false)
        val releasePlaybackTrack = fun() {
            if (!released.compareAndSet(false, true)) return
            releaseAudioTrack(audioTrack)
            if (activeAudioTrack === audioTrack) activeAudioTrack = null
        }
        activeAudioTrack = audioTrack
        activeAudioTrackReleaser = releasePlaybackTrack
        val frames = audio.size / BytesPerFrame
        val durationMs = (frames.toLong() * 1_000L) / SampleRateHz
        val nextLeadMs = when {
            durationMs <= ShortAudioPrefetchMs -> 0L
            else -> (durationMs - NextRequestLeadMs).coerceAtLeast(0L)
        }
        val nextRequestJob = if (nextLeadMs == 0L) {
            mutableEvents.tryEmit(TtsEvent.NeedsNext(request.utteranceId))
            null
        } else {
            scope.launch {
                delay(nextLeadMs)
                mutableEvents.tryEmit(TtsEvent.NeedsNext(request.utteranceId))
            }
        }
        try {
            audioTrack.play()
            activeStoryPlayback = storySoundFile?.let(::startStorySound)
            Log.d(Tag, "Rumik audio playback started utterance=${request.utteranceId} durationMs=$durationMs")
            mutableEvents.tryEmit(TtsEvent.Started(request.utteranceId))
            var bytesWritten = audioTrack.writeBlocking(audio)
            if (bytesWritten == AudioTrack.ERROR_DEAD_OBJECT) {
                Log.w(Tag, "Rumik audio output died while playing ${request.utteranceId}; recreating AudioTrack")
                releasePlaybackTrack()
                playAudio(request, audio, storySoundFile)
                return
            }
            if (bytesWritten <= 0) {
                throw IOException("Rumik audio write failed: $bytesWritten")
            }
            waitForPlaybackToDrain(audioTrack, bytesWritten / BytesPerFrame)
            Log.d(Tag, "Rumik audio drain complete utterance=${request.utteranceId} delivered=${audioTrack.playbackHeadPosition}")
        } finally {
            nextRequestJob?.cancel()
            activeStoryPlayback?.stop()
            activeStoryPlayback = null
            releasePlaybackTrack()
            if (activeAudioTrackReleaser === releasePlaybackTrack) {
                activeAudioTrackReleaser = null
            }
        }
    }

    private suspend fun mintSession(text: String): RumikWsSession = withContext(Dispatchers.IO) {
        val body = synthesisPayload(text).toRequestBody(JsonMediaType)
        val request = Request.Builder()
            .url("$BaseUrl/v1/tts/ws-connect")
            .header("Authorization", "Bearer ${apiKey()}")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Rumik ws-connect failed: ${response.code} ${bodyText.toApiErrorMessage()}")
            }
            val json = JSONObject(bodyText)
            RumikWsSession(
                wsUrl = json.requiredString("ws_url", "wsUrl", "url"),
                wsToken = json.requiredString("ws_token", "token", "wsToken")
            )
        }
    }

    private suspend fun stopActivePlayback(emitStopped: Boolean) {
        playbackMutex.withLock {
            activeJob?.cancel()
            activeJob = null
            queuedSpeech?.audio?.cancel()
            queuedSpeech = null
            activeWebSocket?.cancel()
            activeWebSocket = null
            activeAudioTrackReleaser?.invoke() ?: activeAudioTrack?.let(::releaseAudioTrack)
            activeAudioTrackReleaser = null
            activeAudioTrack = null
            activeStoryPlayback?.stop()
            activeStoryPlayback = null
            if (emitStopped) mutableEvents.tryEmit(TtsEvent.Stopped)
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            SampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SampleRateHz / 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBufferBytes * 2)
            .build()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setVolume(AudioTrack.getMaxVolume())
                }
                Log.d(Tag, "Created AudioTrack state=$state sampleRate=$sampleRate bufferSize=$bufferSizeInFrames")
            }
    }

    private fun AudioTrack.writeBlocking(audio: ByteArray): Int {
        var totalWritten = 0
        while (totalWritten < audio.size) {
            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                write(audio, totalWritten, audio.size - totalWritten, AudioTrack.WRITE_BLOCKING)
            } else {
                write(audio, totalWritten, audio.size - totalWritten)
            }
            if (written <= 0) return if (totalWritten > 0) totalWritten else written
            totalWritten += written
        }
        return totalWritten
    }

    private fun releaseAudioTrack(audioTrack: AudioTrack) {
        val playState = runCatching { audioTrack.playState }.getOrNull()
        if (playState == AudioTrack.PLAYSTATE_PLAYING || playState == AudioTrack.PLAYSTATE_PAUSED) {
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
        }
        runCatching { audioTrack.release() }
    }

    private suspend fun fetchStorySound(text: String): File? = withContext(Dispatchers.IO) {
        val keyword = storyKeyword(text) ?: return@withContext null
        val token = BuildConfig.FREESOUND_API_TOKEN.takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching {
            val searchUrl = "https://freesound.org/apiv2/search/text/".toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", keyword)
                .addQueryParameter("token", token)
                .build()
            val searchRequest = Request.Builder().url(searchUrl).get().build()
            val soundId = client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Freesound search failed: ${response.code}")
                }
                JSONObject(response.body?.string().orEmpty())
                    .optJSONArray("results")
                    ?.optJSONObject(0)
                    ?.optLong("id")
                    ?.takeIf { it > 0L }
                    ?: throw IOException("Freesound search returned no result for $keyword")
            }
            val targetFile = File(storySoundCacheDir(), "$soundId.mp3")
            if (targetFile.exists() && targetFile.length() > 0L) {
                return@runCatching targetFile
            }
            val detailUrl = "https://freesound.org/apiv2/sounds/$soundId/".toHttpUrl()
                .newBuilder()
                .addQueryParameter("token", token)
                .build()
            val detailRequest = Request.Builder().url(detailUrl).get().build()
            val previewUrl = client.newCall(detailRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Freesound sound detail failed: ${response.code}")
                }
                JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("previews")
                    ?.optString("preview-hq-mp3")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Freesound sound $soundId has no hq mp3 preview")
            }
            val previewRequest = Request.Builder().url(previewUrl).get().build()
            client.newCall(previewRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Freesound preview download failed: ${response.code}")
                }
                targetFile.outputStream().use { output ->
                    response.body?.byteStream()?.copyTo(output)
                        ?: throw IOException("Freesound preview response was empty")
                }
            }
            targetFile
        }.onFailure { error ->
            Log.w(Tag, "Unable to fetch story sound for keyword=$keyword", error)
        }.getOrNull()
    }

    private fun startStorySound(file: File): StoryPlayback? {
        return runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(StoryModeVolume, StoryModeVolume)
                isLooping = false
                prepare()
                start()
            }
            val fadeJob = scope.launch {
                delay(StoryModeDurationMs - StoryModeFadeOutMs)
                repeat(StoryModeFadeSteps) { step ->
                    val volume = StoryModeVolume * (StoryModeFadeSteps - step - 1) / StoryModeFadeSteps.toFloat()
                    runCatching { player.setVolume(volume, volume) }
                    delay(StoryModeFadeOutMs / StoryModeFadeSteps)
                }
                runCatching { player.stop() }
                runCatching { player.release() }
            }
            StoryPlayback(player, fadeJob)
        }.onFailure { error ->
            Log.w(Tag, "Unable to play story sound ${file.absolutePath}", error)
        }.getOrNull()
    }

    private fun storyKeyword(text: String): String? {
        val normalized = text.lowercase()
        return StoryModeKeywords.firstOrNull { keyword ->
            Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(normalized)
        }
    }

    private fun storySoundCacheDir(): File {
        return File(context.cacheDir, "freesound-story").apply { mkdirs() }
    }

    private suspend fun waitForPlaybackToDrain(audioTrack: AudioTrack, targetFrames: Int) {
        if (targetFrames <= 0) return
        val estimatedMs = (targetFrames.toLong() * 1_000L) / SampleRateHz
        val deadline = System.currentTimeMillis() + estimatedMs + 2_000L
        while (
            audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
            audioTrack.playbackHeadPosition < targetFrames &&
            System.currentTimeMillis() < deadline
        ) {
            delay(40)
        }
    }

    private fun apiKey(): String = BuildConfig.RUMIK_API_KEY

    private fun Throwable.toRumikMessage(prefix: String): String {
        val detail = message?.takeIf { it.isNotBlank() }
        return if (detail == null) prefix else "$prefix: $detail"
    }

    private fun String.toApiErrorMessage(): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return "empty response"
        runCatching { JSONObject(trimmed) }.getOrNull()?.let { json ->
            return json.optString("message")
                .takeIf { it.isNotBlank() }
                ?: json.optString("error")
                    .takeIf { it.isNotBlank() }
                ?: json.toString()
        }
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return "HTML error page returned by API host"
        }
        return trimmed.take(MaxErrorBodyChars)
    }

    private fun JSONObject.requiredString(vararg names: String): String {
        names.forEach { name ->
            optString(name)
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        throw IOException("Rumik ws-connect response missing ${names.joinToString(" or ")}")
    }

    private fun synthesisPayload(text: String): String {
        return JSONObject()
            .put("model", ModelName)
            .put("text", text)
            .put("description", currentSettings.cloudVoice.description)
            .toString()
    }

    private fun JSONObject?.toWebSocketMessage(fallback: String): String {
        if (this == null) return fallback
        return optString("message")
            .takeIf { it.isNotBlank() }
            ?: optString("error")
                .takeIf { it.isNotBlank() }
            ?: fallback
    }

    private data class RumikWsSession(
        val wsUrl: String,
        val wsToken: String
    ) {
        fun urlWithToken(): String {
            val separator = if (wsUrl.contains("?")) "&" else "?"
            return "$wsUrl${separator}token=$wsToken"
        }
    }

    private data class QueuedSpeech(
        val request: TtsSpeakRequest,
        val audio: Deferred<SpeechAssets>?
    )

    private data class SpeechAssets(
        val audio: ByteArray,
        val storySoundFile: File?
    )

    private data class StoryPlayback(
        val player: MediaPlayer,
        val fadeJob: Job
    ) {
        fun stop() {
            fadeJob.cancel()
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    private class RetryableRumikWebSocketException(
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)

    companion object {
        private const val Tag = "RumikCloudTts"
        const val CloudVoiceName = "rumik-muga-cloud"
        private const val BaseUrl = "https://silk-api.rumik.ai"
        private const val ModelName = "mulberry"
        private const val MissingApiKeyMessage =
            "Rumik API key missing. Add RUMIK_API_KEY or SILK_API_KEY to local.properties or .env, then rebuild the app."
        private const val SampleRateHz = 24_000
        private const val BytesPerFrame = 2
        private const val NextRequestLeadMs = 3_000L
        private const val ShortAudioPrefetchMs = 1_500L
        private const val QueuedSpeechGraceMs = 75L
        private const val MaxWebSocketAttempts = 3
        private const val MaxErrorBodyChars = 280
        private const val StoryModeDurationMs = 10_000L
        private const val StoryModeFadeOutMs = 2_000L
        private const val StoryModeFadeSteps = 20
        private const val StoryModeVolume = 0.6f
        private val PersonalItemKeywords = listOf(
            "diary",
            "bottle",
            "water",
            "packet",
            "chewing gum",
            "tissue",
            "glasses",
            "watch",
            "sweet",
            "photo",
            "camera",
            "stamp",
            "postcard",
            "dictionary",
            "coin",
            "brush",
            "credit card",
            "identity card",
            "key",
            "mobile phone",
            "phone card",
            "wallet",
            "button",
            "umbrella",
            "pen",
            "pencil",
            "lighter",
            "cigarette",
            "match",
            "lipstick",
            "purse",
            "case",
            "clip",
            "scissors",
            "rubber",
            "file",
            "banknote",
            "passport",
            "driving licence",
            "comb",
            "notebook",
            "laptop",
            "rubbish",
            "mirror",
            "painkiller",
            "sunscreen",
            "toothbrush",
            "headphone",
            "player",
            "battery",
            "light bulb",
            "bin",
            "newspaper",
            "magazine",
            "alarm clock"
        )

        private val ObjectKeywords = listOf(
            "air conditioners",
            "air purifiers",
            "amulets",
            "anklets",
            "armor",
            "arrowheads",
            "ashtrays",
            "automaton",
            "baby carriages",
            "backpacks",
            "balloons",
            "balls",
            "bamboo sticks",
            "bangles",
            "bar stools",
            "bars",
            "baseball bats",
            "cricket bats",
            "baseball caps",
            "baskets",
            "bath mats",
            "ceramics",
            "chainsaws",
            "chairs",
            "chalkboards",
            "champagne glasses",
            "chandeliers",
            "charm bracelets",
            "chess sets",
            "chest of drawers",
            "dressers",
            "forklifts",
            "fossils",
            "fridge magnets",
            "furniture",
            "futons",
            "games",
            "cards",
            "gaming consoles",
            "garden shears",
            "gems",
            "geodes",
            "glasses",
            "mugs",
            "sunglasses",
            "jet aircraft",
            "journals",
            "planners",
            "jump rope",
            "kettles",
            "key chains",
            "keyboards"
        )

        private val LocationKeywords = listOf(
            "geographical location",
            "gps coordinates",
            "google maps",
            "mile radius",
            "immediate vicinity",
            "centrally located",
            "close proximity",
            "conveniently located",
            "mountain pass",
            "mailing address",
            "outer space",
            "mathematical space",
            "bird's eye view",
            "shopping center",
            "shopping plaza",
            "coffee shop",
            "watering hole",
            "observation tower",
            "pole position",
            "lotus position",
            "fetal position",
            "indoor space",
            "radio fix",
            "space simulator",
            "phase space",
            "vector space",
            "region",
            "space",
            "site",
            "venue",
            "place",
            "proximity",
            "area",
            "where",
            "position",
            "locator",
            "country",
            "locale",
            "vicinity",
            "destination",
            "locality",
            "home",
            "station",
            "point",
            "orientation",
            "locate",
            "whereabouts",
            "geographic",
            "facility",
            "places",
            "time",
            "date",
            "location",
            "geographical",
            "base",
            "east",
            "maps",
            "state",
            "tracking",
            "city",
            "locations",
            "address",
            "gps",
            "source",
            "planet",
            "warehouse",
            "plaza",
            "north",
            "store",
            "work",
            "object",
            "distance",
            "room",
            "building",
            "environment",
            "event",
            "land",
            "weather",
            "market",
            "town",
            "earth",
            "server",
            "neighborhood",
            "west",
            "northwest",
            "south",
            "zone",
            "scene",
            "local",
            "northeast",
            "southeast",
            "phone",
            "geolocation",
            "business",
            "item",
            "timezone",
            "background",
            "unit",
            "context",
            "folder",
            "movement",
            "card",
            "campsite",
            "travel",
            "campus",
            "view",
            "route",
            "path",
            "property",
            "spot",
            "story",
            "coordinates",
            "world",
            "hotel",
            "season",
            "history",
            "restaurant",
            "branch",
            "visitor",
            "outlet",
            "attraction",
            "surrounding",
            "terminal",
            "landmark",
            "surroundings",
            "heliport",
            "residences",
            "eatery",
            "environs",
            "gateway",
            "radius",
            "kiosk",
            "landmarks",
            "ambience",
            "microclimate",
            "footprint",
            "precinct",
            "zip code",
            "headquarters",
            "jungle",
            "setting",
            "school",
            "office",
            "park space"
        )

        private val AnimalKeywords = listOf(
            "african buffalo",
            "african elephant",
            "african leopard",
            "american buffalo",
            "american robin",
            "arabian leopard",
            "arctic fox",
            "arctic wolf",
            "bald eagle",
            "beaked whale",
            "black panther",
            "black widow spider",
            "blue bird",
            "blue jay",
            "blue whale",
            "box jellyfish",
            "cape buffalo",
            "catshark",
            "crane fly",
            "great blue heron",
            "great white shark",
            "grizzly bear",
            "hammerhead shark",
            "hermit crab",
            "humpback whale",
            "irukandji jellyfish",
            "kangaroo mouse",
            "kangaroo rat",
            "komodo dragon",
            "manta ray",
            "monitor lizard",
            "mountain goat",
            "new world quail",
            "old world quail",
            "peregrine falcon",
            "pilot whale",
            "polar bear",
            "portuguese man o' war",
            "prairie dog",
            "praying mantis",
            "rainbow trout",
            "red panda",
            "right whale",
            "saber-toothed cat",
            "sea lion",
            "sea slug",
            "sea snail",
            "snow leopard",
            "sockeye salmon",
            "sperm whale",
            "spider monkey",
            "star-nosed mole",
            "steelhead trout",
            "sugar glider",
            "tasmanian devil",
            "tiger shark",
            "trapdoor spider",
            "tree frog",
            "vampire bat",
            "vampire squid",
            "water boa",
            "water buffalo",
            "whooping crane",
            "x-ray fish",
            "zebra finch",
            "domestic bactrian camel",
            "domestic canary",
            "domestic dromedary camel",
            "domestic duck",
            "domestic goat",
            "domestic goose",
            "domestic guineafowl",
            "domestic hedgehog",
            "domestic pig",
            "domestic pigeon",
            "domestic rabbit",
            "domestic silkmoth",
            "domestic silver fox",
            "domestic turkey",
            "fancy mouse",
            "fancy rat",
            "lab rat",
            "ringneck dove",
            "siamese fighting fish",
            "society finch",
            "canidae",
            "felidae",
            "cat",
            "cattle",
            "dog",
            "donkey",
            "goat",
            "horse",
            "pig",
            "rabbit",
            "aardvark",
            "aardwolf",
            "albatross",
            "alligator",
            "alpaca",
            "amphibian",
            "anaconda",
            "angelfish",
            "anglerfish",
            "ant",
            "anteater",
            "antelope",
            "antlion",
            "ape",
            "aphid",
            "armadillo",
            "baboon",
            "badger",
            "bandicoot",
            "barnacle",
            "barracuda",
            "basilisk",
            "bass",
            "bat",
            "bear",
            "beaver",
            "bedbug",
            "bee",
            "beetle",
            "bird",
            "bison",
            "blackbird",
            "boa",
            "boar",
            "bobcat",
            "bobolink",
            "bonobo",
            "buffalo",
            "butterfly",
            "buzzard",
            "camel",
            "canid",
            "capybara",
            "cardinal",
            "caribou",
            "carp",
            "caterpillar",
            "catfish",
            "centipede",
            "chameleon",
            "cheetah",
            "chickadee",
            "chicken",
            "chimpanzee",
            "chinchilla",
            "chipmunk",
            "clam",
            "clownfish",
            "cobra",
            "cockroach",
            "cod",
            "condor",
            "coral",
            "cougar",
            "cow",
            "coyote",
            "crab",
            "crane",
            "crayfish",
            "cricket",
            "crocodile",
            "crow",
            "cuckoo",
            "cicada",
            "deer",
            "dingo",
            "dinosaur",
            "dolphin",
            "dove",
            "dragonfly",
            "dragon",
            "duck",
            "eagle",
            "earthworm",
            "eel",
            "egret",
            "elephant",
            "elk",
            "emu",
            "falcon",
            "ferret",
            "finch",
            "firefly",
            "fish",
            "flamingo",
            "flea",
            "fly",
            "fox",
            "frog",
            "gazelle",
            "gecko",
            "gerbil",
            "giraffe",
            "goldfish",
            "goose",
            "gopher",
            "gorilla",
            "grasshopper",
            "guppy",
            "haddock",
            "halibut",
            "hamster",
            "hare",
            "hawk",
            "hedgehog",
            "heron",
            "herring",
            "hippopotamus",
            "hornet",
            "hummingbird",
            "hyena",
            "iguana",
            "impala",
            "jackal",
            "jaguar",
            "jay",
            "jellyfish",
            "kangaroo",
            "kingfisher",
            "kite",
            "kiwi",
            "koala",
            "koi",
            "krill",
            "ladybug",
            "leech",
            "lemming",
            "lemur",
            "leopard",
            "lion",
            "lizard",
            "llama",
            "lobster",
            "locust",
            "loon",
            "lynx",
            "macaw",
            "mackerel",
            "magpie",
            "mammal",
            "manatee",
            "mandrill",
            "marlin",
            "marmoset",
            "marmot",
            "marten",
            "meerkat",
            "mink",
            "minnow",
            "mite",
            "mockingbird",
            "mole",
            "mongoose",
            "monkey",
            "moose",
            "mosquito",
            "moth",
            "mouse",
            "mule",
            "narwhal",
            "newt",
            "nightingale",
            "ocelot",
            "octopus",
            "opossum",
            "orangutan",
            "orca",
            "ostrich",
            "otter",
            "owl",
            "ox",
            "panda",
            "panther",
            "parakeet",
            "parrot",
            "parrotfish",
            "partridge",
            "peacock",
            "pelican",
            "penguin",
            "perch",
            "pheasant",
            "pigeon",
            "pike",
            "piranha",
            "platypus",
            "pony",
            "porcupine",
            "porpoise",
            "possum",
            "prawn",
            "primate",
            "puffin",
            "puma",
            "python",
            "quail",
            "rabbit",
            "raccoon",
            "rat",
            "rattlesnake",
            "raven",
            "reindeer",
            "rhinoceros",
            "rooster",
            "salamander",
            "salmon",
            "scorpion",
            "seahorse",
            "shark",
            "sheep",
            "shrimp",
            "skunk",
            "sloth",
            "snail",
            "snake",
            "sparrow",
            "spider",
            "squid",
            "squirrel",
            "starfish",
            "stork",
            "swan",
            "swift",
            "tapir",
            "tarantula",
            "termite",
            "tick",
            "tiger",
            "toad",
            "tortoise",
            "toucan",
            "trout",
            "tuna",
            "turkey",
            "turtle",
            "viper",
            "vole",
            "vulture",
            "wallaby",
            "walrus",
            "wasp",
            "weasel",
            "whale",
            "wolf",
            "wombat",
            "woodpecker",
            "worm",
            "wren",
            "yak",
            "zebra"
        )

        private val StoryModeKeywords = listOf(
            PersonalItemKeywords,
            ObjectKeywords,
            LocationKeywords,
            AnimalKeywords
        )
            .flatten()
            .map { it.lowercase() }
            .distinct()
            .sortedWith(compareByDescending<String> { it.count { char -> char == ' ' } }.thenByDescending { it.length })
        private val JsonMediaType = "application/json".toMediaType()
    }
}
