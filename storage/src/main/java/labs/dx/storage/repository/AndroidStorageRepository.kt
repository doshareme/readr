package labs.dx.storage.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.ReaderError
import labs.dx.core.domain.repository.StorageRepository
import labs.dx.core.domain.repository.StorageResult
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStorageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageRepository {

    override suspend fun persistReadPermission(uriString: String): StorageResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.fold(
            onSuccess = { StorageResult.Success(Unit) },
            onFailure = { StorageResult.Failure(ReaderError.StoragePermissionDenied) }
        )
    }

    override suspend fun describeDocument(uriString: String): StorageResult<DocumentDescriptor> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else "Document.pdf"
                    val sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                    val mimeType = resolver.getType(uri)
                    DocumentDescriptor(
                        documentId = buildDocumentId(uriString, displayName, sizeBytes),
                        uriString = uriString,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes
                    )
                } else {
                    throw IllegalStateException("Cursor was empty")
                }
            } ?: throw IllegalStateException("Unable to resolve document")
        }.fold(
            onSuccess = { StorageResult.Success(it) },
            onFailure = {
                val error = when (it) {
                    is SecurityException -> ReaderError.StoragePermissionDenied
                    else -> ReaderError.DocumentNotFound
                }
                StorageResult.Failure(error)
            }
        )
    }

    private fun buildDocumentId(uriString: String, displayName: String, sizeBytes: Long?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val raw = "$uriString|$displayName|${sizeBytes ?: -1L}"
        return digest.digest(raw.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }
}
