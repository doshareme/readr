package labs.dx.core.domain.model

data class ReadingPosition(
    val documentId: String,
    val globalWordIndex: Int,
    val pageIndex: Int,
    val sentenceIndex: Int,
    val paragraphIndex: Int,
    val updatedAtEpochMillis: Long
)

data class NarrationSettings(
    val speechRate: Float = 1.0f,
    val voiceName: String? = null,
    val localeTag: String? = null,
    val researchPaperMode: Boolean = false,
    val useCloudTts: Boolean = false,
    val storyMode: Boolean = false,
    val cloudVoice: CloudVoicePreferences = CloudVoicePreferences()
)

data class CloudVoicePreferences(
    val gender: String = "male",
    val age: String = "30s",
    val region: CloudVoiceRegion = CloudVoiceRegion.African
) {
    val description: String
        get() = "a $gender voice in $age with ${region.promptValue} accent and casual speaker with medium intensity at conversational pace"
}

enum class CloudVoiceRegion(
    val displayName: String,
    val promptValue: String
) {
    American("American", "american"),
    European("European", "british"),
    African("African", "middle_eastern"),
    Asian("Asian", "asian_american"),
    Indian("Indian", "Indian")
}

data class NarrationVoice(
    val voiceName: String,
    val displayName: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val isInstalled: Boolean
)

enum class HighlightMode {
    WORD,
    SENTENCE,
    CHUNK
}

data class NarrationHighlight(
    val word: PdfWord,
    val mode: HighlightMode
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Preparing : PlaybackState
    data class Playing(val currentWordIndex: Int) : PlaybackState
    data class Paused(val currentWordIndex: Int) : PlaybackState
    data class Stopped(val currentWordIndex: Int) : PlaybackState
    data object Completed : PlaybackState
    data class Error(val error: ReaderError) : PlaybackState
}
