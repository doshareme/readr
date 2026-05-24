package labs.dx.core.domain.model

import java.util.Locale

enum class DocumentFormat(val extensions: Set<String>, val displayName: String) {
    PDF(setOf("pdf"), "PDF"),
    EPUB(setOf("epub"), "EPUB"),
    CHM(setOf("chm"), "CHM"),
    CBR(setOf("cbr"), "CBR"),
    CBZ(setOf("cbz"), "CBZ"),
    CB7(setOf("cb7"), "CB7"),
    CBT(setOf("cbt"), "CBT"),
    CBA(setOf("cba"), "CBA"),
    DOC(setOf("doc"), "Word document"),
    HTML(setOf("html", "htm"), "HTML"),
    TXT(setOf("txt"), "Text"),
    XML(setOf("xml"), "XML"),
    IBOOKS(setOf("ibooks"), "iBooks"),
    DOCX(setOf("docx"), "Word document"),
    UNKNOWN(emptySet(), "Document");

    companion object {
        val supportedExtensions: Set<String> = entries
            .filterNot { it == UNKNOWN }
            .flatMap { it.extensions }
            .toSet()

        val pickerMimeTypes: Array<String> = arrayOf(
            "application/pdf",
            "application/epub+zip",
            "application/vnd.apple.ibooks+zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/html",
            "application/xhtml+xml",
            "text/xml",
            "application/xml",
            "application/zip",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "application/x-cbr",
            "application/vnd.comicbook-rar",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/x-ace-compressed",
            "application/vnd.ms-htmlhelp",
            "application/octet-stream"
        )

        fun from(document: DocumentDescriptor): DocumentFormat {
            return fromName(document.displayName)
        }

        fun fromName(name: String): DocumentFormat {
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US)
            return entries.firstOrNull { extension in it.extensions } ?: UNKNOWN
        }

        fun isSupportedName(name: String): Boolean {
            return fromName(name) != UNKNOWN
        }
    }
}
