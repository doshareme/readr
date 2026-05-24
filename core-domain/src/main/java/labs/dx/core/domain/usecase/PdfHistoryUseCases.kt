package labs.dx.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.PdfHistoryEntry
import labs.dx.core.domain.repository.PdfHistoryRepository
import javax.inject.Inject

class ObservePdfHistoryUseCase @Inject constructor(
    private val repository: PdfHistoryRepository
) {
    operator fun invoke(): Flow<List<PdfHistoryEntry>> = repository.observeHistory()
}

class RecordPdfSelectionUseCase @Inject constructor(
    private val repository: PdfHistoryRepository
) {
    suspend operator fun invoke(document: DocumentDescriptor) {
        repository.recordSelection(document)
    }
}

class RemovePdfHistoryEntryUseCase @Inject constructor(
    private val repository: PdfHistoryRepository
) {
    suspend operator fun invoke(documentId: String) {
        repository.remove(documentId)
    }
}

class UpdatePdfHistoryCoverUseCase @Inject constructor(
    private val repository: PdfHistoryRepository
) {
    suspend operator fun invoke(documentId: String, coverImagePath: String) {
        repository.updateCover(documentId, coverImagePath)
    }
}

class SetPdfPinnedUseCase @Inject constructor(
    private val repository: PdfHistoryRepository
) {
    suspend operator fun invoke(documentId: String, pinned: Boolean) {
        repository.setPinned(documentId, pinned)
    }
}
