package labs.dx.core.domain.model

sealed interface ReaderError {
    data object EncryptedPdf : ReaderError
    data object ImageOnlyPdf : ReaderError
    data object CorruptedPdf : ReaderError
    data object MissingTtsEngine : ReaderError
    data object UnsupportedLanguage : ReaderError
    data object StoragePermissionDenied : ReaderError
    data object DocumentNotFound : ReaderError
    data object UnsupportedDocumentFormat : ReaderError
    data class Unknown(val message: String, val cause: Throwable? = null) : ReaderError
}
