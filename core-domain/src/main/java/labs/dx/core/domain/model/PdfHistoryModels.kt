package labs.dx.core.domain.model

data class PdfHistoryEntry(
    val document: DocumentDescriptor,
    val lastOpenedAtEpochMillis: Long,
    val isPinned: Boolean,
    val pinnedAtEpochMillis: Long?,
    val coverImagePath: String?
)
