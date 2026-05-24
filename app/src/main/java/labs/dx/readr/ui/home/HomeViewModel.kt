package labs.dx.readr.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import labs.dx.core.domain.model.CloudVoicePreferences
import labs.dx.core.domain.model.PdfHistoryEntry
import labs.dx.core.domain.repository.StorageRepository
import labs.dx.core.domain.repository.StorageResult
import labs.dx.core.domain.usecase.ObservePdfHistoryUseCase
import labs.dx.core.domain.usecase.RecordPdfSelectionUseCase
import labs.dx.core.domain.usecase.RemovePdfHistoryEntryUseCase
import labs.dx.core.domain.usecase.SetPdfPinnedUseCase
import labs.dx.readr.onboarding.OnboardingRepository

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val observePdfHistory: ObservePdfHistoryUseCase,
    private val recordPdfSelection: RecordPdfSelectionUseCase,
    private val removePdfHistoryEntry: RemovePdfHistoryEntryUseCase,
    private val setPdfPinned: SetPdfPinnedUseCase,
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observePdfHistory()
            .combine(onboardingRepository.isCompleted) { history, completed ->
                HomeUiState(
                    showOnboarding = !completed,
                    history = history,
                    suggested = history.take(3)
                )
            }
            .onEach { state ->
                _uiState.value = state
            }
            .launchIn(viewModelScope)
    }

    fun onDocumentSelected(uriString: String) {
        viewModelScope.launch {
            val document = when (val result = storageRepository.describeDocument(uriString)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return@launch
            }
            recordPdfSelection(document)
        }
    }

    fun onHistoryOpened(entry: PdfHistoryEntry) {
        viewModelScope.launch {
            recordPdfSelection(entry.document)
        }
    }

    fun onRemoveHistory(documentId: String) {
        viewModelScope.launch {
            removePdfHistoryEntry(documentId)
        }
    }

    fun onPinnedChanged(documentId: String, pinned: Boolean) {
        viewModelScope.launch {
            setPdfPinned(documentId, pinned)
        }
    }

    fun completeOnboarding(voicePreferences: CloudVoicePreferences = onboardingRepository.voicePreferences.value) {
        onboardingRepository.complete(voicePreferences)
    }
}
