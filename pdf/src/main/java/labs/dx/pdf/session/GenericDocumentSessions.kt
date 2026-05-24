package labs.dx.pdf.session

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.Html
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.repository.PdfDocumentSession
import org.w3c.dom.Element

internal class TextDocumentSession(
    private val documentDescriptor: DocumentDescriptor,
    text: String
) : PdfDocumentSession {
    private val pages: List<TextPage> = TextPaginator.paginate(text)

    override val documentInfo: PdfDocumentInfo = PdfDocumentInfo(
        document = documentDescriptor,
        pageCount = pages.size.coerceAtLeast(1),
        isEncrypted = false,
        hasExtractableText = pages.any { it.words.isNotEmpty() }
    )

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo {
        return PdfPageInfo(pageIndex, TextPaginator.PageWidth, TextPaginator.PageHeight)
    }

    override suspend fun getPageLayoutDebugInfo(pageIndex: Int) = null

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val page = pages.getOrNull(pageIndex) ?: TextPage(emptyList())
        val safeTargetWidth = targetWidthPx.coerceIn(480, 1280)
        val scale = safeTargetWidth.toFloat() / TextPaginator.PageWidth.toFloat()
        val bitmap = Bitmap.createBitmap(
            safeTargetWidth,
            (TextPaginator.PageHeight * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        TextPaginator.drawPage(canvas, page)
        return bitmap
    }

    override suspend fun setResearchPaperMode(enabled: Boolean) = Unit
    override suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean, resetQueue: Boolean) = Unit
    override suspend fun prefetchPageAnalysis(anchorPageIndex: Int) = Unit

    override suspend fun getWordsForPage(pageIndex: Int): List<PdfWord> {
        return pages.getOrNull(pageIndex)?.words.orEmpty()
    }

    override suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord? {
        return pages.asSequence().flatMap { it.words.asSequence() }
            .firstOrNull { it.globalWordIndex == globalWordIndex }
    }

    override suspend fun getTotalWordCount(): Int {
        return pages.sumOf { it.words.size }
    }

    override suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord? {
        return getWordsForPage(pageIndex).firstOrNull { it.boundingBox.contains(pdfX, pdfY) }
    }

    override fun close() = Unit
}

internal class ComicArchiveDocumentSession(
    private val resolver: ContentResolver,
    private val documentDescriptor: DocumentDescriptor,
    private val uri: Uri,
    private val archiveType: ComicArchiveType
) : PdfDocumentSession {
    private val pageNames: List<String> = resolver.openInputStream(uri)?.use { input ->
        archiveType.listImageEntries(input)
    }.orEmpty()

    override val documentInfo: PdfDocumentInfo = PdfDocumentInfo(
        document = documentDescriptor,
        pageCount = pageNames.size.coerceAtLeast(1),
        isEncrypted = false,
        hasExtractableText = false
    )

    override suspend fun getPageInfo(pageIndex: Int): PdfPageInfo {
        val bytes = pageBytes(pageIndex) ?: return PdfPageInfo(pageIndex, 612, 792)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return PdfPageInfo(
            pageIndex = pageIndex,
            widthPoints = options.outWidth.takeIf { it > 0 } ?: 612,
            heightPoints = options.outHeight.takeIf { it > 0 } ?: 792
        )
    }

    override suspend fun getPageLayoutDebugInfo(pageIndex: Int) = null

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val bytes = pageBytes(pageIndex)
        val source = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?: return placeholderBitmap(targetWidthPx, "No image")
        val safeTargetWidth = targetWidthPx.coerceIn(360, 1600)
        val targetHeight = (source.height * (safeTargetWidth.toFloat() / source.width.toFloat()))
            .toInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, safeTargetWidth, targetHeight, true).also {
            if (it != source) source.recycle()
        }
    }

    override suspend fun setResearchPaperMode(enabled: Boolean) = Unit
    override suspend fun requestPageAnalysis(pageIndex: Int, urgent: Boolean, resetQueue: Boolean) = Unit
    override suspend fun prefetchPageAnalysis(anchorPageIndex: Int) = Unit
    override suspend fun getWordsForPage(pageIndex: Int): List<PdfWord> = emptyList()
    override suspend fun getWordByGlobalIndex(globalWordIndex: Int): PdfWord? = null
    override suspend fun getTotalWordCount(): Int = 0
    override suspend fun findWordAt(pageIndex: Int, pdfX: Float, pdfY: Float): PdfWord? = null
    override fun close() = Unit

    private fun pageBytes(pageIndex: Int): ByteArray? {
        val name = pageNames.getOrNull(pageIndex) ?: return null
        return resolver.openInputStream(uri)?.use { input ->
            archiveType.readEntry(input, name)
        }
    }
}

internal enum class ComicArchiveType {
    ZIP {
        override fun listImageEntries(input: InputStream): List<String> {
            return ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .filter { !it.isDirectory && it.name.isSupportedImageName() }
                    .map { it.name }
                    .toList()
                    .sortedWith(naturalPathOrder)
            }
        }

        override fun readEntry(input: InputStream, name: String): ByteArray? {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (!entry.isDirectory && entry.name == name) {
                        return zip.readBytes()
                    }
                }
            }
        }
    },
    TAR {
        override fun listImageEntries(input: InputStream): List<String> {
            return readTarEntries(input)
                .filter { it.name.isSupportedImageName() }
                .map { it.name }
                .sortedWith(naturalPathOrder)
        }

        override fun readEntry(input: InputStream, name: String): ByteArray? {
            var match: ByteArray? = null
            readTarEntries(input) { entryName, bytes ->
                if (entryName == name) match = bytes
            }
            return match
        }
    };

    abstract fun listImageEntries(input: InputStream): List<String>
    abstract fun readEntry(input: InputStream, name: String): ByteArray?
}

internal object DocumentTextExtractor {
    fun plainText(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)

    fun binaryText(bytes: ByteArray): String {
        val builder = StringBuilder()
        var current = StringBuilder()
        bytes.forEach { raw ->
            val value = raw.toInt() and 0xFF
            val char = value.toChar()
            if (char == '\n' || char == '\r' || char == '\t' || char in ' '..'~') {
                current.append(char)
            } else {
                if (current.length >= 4) builder.appendLine(current.toString())
                current = StringBuilder()
            }
        }
        if (current.length >= 4) builder.appendLine(current.toString())
        return builder.toString()
    }

    fun html(bytes: ByteArray): String {
        val source = bytes.toString(Charsets.UTF_8)
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    fun xml(bytes: ByteArray): String {
        val source = bytes.toString(Charsets.UTF_8)
        return source
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun docx(bytes: ByteArray): String {
        val documentXml = findZipEntry(bytes, "word/document.xml") ?: return ""
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(documentXml))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val text = StringBuilder()
        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val runs = paragraph.getElementsByTagNameNS("*", "t")
            for (runIndex in 0 until runs.length) {
                text.append(runs.item(runIndex).textContent)
            }
            text.appendLine()
        }
        return text.toString()
    }

    fun epub(bytes: ByteArray): String {
        val text = StringBuilder()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase(Locale.US)
                if (!entry.isDirectory && (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))) {
                    text.appendLine(html(zip.readBytes()))
                }
            }
        }
        return text.toString()
    }

    private fun findZipEntry(bytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (!entry.isDirectory && entry.name == name) return zip.readBytes()
            }
        }
    }
}

private object TextPaginator {
    const val PageWidth = 612
    const val PageHeight = 792
    private const val Margin = 48f
    private const val BodySize = 18f
    private const val LineHeight = 27f
    private const val ParagraphGap = 13f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 28, 28)
        textSize = BodySize
    }

    fun paginate(rawText: String): List<TextPage> {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isBlank()) return listOf(TextPage(emptyList()))

        val pages = mutableListOf<TextPage>()
        var currentWords = mutableListOf<PdfWord>()
        var pageIndex = 0
        var x = Margin
        var baseline = Margin + BodySize
        var globalWordIndex = 0
        var sentenceIndex = 0
        var paragraphIndex = 0

        fun newPage() {
            pages += TextPage(currentWords)
            currentWords = mutableListOf()
            pageIndex += 1
            x = Margin
            baseline = Margin + BodySize
        }

        fun newLine(extraGap: Float = 0f) {
            x = Margin
            baseline += LineHeight + extraGap
            if (baseline > PageHeight - Margin) newPage()
        }

        normalized.split(Regex("\\n{2,}|\\n")).forEach { paragraph ->
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                newLine(ParagraphGap)
            } else {
                words.forEach { word ->
                    val width = paint.measureText(word)
                    if (x > Margin && x + width > PageWidth - Margin) newLine()
                    val rect = RectF(x, baseline - BodySize, x + width, baseline + 5f)
                    currentWords += PdfWord(
                        text = word,
                        pageIndex = pageIndex,
                        boundingBox = rect,
                        normalizedText = word.lowercase(Locale.US).trim('.', ',', ';', ':', '!', '?', '"', '\''),
                        globalWordIndex = globalWordIndex++,
                        sentenceIndex = sentenceIndex,
                        paragraphIndex = paragraphIndex
                    )
                    x += width + paint.measureText(" ")
                    if (word.lastOrNull() in setOf('.', '!', '?')) sentenceIndex += 1
                }
                paragraphIndex += 1
                newLine(ParagraphGap)
            }
        }
        if (currentWords.isNotEmpty() || pages.isEmpty()) pages += TextPage(currentWords)
        return pages
    }

    fun drawPage(canvas: Canvas, page: TextPage) {
        var previousWord: PdfWord? = null
        page.words.forEach { word ->
            val prev = previousWord
            if (prev != null && prev.boundingBox.top == word.boundingBox.top) {
                canvas.drawText(" ", prev.boundingBox.right, word.boundingBox.bottom - 5f, paint)
            }
            canvas.drawText(word.text, word.boundingBox.left, word.boundingBox.bottom - 5f, paint)
            previousWord = word
        }
    }
}

private data class TextPage(
    val words: List<PdfWord>
)

private data class TarEntry(
    val name: String,
    val bytes: ByteArray
)

private fun readTarEntries(input: InputStream, visitor: ((String, ByteArray) -> Unit)? = null): List<TarEntry> {
    val entries = mutableListOf<TarEntry>()
    val header = ByteArray(512)
    while (true) {
        if (!input.readFully(header)) break
        if (header.all { it == 0.toByte() }) break
        val name = header.copyOfRange(0, 100).toNullTerminatedString()
        val size = header.copyOfRange(124, 136).toNullTerminatedString().trim().toLongOrNull(8) ?: 0L
        val bytes = input.readExactly(size)
        if (name.isNotBlank()) {
            visitor?.invoke(name, bytes)
            entries += TarEntry(name, bytes)
        }
        val padding = (512 - (size % 512)) % 512
        if (padding > 0) input.skipFully(padding)
    }
    return entries
}

private fun InputStream.readFully(buffer: ByteArray): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) return offset > 0
        offset += read
    }
    return true
}

private fun InputStream.readExactly(size: Long): ByteArray {
    val output = ByteArrayOutputStream(size.coerceAtMost(1024 * 1024).toInt())
    var remaining = size
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}

private fun InputStream.skipFully(size: Long) {
    var remaining = size
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() < 0) return
            remaining -= 1
        } else {
            remaining -= skipped
        }
    }
}

private fun ByteArray.toNullTerminatedString(): String {
    val end = indexOf(0).takeIf { it >= 0 } ?: size
    return copyOfRange(0, end).toString(Charsets.UTF_8)
}

private fun String.isSupportedImageName(): Boolean {
    val lower = lowercase(Locale.US)
    return lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".bmp")
}

private val naturalPathOrder = compareBy<String> { it.lowercase(Locale.US) }

private fun placeholderBitmap(targetWidthPx: Int, label: String): Bitmap {
    val width = targetWidthPx.coerceIn(360, 1000)
    val height = (width * 1.35f).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 80, 80)
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(label, width / 2f, height / 2f, paint)
    return bitmap
}
