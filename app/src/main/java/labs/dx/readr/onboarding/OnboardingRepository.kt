package labs.dx.readr.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("readr_onboarding", Context.MODE_PRIVATE)
    private val completionKey = "completed"
    private val _isCompleted = MutableStateFlow(preferences.getBoolean(completionKey, false))

    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    fun complete() {
        preferences.edit().putBoolean(completionKey, true).apply()
        _isCompleted.value = true
    }
}
