package labs.dx.tts.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import labs.dx.tts.BuildConfig
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.ReaderError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

@Singleton
class RumikCloudTtsEngine @Inject constructor() : TtsEngine {
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
        stopActivePlayback(emitStopped = false)
        Log.d(Tag, "Starting Rumik cloud TTS request")
        activeJob = scope.launch {
            streamSpeech(request)
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

    private suspend fun streamSpeech(request: TtsSpeakRequest) {
        val audioTrackRef = AtomicReference<AudioTrack?>(null)
        val audioTrackReleased = AtomicBoolean(false)
        val done = CompletableDeferred<Unit>()
        val receivedDone = AtomicBoolean(false)
        val framesWritten = AtomicInteger(0)
        val bytesWrittenTotal = AtomicInteger(0)
        var streamWebSocket: WebSocket? = null
        var started = false
        val releasePlaybackTrack = fun() {
            if (!audioTrackReleased.compareAndSet(false, true)) return
            val releasedTrack = audioTrackRef.getAndSet(null)
            releasedTrack?.let(::releaseAudioTrack)
            if (activeAudioTrack === releasedTrack) activeAudioTrack = null
        }
        fun resetPlaybackTrack() {
            val releasedTrack = audioTrackRef.getAndSet(null)
            releasedTrack?.let(::releaseAudioTrack)
            if (activeAudioTrack === releasedTrack) activeAudioTrack = null
        }
        fun currentPlaybackTrack(): AudioTrack {
            if (audioTrackReleased.get()) {
                throw CancellationException("Rumik audio playback has stopped")
            }
            val existingTrack = audioTrackRef.get()
            if (existingTrack != null && existingTrack.state == AudioTrack.STATE_INITIALIZED) {
                return existingTrack
            }
            existingTrack?.let(::releaseAudioTrack)
            if (activeAudioTrack === existingTrack) activeAudioTrack = null
            val newTrack = createAudioTrack()
            audioTrackRef.set(newTrack)
            activeAudioTrack = newTrack
            activeAudioTrackReleaser = releasePlaybackTrack
            return newTrack
        }
        activeAudioTrackReleaser = releasePlaybackTrack
        try {
            val session = mintSession(request.text)
            Log.d(Tag, "Rumik websocket session minted")
            val wsRequest = Request.Builder()
                .url(session.urlWithToken())
                .build()
            val webSocket = client.newWebSocket(
                wsRequest,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(Tag, "Rumik websocket opened")
                        if (!webSocket.send(synthesisPayload(request.text))) {
                            done.completeExceptionally(IOException("Rumik websocket failed to send synthesis request"))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val audioTrack = runCatching { currentPlaybackTrack() }
                            .getOrElse { error ->
                                done.completeExceptionally(error)
                                webSocket.close(1011, "Audio output unavailable")
                                return
                            }
                        if (!started) {
                            started = true
                            Log.d(Tag, "Rumik websocket received first audio bytes")
                            runCatching { audioTrack.play() }
                                .onFailure { error ->
                                    done.completeExceptionally(error)
                                    webSocket.close(1011, "Audio playback failed")
                                    return
                                }
                            Log.d(Tag, "Rumik audio playback started")
                            mutableEvents.tryEmit(TtsEvent.Started(request.utteranceId))
                        }
                        val audio = bytes.toByteArray()
                        var bytesWritten = audioTrack.writeBlocking(audio)
                        if (bytesWritten == AudioTrack.ERROR_DEAD_OBJECT) {
                            Log.w(Tag, "Rumik audio output died; recreating AudioTrack")
                            resetPlaybackTrack()
                            val retryTrack = runCatching { currentPlaybackTrack() }
                                .getOrElse { error ->
                                    done.completeExceptionally(error)
                                    webSocket.close(1011, "Audio output unavailable")
                                    return
                                }
                            runCatching { retryTrack.play() }
                                .onFailure { error ->
                                    done.completeExceptionally(error)
                                    webSocket.close(1011, "Audio playback failed")
                                    return
                                }
                            bytesWritten = retryTrack.writeBlocking(audio)
                        }
                        if (bytesWritten > 0) {
                            framesWritten.addAndGet(bytesWritten / BytesPerFrame)
                            bytesWrittenTotal.addAndGet(bytesWritten)
                        } else {
                            Log.w(Tag, "Rumik audio write returned $bytesWritten for ${audio.size} bytes")
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
                            if (responseMessage == null) t else IOException("Rumik websocket failed: $responseMessage", t)
                        )
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(Tag, "Rumik websocket closed: $code $reason")
                        if (!done.isCompleted) {
                            if (receivedDone.get()) {
                                done.complete(Unit)
                            } else {
                                done.completeExceptionally(
                                    IOException("Rumik websocket closed before audio completed: $code $reason")
                                )
                            }
                        }
                    }
                }
            )
            streamWebSocket = webSocket
            activeWebSocket = webSocket
            done.await()
            if (started) {
                val audioTrack = audioTrackRef.get()
                if (audioTrack == null) {
                    Log.w(Tag, "Rumik audio stream completed after AudioTrack was released")
                } else {
                    Log.d(
                        Tag,
                        "Rumik audio stream complete bytes=${bytesWrittenTotal.get()} frames=${framesWritten.get()} delivered=${audioTrack.playbackHeadPosition}"
                    )
                    waitForPlaybackToDrain(audioTrack, framesWritten.get())
                    Log.d(Tag, "Rumik audio drain complete delivered=${audioTrack.playbackHeadPosition}")
                }
            }
            mutableEvents.tryEmit(TtsEvent.Done(request.utteranceId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(Tag, "Unable to stream Rumik speech", error)
            mutableEvents.tryEmit(
                TtsEvent.Error(
                    utteranceId = request.utteranceId,
                    error = ReaderError.Unknown(error.toRumikMessage("Unable to stream Rumik speech"), error)
                )
            )
        } finally {
            releasePlaybackTrack()
            if (activeAudioTrackReleaser === releasePlaybackTrack) {
                activeAudioTrackReleaser = null
            }
            if (activeWebSocket === streamWebSocket) {
                activeWebSocket = null
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
            activeWebSocket?.cancel()
            activeWebSocket = null
            activeAudioTrackReleaser?.invoke() ?: activeAudioTrack?.let(::releaseAudioTrack)
            activeAudioTrackReleaser = null
            activeAudioTrack = null
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
            .put("description", VoiceDescription)
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

    companion object {
        private const val Tag = "RumikCloudTts"
        const val CloudVoiceName = "rumik-muga-cloud"
        private const val BaseUrl = "https://silk-api.rumik.ai"
        private const val ModelName = "muga"
        private const val MissingApiKeyMessage =
            "Rumik API key missing. Add RUMIK_API_KEY or SILK_API_KEY to local.properties or .env, then rebuild the app."
        private const val SampleRateHz = 24_000
        private const val BytesPerFrame = 2
        private const val MaxErrorBodyChars = 280
        private const val VoiceDescription =
            "a male voice in 30s with middle eastern accent and casual speaker with med intensity at conversational pace"
        private val JsonMediaType = "application/json".toMediaType()
    }
}
