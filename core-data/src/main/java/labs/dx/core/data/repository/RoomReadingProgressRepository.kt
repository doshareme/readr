package labs.dx.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import labs.dx.core.data.db.ReadingProgressDao
import labs.dx.core.data.db.toEntity
import labs.dx.core.data.db.toModel
import labs.dx.core.domain.model.ReadingPosition
import labs.dx.core.domain.repository.ReadingProgressRepository
import javax.inject.Inject

class RoomReadingProgressRepository @Inject constructor(
    private val dao: ReadingProgressDao
) : ReadingProgressRepository {
    override fun observeProgress(documentId: String): Flow<ReadingPosition?> {
        return dao.observeByDocumentId(documentId).map { it?.toModel() }
    }

    override suspend fun saveProgress(position: ReadingPosition) {
        dao.upsert(position.toEntity())
    }
}
