package labs.dx.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import labs.dx.core.data.db.PdfHistoryDao
import labs.dx.core.data.db.PdfHistoryEntity
import labs.dx.core.data.db.toModel
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfHistoryEntry
import labs.dx.core.domain.repository.PdfHistoryRepository
import javax.inject.Inject

class RoomPdfHistoryRepository @Inject constructor(
    private val dao: PdfHistoryDao
) : PdfHistoryRepository {
    override fun observeHistory(): Flow<List<PdfHistoryEntry>> {
        return dao.observeAll().map { entries -> entries.map(PdfHistoryEntity::toModel) }
    }

    override suspend fun recordSelection(document: DocumentDescriptor, openedAtEpochMillis: Long) {
        val existing = dao.getByDocumentId(document.documentId)
        dao.upsert(
            PdfHistoryEntity(
                documentId = document.documentId,
                uriString = document.uriString,
                displayName = document.displayName,
                mimeType = document.mimeType,
                sizeBytes = document.sizeBytes,
                lastOpenedAtEpochMillis = openedAtEpochMillis,
                isPinned = existing?.isPinned ?: false,
                pinnedAtEpochMillis = existing?.pinnedAtEpochMillis,
                coverImagePath = existing?.coverImagePath
            )
        )
    }

    override suspend fun updateCover(documentId: String, coverImagePath: String) {
        dao.updateCover(documentId, coverImagePath)
    }

    override suspend fun remove(documentId: String) {
        dao.deleteByDocumentId(documentId)
    }

    override suspend fun setPinned(documentId: String, pinned: Boolean) {
        dao.updatePinned(
            documentId = documentId,
            pinned = pinned,
            pinnedAtEpochMillis = if (pinned) System.currentTimeMillis() else null
        )
    }
}
