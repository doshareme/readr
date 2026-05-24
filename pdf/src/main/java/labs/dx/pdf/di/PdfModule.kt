package labs.dx.pdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import labs.dx.core.domain.repository.PdfRepository
import labs.dx.pdf.session.AndroidPdfRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {
    @Binds
    @Singleton
    abstract fun bindPdfRepository(
        impl: AndroidPdfRepository
    ): PdfRepository
}
