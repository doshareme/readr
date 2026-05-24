package labs.dx.storage.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import labs.dx.core.domain.repository.StorageRepository
import labs.dx.storage.repository.AndroidStorageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        impl: AndroidStorageRepository
    ): StorageRepository
}
