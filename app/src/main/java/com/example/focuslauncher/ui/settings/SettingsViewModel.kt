package com.example.focuslauncher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslauncher.data.AppModel
import com.example.focuslauncher.data.AppRepository
import com.example.focuslauncher.data.SettingsRepository
import com.example.focuslauncher.data.knowledge.KnowledgeRepository
import com.example.focuslauncher.data.knowledge.Topic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private val settingsRepository = SettingsRepository(context)
    private val knowledgeRepository = KnowledgeRepository.getInstance(context)
    private val appRepository = AppRepository(context)

    // Mode Toggles
    // Mode Toggles
    val isFocusModeEnabled = settingsRepository.isFocusModeEnabled

    fun toggleFocusMode(enabled: Boolean) = settingsRepository.toggleFocusMode(enabled)
    
    // Quiz Mode
    val isQuizModeEnabled = settingsRepository.isQuizModeEnabled
    fun toggleQuizMode(enabled: Boolean) {
        settingsRepository.toggleQuizMode(enabled)
        refreshNuggets() // Auto-fetch appropriate content
    }
    
    // Wait Time Configuration
    val focusWaitTimeMinutes = settingsRepository.focusWaitTimeMinutes
    fun setFocusWaitTimeMinutes(minutes: Int) = settingsRepository.setFocusWaitTimeMinutes(minutes)
    
    val standardWaitTimeSeconds = settingsRepository.standardWaitTimeSeconds
    fun setStandardWaitTimeSeconds(seconds: Int) = settingsRepository.setStandardWaitTimeSeconds(seconds)
    
    // PDF/File Analysis Removed per user request


    // Topics
    val selectedTopics = knowledgeRepository.selectedTopics

    fun toggleTopic(topic: Topic) {
        knowledgeRepository.toggleTopic(topic)
        android.widget.Toast.makeText(context, "Refreshing knowledge...", android.widget.Toast.LENGTH_SHORT).show()
        refreshNuggets()
    }
    
    fun addTopic(name: String) {
        if (name.isNotBlank()) {
            knowledgeRepository.addCustomTopic(name.trim())
            refreshNuggets()
        }
    }
    
    private fun refreshNuggets() {
        viewModelScope.launch {
            try {
                knowledgeRepository.refreshNuggets()
            } catch (e: Exception) {
                // Log error or show toast if needed (context available?)
                android.util.Log.e("FocusSettings", "Failed to refresh: ${e.message}")
            }
        }
    }

    fun clearLibrary() {
        knowledgeRepository.clearAll()
        android.widget.Toast.makeText(context, "Knowledge Base Cleared", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Excluded Apps
    private val _installedApps = MutableStateFlow<List<AppModel>>(emptyList())
    val excludedApps = settingsRepository.excludedApps

    val appListState = combine(_installedApps, excludedApps) { apps, excluded ->
        apps.map { app ->
            app.copy(isFavorite = excluded.contains(app.packageName)) // isFavorite used as 'isExcluded' here
        }.sortedWith(
            compareByDescending<AppModel> { it.isFavorite } // Show Excluded (Favorite=true) first
                .thenBy { it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            appRepository.getApps().collect { apps ->
                _installedApps.value = apps
            }
        }
    }

    fun toggleAppExclusion(packageName: String) {
        settingsRepository.toggleExcludedApp(packageName)
    }
    
    // Gemini API Key
    val geminiApiKey = settingsRepository.geminiApiKey
    
    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState
    
    fun setGeminiApiKey(key: String) {
        // Just update local text, don't save to prefs yet until verified or user explicitly saves?
        // User asked for "check in setting itself if API key is working".
        // Let's keep existing behavior (save immediately on type) OR change to verify-first.
        // The implementation plan says: "On Success -> Save Key". 
        // So we should maybe separate the input field state from the saved key state, 
        // OR just save it but let them verify it.
        // Let's go with: Save it as they type (so they don't lose it), but Verify button tests it.
        settingsRepository.setGeminiApiKey(key)
        _verificationState.value = VerificationState.Idle
    }
    
    fun verifyApiKey() {
        val key = _verificationState.value.let { 
            // We read from repository since we save on type
           // Actually, deeper access:
           // We need the current key from flow, but flows are async. 
           // Let's assumes settingsRepository.geminiApiKey is source of truth.
           // Ideally we pass current input text.
           null
        }
    }
    
    fun verifyAndRefretch(apiKey: String) {
        viewModelScope.launch {
            _verificationState.value = VerificationState.Loading
            try {
                // Test with a simple topic
                val testTopic = Topic("TEST", "Artificial Intelligence")
                val results = com.example.focuslauncher.data.knowledge.GeminiDataSource.fetchNuggetsForTopics(listOf(testTopic), apiKey)
                
                if (results.isNotEmpty()) {
                    // Success!
                    settingsRepository.setGeminiApiKey(apiKey)
                    _verificationState.value = VerificationState.Success
                    // Trigger full refresh to clean up old data and load new Gemini content
                    knowledgeRepository.refreshNuggets()
                } else {
                    _verificationState.value = VerificationState.Error
                }
            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error
            }
        }
    }
}

enum class VerificationState {
    Idle, Loading, Success, Error
}
