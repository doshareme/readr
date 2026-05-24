package labs.dx.readr.ui.reader

import labs.dx.core.domain.model.DocumentDescriptor
import labs.dx.core.domain.model.NarrationHighlight
import labs.dx.core.domain.model.NarrationSettings
import labs.dx.core.domain.model.NarrationVoice
import labs.dx.core.domain.model.PdfDocumentInfo
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError

data class ReaderUiState(
    val isLoading: Boolean = true,
    val document: DocumentDescriptor? = null,
    val documentInfo: PdfDocumentInfo? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentHighlight: NarrationHighlight? = null,
    val voices: List<NarrationVoice> = emptyList(),
    val settings: NarrationSettings = NarrationSettings(),
    val savedWordIndex: Int = 0,
    val totalWordCount: Int = 0,
    val error: ReaderError? = null
)
