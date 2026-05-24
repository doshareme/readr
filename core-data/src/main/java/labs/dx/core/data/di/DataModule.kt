package labs.dx.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import labs.dx.core.data.db.ReadrDatabase
import labs.dx.core.data.db.PdfHistoryDao
import labs.dx.core.data.db.ReadingProgressDao
import labs.dx.core.data.repository.RoomPdfHistoryRepository
import labs.dx.core.data.repository.RoomReadingProgressRepository
import labs.dx.core.domain.repository.PdfHistoryRepository
import labs.dx.core.domain.repository.ReadingProgressRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadrDatabase {
        return Room.databaseBuilder(
            context,
            ReadrDatabase::class.java,
            "readr.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideReadingProgressDao(database: ReadrDatabase): ReadingProgressDao {
        return database.readingProgressDao()
    }

    @Provides
    fun providePdfHistoryDao(database: ReadrDatabase): PdfHistoryDao {
        return database.pdfHistoryDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(
        impl: RoomReadingProgressRepository
    ): ReadingProgressRepository

    @Binds
    @Singleton
    abstract fun bindPdfHistoryRepository(
        impl: RoomPdfHistoryRepository
    ): PdfHistoryRepository
}
