package labs.dx.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingProgressEntity::class, PdfHistoryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class ReadrDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun pdfHistoryDao(): PdfHistoryDao
}
