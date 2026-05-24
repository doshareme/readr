package labs.dx.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfHistoryEntry

@Entity(tableName = "pdf_history")
data class PdfHistoryEntity(
    @PrimaryKey val documentId: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastOpenedAtEpochMillis: Long,
    val isPinned: Boolean,
    val pinnedAtEpochMillis: Long?,
    val coverImagePath: String?
)

fun PdfHistoryEntity.toModel(): PdfHistoryEntry {
    return PdfHistoryEntry(
        document = DocumentDescriptor(
            documentId = documentId,
            uriString = uriString,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        ),
        lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
        isPinned = isPinned,
        pinnedAtEpochMillis = pinnedAtEpochMillis,
        coverImagePath = coverImagePath
    )
}
