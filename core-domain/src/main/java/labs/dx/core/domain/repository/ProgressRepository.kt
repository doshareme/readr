package labs.dx.core.domain.repository

import kotlinx.coroutines.flow.Flow
import labs.dx.core.domain.model.ReadingPosition

interface ReadingProgressRepository {
    fun observeProgress(documentId: String): Flow<ReadingPosition?>
    suspend fun saveProgress(position: ReadingPosition)
}
