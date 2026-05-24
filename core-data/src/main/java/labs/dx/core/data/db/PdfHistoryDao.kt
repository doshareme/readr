package labs.dx.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfHistoryDao {
    @Query(
        """
        SELECT * FROM pdf_history
        ORDER BY 
            isPinned DESC,
            CASE WHEN isPinned = 1 THEN COALESCE(pinnedAtEpochMillis, 0) ELSE lastOpenedAtEpochMillis END DESC,
            lastOpenedAtEpochMillis DESC
        """
    )
    fun observeAll(): Flow<List<PdfHistoryEntity>>

    @Query("SELECT * FROM pdf_history WHERE documentId = :documentId LIMIT 1")
    suspend fun getByDocumentId(documentId: String): PdfHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfHistoryEntity)

    @Query("DELETE FROM pdf_history WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query(
        """
        UPDATE pdf_history
        SET isPinned = :pinned,
            pinnedAtEpochMillis = :pinnedAtEpochMillis
        WHERE documentId = :documentId
        """
    )
    suspend fun updatePinned(
        documentId: String,
        pinned: Boolean,
        pinnedAtEpochMillis: Long?
    )

    @Query(
        """
        UPDATE pdf_history
        SET coverImagePath = :coverImagePath
        WHERE documentId = :documentId
        """
    )
    suspend fun updateCover(
        documentId: String,
        coverImagePath: String
    )
}
