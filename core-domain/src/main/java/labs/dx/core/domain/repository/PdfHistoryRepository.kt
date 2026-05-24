package labs.dx.core.domain.repository

import kotlinx.coroutines.flow.Flow
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfHistoryEntry

interface PdfHistoryRepository {
    fun observeHistory(): Flow<List<PdfHistoryEntry>>
    suspend fun recordSelection(document: DocumentDescriptor, openedAtEpochMillis: Long = System.currentTimeMillis())
    suspend fun updateCover(documentId: String, coverImagePath: String)
    suspend fun remove(documentId: String)
    suspend fun setPinned(documentId: String, pinned: Boolean)
}
