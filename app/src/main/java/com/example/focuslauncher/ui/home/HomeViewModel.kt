package com.example.focuslauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslauncher.data.AppModel
import com.example.focuslauncher.data.AppRepository
import com.example.focuslauncher.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val searchQuery: String = "",
    val allApps: List<AppModel> = emptyList(),
    val filteredApps: List<AppModel> = emptyList(),
    val favorites: List<AppModel> = emptyList(),
    val highUsageApps: List<AppModel> = emptyList(),
    val dustyApps: List<AppModel> = emptyList(),
    // weatherInfo removed
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val knowledgeRepository: com.example.focuslauncher.data.knowledge.KnowledgeRepository = com.example.focuslauncher.data.knowledge.KnowledgeRepository.getInstance(appRepository.context)
) : ViewModel() {

    // Knowledge State moved up to be initialized before init block
    private val _refreshState = MutableStateFlow<String>("Idle")
    val refreshState: StateFlow<String> = _refreshState.asStateFlow()
    
    val allNuggets = knowledgeRepository.allNuggets

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val isFocusModeEnabled = settingsRepository.isFocusModeEnabled
    
    val currentNugget: StateFlow<com.example.focuslauncher.data.knowledge.KnowledgeNugget?> = knowledgeRepository.currentNugget
    val selectedTopics = knowledgeRepository.selectedTopics
    val geminiApiKey = settingsRepository.geminiApiKey

    init {
        loadApps()
        // Initialize repository in background
        viewModelScope.launch {
            knowledgeRepository.initialize()
            refreshNugget() // Fetch fresh content after init
        }
    }
    


    fun refreshNugget() {
        viewModelScope.launch {
            _refreshState.value = "Loading"
            try {
                knowledgeRepository.refreshNuggets()
                _refreshState.value = "Success"
            } catch (e: Exception) {
                _refreshState.value = "Error"
            }
        }
    }
    
    fun addTopic(name: String) {
        if (name.isNotBlank()) {
            knowledgeRepository.addCustomTopic(name.trim())
            refreshNugget() // Auto-fetch
        }
    }
    
    fun removeTopic(topic: com.example.focuslauncher.data.knowledge.Topic) {
        knowledgeRepository.removeTopic(topic)
        // refreshNugget() // Optional: Refresh to remove nuggets of deleted topic? 
        // For now, let's keep them until manual refresh or next auto-fetch.
    }
    
    fun clearLibrary() {
        knowledgeRepository.clearAll()
        _refreshState.value = "Cleared"
    }
    
    fun verifyApiKey(apiKey: String) {
        viewModelScope.launch {
             _refreshState.value = "Verifying..."
             try {
                 val testTopic = com.example.focuslauncher.data.knowledge.Topic("verify", "Test")
                 val result = com.example.focuslauncher.data.knowledge.GeminiDataSource.fetchNuggetsForTopics(listOf(testTopic), apiKey)
                 if (result.isNotEmpty()) {
                     settingsRepository.setGeminiApiKey(apiKey)
                     _refreshState.value = "Verified"
                     refreshNugget() // Auto-load content
                 } else {
                     _refreshState.value = "Invalid Key"
                 }
             } catch (e: Exception) {
                 val msg = e.message ?: ""
                 when {
                     msg.contains("429") -> _refreshState.value = "Limit Reached"
                     msg.contains("400") || msg.contains("403") -> _refreshState.value = "Invalid Key"
                     else -> _refreshState.value = "Connection Error"
                 }
                 android.util.Log.e("FocusHome", "Verify Error: $msg")
             }
        }
    }
    
    fun nextNugget() = knowledgeRepository.nextNugget()
    fun previousNugget() = knowledgeRepository.previousNugget()
    
    private fun loadApps() {
        _uiState.update { it.copy(isLoading = true) }
        // Combine apps with deep focus state
        viewModelScope.launch {
            combine(
                appRepository.getApps(),
                settingsRepository.deepFocusEndTime
            ) { apps, endTime ->
                val currentTime = System.currentTimeMillis()
                val isDeepFocusActive = currentTime < endTime
                
                // If Deep Focus is active, override isBlocked for non-favorites
                if (isDeepFocusActive) {
                    apps.map { app ->
                        if (!app.isFavorite) {
                            app.copy(isBlocked = true) // Force block during deep focus
                        } else {
                            app
                        }
                    }
                } else {
                    apps
                }
            }.collectLatest { apps ->
                _uiState.update { currentState ->
                     val filtered = filterApps(apps, currentState.searchQuery)
                     val favorites = apps.filter { it.isFavorite }
                     
                     // Usage Logic
                     val highUsageThreshold = 60 * 60 * 1000L // 1 hour
                     val dustyThreshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L) // 30 days ago
                     
                     // Exclude ourselves
                     val launcherPackage = appRepository.contextPackageName
                     
                     val highUsage = apps.filter { it.usageDuration > highUsageThreshold && it.packageName != launcherPackage }.sortedByDescending { it.usageDuration }
                     val dusty = apps.filter { it.lastUsedTimestamp > 0 && it.lastUsedTimestamp < dustyThreshold && it.packageName != launcherPackage }

                    currentState.copy(
                        allApps = apps,
                        filteredApps = filtered,
                        favorites = favorites,
                        highUsageApps = highUsage,
                        dustyApps = dusty,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { currentState ->
            val filtered = filterApps(currentState.allApps, query)
            currentState.copy(searchQuery = query, filteredApps = filtered)
        }
    }
    
    fun onAppLongClick(app: AppModel) {
        viewModelScope.launch {
             appRepository.toggleFavorite(app)
        }
    }
    
    fun toggleFocusMode(enabled: Boolean) {
        settingsRepository.toggleFocusMode(enabled)
    }

    fun onToggleBlocked(app: AppModel) {
        viewModelScope.launch {
            appRepository.toggleBlocked(app)
        }
    }
    

    
    val deepFocusEndTime = settingsRepository.deepFocusEndTime
    
    fun setDeepFocus(minutes: Int) {
        val endTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        settingsRepository.setDeepFocusEndTime(endTime)
    }

    private fun filterApps(apps: List<AppModel>, query: String): List<AppModel> {
        return if (query.isBlank()) {
            apps
        } else {
            apps.filter { 
                it.label.contains(query, ignoreCase = true) 
            }
        }
    }
}

class HomeViewModelFactory(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(appRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
