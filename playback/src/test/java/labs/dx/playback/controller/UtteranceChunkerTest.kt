package labs.dx.playback.controller

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError
import labs.dx.core.domain.repository.PdfDocumentSession
import labs.dx.tts.engine.TtsEngine
import labs.dx.tts.engine.TtsEngineInitResult
import labs.dx.tts.engine.TtsEvent
import labs.dx.tts.engine.TtsSpeakRequest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UtteranceChunkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun buildChunk_keepsSentenceBoundaries_whenPossible() = runTest {
        val chunker = UtteranceChunker(maxChars = 24)
        val words = listOf(
            fakeWord("Hello", 0, 0),
            fakeWord("world.", 1, 0),
            fakeWord("Next", 2, 1),
            fakeWord("sentence.", 3, 1)
        )

        val chunk = chunker.buildChunk(0) { index -> words.getOrNull(index) }

        assertThat(chunk).isNotNull()
        assertThat(chunk!!.text).isEqualTo("Hello world. Next")
        assertThat(chunk.words.map { it.globalWordIndex }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun playbackController_updatesCurrentWord_fromRangeCallbacks() = runTest {
        val engine = FakeTtsEngine()
        val controller = NarrationPlaybackController(engine)
        val words = listOf(
            fakeWord("Hello", 0, 0),
            fakeWord("world.", 1, 0),
            fakeWord("Again", 2, 1)
        )
        val session = FakeSession(words)

        controller.initialize()
        controller.prepare(session)
        controller.play(0)
        advanceUntilIdle()

        val request = engine.lastRequest!!
        engine.emit(TtsEvent.Started(request.utteranceId))
        engine.emit(TtsEvent.Range(request.utteranceId, 6, 11))
        advanceUntilIdle()

        assertThat(controller.playbackState.value).isEqualTo(PlaybackState.Playing(1))
        assertThat(controller.currentHighlight.value?.word?.text).isEqualTo("world.")
    }

    private fun fakeWord(
        text: String,
        index: Int,
        sentenceIndex: Int
    ): PdfWord {
        return PdfWord(
            text = text,
            pageIndex = 0,
            boundingBox = RectF(0f, 0f, 10f, 10f),
            normalizedText = text.lowercase(),
            globalWordIndex = index,
            sentenceIndex = sentenceIndex,
            paragraphIndex = 0
        )
    }
}

private class FakeTtsEngine : TtsEngine {
    private val mutableEvents = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<TtsEvent> = mutableEvents
    var lastRequest: TtsSpeakRequest? = null

    override suspend fun initialize(): TtsEngineInitResult {
        return TtsEngineInitResult.Success(
            voices = listOf(
                NarrationVoice(
                    voiceName = "local",
                    displayName = "Local",
                    localeTag = "en-US",
                    requiresNetwork = false,
                    isInstalled = true
                )
            ),
            supportsRangeCallbacks = true
        )
    }

    override suspend fun applySettings(settings: NarrationSettings): Result<Unit> = Result.success(Unit)

    override suspend fun speak(request: TtsSpeakRequest): Result<Unit> {
        lastRequest = request
        return Result.success(Unit)
    }

    override suspend fun stop() = Unit

    override fun shutdown() = Unit

    suspend fun emit(event: TtsEvent) {
        mutableEvents.emit(event)
    }
}

private class FakeSession(
    private val words: List<PdfWord>
) : PdfDocumentSession {
    override val documentInfo: PdfDocumentInfo = PdfDocumentInfo(
        document = DocumentDescriptor("doc", "uri", "Sample.pdf", "application/pdf", 10L),
        pageCount = 1,
        isEncrypted = false,
        hasExtractableText = true
    )

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo = PdfPageInfo(pageIndex, 100, 100, 0)

    override suspend fun getPageLayoutDebugInfo(pageIndex: Int) = null

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        error("Not needed in this test")
    }

    override suspend fun setResearchPaperMode(enabled: Boolean) = Unit

    override suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean, resetQueue: Boolean) = Unit

    override suspend fun prefetchPageAnalysis(anchorPageIndex: Int) = Unit

    override suspend fun getWordsForPage(pageIndex: Int): List<PdfWord> = words

    override suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord? {
        return words.firstOrNull { it.globalWordIndex == globalWordIndex }
    }

    override suspend fun getTotalWordCount(): Int = words.size

    override suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord? = words.firstOrNull()

    override fun close() = Unit
}
