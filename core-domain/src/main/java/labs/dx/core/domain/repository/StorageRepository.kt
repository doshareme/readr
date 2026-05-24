package labs.dx.core.domain.repository

import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.ReaderError

sealed interface StorageResult<out T> {
    data class Success<T>(val value: T) : StorageResult<T>
    data class Failure(val error: ReaderError) : StorageResult<Nothing>
}

interface StorageRepository {
    suspend fun persistReadPermission(uriString: String): StorageResult<Unit>
    suspend fun describeDocument(uriString: String): StorageResult<DocumentDescriptor>
}
