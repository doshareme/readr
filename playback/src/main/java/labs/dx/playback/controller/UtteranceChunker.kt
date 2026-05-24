package labs.dx.playback.controller

import labs.dx.core.domain.model.PdfWord

internal data class UtteranceChunk(
    val utteranceId: String,
    val text: String,
    val words: List<PdfWord>,
    val offsets: List<IntRange>
)

internal class UtteranceChunker(
    private val maxChars: Int = 600,
    private val maxWords: Int = 150
) {

    suspend fun buildChunk(
        startWordIndex: Int,
        wordProvider: suspend (Int) -> PdfWord?
    ): UtteranceChunk? {
        val words = mutableListOf<PdfWord>()
        var totalChars = 0
        var currentIndex = startWordIndex
        var targetParagraphIndex: Int? = null

        while (true) {
            val word = wordProvider(currentIndex) ?: break
            if (targetParagraphIndex == null) {
                targetParagraphIndex = word.paragraphIndex
            }
            val candidateLength = if (words.isEmpty()) word.text.length else totalChars + 1 + word.text.length
            if (words.isNotEmpty() && word.paragraphIndex != targetParagraphIndex) {
                break
            }
            if (words.size >= maxWords) break
            if (words.isNotEmpty() && candidateLength > maxChars) break
            words += word
            totalChars = candidateLength
            currentIndex += 1
        }

        if (words.isEmpty()) return null

        val builder = StringBuilder()
        val offsets = mutableListOf<IntRange>()
        words.forEachIndexed { index, word ->
            if (index > 0) builder.append(' ')
            val start = builder.length
            builder.append(word.text)
            offsets += start until builder.length
        }

        return UtteranceChunk(
            utteranceId = "utt-${words.first().globalWordIndex}",
            text = builder.toString(),
            words = words,
            offsets = offsets
        )
    }
}
