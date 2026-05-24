package labs.dx.readr.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import labs.dx.core.domain.model.CloudVoicePreferences
import labs.dx.core.domain.model.CloudVoiceRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("readr_onboarding", Context.MODE_PRIVATE)
    private val completionKey = "completed"
    private val genderKey = "voice_gender"
    private val ageKey = "voice_age"
    private val regionKey = "voice_region"
    private val _isCompleted = MutableStateFlow(preferences.getBoolean(completionKey, false))
    private val _voicePreferences = MutableStateFlow(readVoicePreferences())

    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()
    val voicePreferences: StateFlow<CloudVoicePreferences> = _voicePreferences.asStateFlow()

    fun complete(voicePreferences: CloudVoicePreferences = _voicePreferences.value) {
        preferences.edit()
            .putBoolean(completionKey, true)
            .putString(genderKey, voicePreferences.gender)
            .putString(ageKey, voicePreferences.age)
            .putString(regionKey, voicePreferences.region.name)
            .apply()
        _voicePreferences.value = voicePreferences
        _isCompleted.value = true
    }

    private fun readVoicePreferences(): CloudVoicePreferences {
        val region = preferences.getString(regionKey, null)
            ?.let { saved -> CloudVoiceRegion.entries.firstOrNull { it.name == saved } }
            ?: CloudVoiceRegion.African
        return CloudVoicePreferences(
            gender = preferences.getString(genderKey, null) ?: "male",
            age = preferences.getString(ageKey, null) ?: "30s",
            region = region
        )
    }
}
