package labs.dx.readr.ui.home

import labs.dx.core.domain.model.PdfHistoryEntry

data class HomeUiState(
    val showOnboarding: Boolean = false,
    val history: List<PdfHistoryEntry> = emptyList(),
    val suggested: List<PdfHistoryEntry> = emptyList()
)
