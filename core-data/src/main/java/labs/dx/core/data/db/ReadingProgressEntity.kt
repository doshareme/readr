package labs.dx.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import labs.dx.core.domain.model.ReadingPosition

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val documentId: String,
    val globalWordIndex: Int,
    val pageIndex: Int,
    val sentenceIndex: Int,
    val paragraphIndex: Int,
    val updatedAtEpochMillis: Long
)

fun ReadingProgressEntity.toModel(): ReadingPosition {
    return ReadingPosition(
        documentId = documentId,
        globalWordIndex = globalWordIndex,
        pageIndex = pageIndex,
        sentenceIndex = sentenceIndex,
        paragraphIndex = paragraphIndex,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}

fun ReadingPosition.toEntity(): ReadingProgressEntity {
    return ReadingProgressEntity(
        documentId = documentId,
        globalWordIndex = globalWordIndex,
        pageIndex = pageIndex,
        sentenceIndex = sentenceIndex,
        paragraphIndex = paragraphIndex,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}
