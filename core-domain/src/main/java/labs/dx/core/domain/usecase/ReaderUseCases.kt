package labs.dx.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.ReadingPosition
import labs.dx.core.domain.repository.OpenPdfResult
import labs.dx.core.domain.repository.PdfRepository
import labs.dx.core.domain.repository.ReadingProgressRepository
import javax.inject.Inject

class OpenPdfDocumentUseCase @Inject constructor(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(document: DocumentDescriptor): OpenPdfResult {
        return pdfRepository.openDocument(document)
    }
}

class ObserveReadingProgressUseCase @Inject constructor(
    private val repository: ReadingProgressRepository
) {
    operator fun invoke(documentId: String): Flow<ReadingPosition?> = repository.observeProgress(documentId)
}

class SaveReadingProgressUseCase @Inject constructor(
    private val repository: ReadingProgressRepository
) {
    suspend operator fun invoke(position: ReadingPosition) {
        repository.saveProgress(position)
    }
}
