package com.example.focuslauncher.data.knowledge

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random
import kotlinx.coroutines.launch

class KnowledgeRepository private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: KnowledgeRepository? = null

        fun getInstance(context: Context): KnowledgeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KnowledgeRepository(context.applicationContext).also { repo ->
                    INSTANCE = repo
                    // Load data immediately on first creation
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repo.initialize()
                    }
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("knowledge_prefs", Context.MODE_PRIVATE)
    
    // Topics user wants to learn (store as set of strings)
    private val _selectedTopics = MutableStateFlow(
        prefs.getStringSet("selected_topics", emptySet())?.map { Topic(it, it.replace("_", " ")) }?.toSet() 
            ?: emptySet()
    )
    val selectedTopics: StateFlow<Set<Topic>> = _selectedTopics.asStateFlow()

    // Cache of fetched nuggets (The Playlist)
    private val _fetchedNuggets = MutableStateFlow<List<KnowledgeNugget>>(emptyList())
    val allNuggets: StateFlow<List<KnowledgeNugget>> = _fetchedNuggets.asStateFlow()
    
    // Current Index for Home Screen Widget
    private val _currentIndex = MutableStateFlow(0)
    val currentNugget: StateFlow<KnowledgeNugget?> = combine(_fetchedNuggets, _currentIndex) { list, index ->
        if (list.isNotEmpty() && index in list.indices) list[index] else null
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), 
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly, 
        initialValue = null
    )

    // Quiz Cache
    private val _fetchedQuizzes = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val allQuizzes: StateFlow<List<QuizQuestion>> = _fetchedQuizzes.asStateFlow()

    // Quiz Index
    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuiz: StateFlow<QuizQuestion?> = combine(_fetchedQuizzes, _currentQuizIndex) { list, index ->
        if (list.isNotEmpty() && index in list.indices) list[index] else null
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = null
    )

    private val gson = com.google.gson.Gson()

    init {
        // init is now empty to prevent main thread blocking
    }

    suspend fun initialize() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Load cached nuggets on startup
            loadNuggetsFromPrefs()
            loadQuizzesFromPrefs()
            
            // Auto-Refresh Logic (24 hours)
            val lastRefresh = prefs.getLong("last_refresh_time", 0L)
            val currentTime = System.currentTimeMillis()
            val twentyFourHours = 24 * 60 * 60 * 1000L
            
            if (currentTime - lastRefresh > twentyFourHours) {
                android.util.Log.d("FocusKnowledge", "Auto-Refresh Triggered > 24h")
                refreshNuggets()
            }
            
            // If cache is empty (first run), load curated high-quality nuggets immediately
            if (_fetchedNuggets.value.isEmpty()) {
                val curated = CuratedKnowledgeDataSource.getCuratedNuggets().shuffled()
                _fetchedNuggets.emit(curated)
                saveNuggetsToPrefs(curated)
                android.util.Log.d("FocusKnowledge", "Loaded ${curated.size} curated nuggets for first run.")
            }
        }
    }

    suspend fun refreshNuggets() {
        // Fetch fresh nuggets for the currently selected topics
        val topics = _selectedTopics.value.toList()
        android.util.Log.d("FocusKnowledge", "Repository refreshNuggets called. Topics: ${topics.size}")
        System.out.println("FocusKnowledge: Repository refreshNuggets called")
        
        if (topics.isNotEmpty()) {
            val settingsPrefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
            val apiKey = settingsPrefs.getString("gemini_api_key", null)
            val newNuggets = mutableListOf<KnowledgeNugget>()
            var isGeminiSuccess = false
            
            if (!apiKey.isNullOrEmpty()) {
                     // Check if Quiz Mode is enabled
                     val isQuizMode = settingsPrefs.getBoolean("quiz_mode_enabled", false)
                     
                     if (isQuizMode) {
                         android.util.Log.d("FocusKnowledge", "Quiz Mode Enabled: Fetching Quizzes...")
                         val questions = GeminiDataSource.fetchQuizQuestionsForTopics(topics, apiKey)
                         if (questions.isNotEmpty()) {
                             val currentQs = _fetchedQuizzes.value
                             val newQs = questions.shuffled()
                             _fetchedQuizzes.emit(newQs) // Replace for simplicity or append? Let's replace for fresh quizzes.
                             saveQuizzesToPrefs(newQs)
                             android.util.Log.d("FocusKnowledge", "Fetched ${newQs.size} quizzes.")
                         } else {
                             android.util.Log.e("FocusKnowledge", "Gemini Quiz Fetch yielded 0 results.")
                         }
                         // Should we ALSO fetch nuggets? Maybe not.
                         return
                     }
                     
                     android.util.Log.d("FocusKnowledge", "Using Gemini API Key for batch fetch")
                     // Single Batch Call for ALL topics
                     val nuggets = GeminiDataSource.fetchNuggetsForTopics(topics, apiKey)
                     if (nuggets.isNotEmpty()) {
                         newNuggets.addAll(nuggets)
                         isGeminiSuccess = true
                     }
                } else if (!apiKey.isNullOrEmpty() && newNuggets.isEmpty()) { 
                    // Fallback handled below
                } else {
                     android.util.Log.d("FocusKnowledge", "Using Wikipedia (No API Key)")
                     newNuggets.addAll(NetworkDataSource.fetchNuggetsForTopics(topics))
                }

            android.util.Log.d("FocusKnowledge", "Repository received ${newNuggets.size} new nuggets")
            System.out.println("FocusKnowledge: Repository received ${newNuggets.size} nuggets")
            
            if (newNuggets.isNotEmpty()) {
                val currentList = _fetchedNuggets.value
                val updatedList: List<KnowledgeNugget>
                
                if (isGeminiSuccess) {
                    // PURGE old content if Gemini succeeded.
                    // User wants "only gemini nuggets" if key is present.
                    android.util.Log.d("FocusKnowledge", "Purging old content for fresh Gemini nuggets")
                    updatedList = newNuggets.shuffled()
                } else {
                    // Normal append logic (Wikipedia or Mixed)
                    val filteredCurrent = if (currentList.size == 1 && currentList[0].id == "error") emptyList() else currentList
                    val initialIds = filteredCurrent.map { it.id }.toSet()
                    val uniqueNew = newNuggets.filter { !initialIds.contains(it.id) }
                    updatedList = (filteredCurrent + uniqueNew).shuffled()
                }
                
                _fetchedNuggets.emit(updatedList)
                saveNuggetsToPrefs(updatedList)
                
                // Save Last Refresh Time
                prefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()
                
                android.util.Log.d("FocusKnowledge", "Repository emitted total ${updatedList.size} nuggets (shuffled)")
            } else {
                 if (!apiKey.isNullOrEmpty()) {
                     // Gemini Mode: Failed to fetch.
                     // User Request: "dont clean old fetched questions if refresh fails"
                     // So we do NOTHING here regarding the cache. We just Log it.
                     android.util.Log.e("FocusKnowledge", "Gemini Fetch yielded 0 results. Keeping old data.")
                     // Optional: Could emit a "Toast Event" if we had an event flow, but for now silent failure is requested.
                 } else if (_fetchedNuggets.value.isEmpty()) {
                     // Only show error if we have NOTHING cached (and no API key)
                    _fetchedNuggets.emit(listOf(
                        KnowledgeNugget(
                            id = "error",
                            topic = Topic("ERR", "Connection Error"),
                            difficulty = Difficulty.BEGINNER,
                            shortText = "No Knowledge Found",
                            detailedText = "Could not fetch new knowledge. Please check your internet connection and tap to refresh."
                        )
                    ))
                 }
            }
        }
    }
    
    fun nextNugget() {
        if (_fetchedNuggets.value.isNotEmpty()) {
            _currentIndex.value = (_currentIndex.value + 1) % _fetchedNuggets.value.size
        }
    }
    
    fun previousNugget() {
        if (_fetchedNuggets.value.isNotEmpty()) {
             val size = _fetchedNuggets.value.size
            _currentIndex.value = (_currentIndex.value - 1 + size) % size
        }
    }

    fun nextQuiz() {
        if (_fetchedQuizzes.value.isNotEmpty()) {
            _currentQuizIndex.value = (_currentQuizIndex.value + 1) % _fetchedQuizzes.value.size
        }
    }

    fun getRandomQuiz(): QuizQuestion? {
        val list = _fetchedQuizzes.value
        if (list.isNotEmpty()) {
            return list.random()
        }
        return null
    }

    fun getRandomNugget(): KnowledgeNugget {
        val list = _fetchedNuggets.value
        if (list.isNotEmpty()) {
            // Pick a random one!
            return list.random()
        }
        return KnowledgeNugget("fallback", Topic("GEN", "General"), Difficulty.BEGINNER, "Loading...", "Please wait while we fetch knowledge.")
    }

    fun getDeepKnowledge(topicId: String? = null): KnowledgeNugget {
         // In a real app, we'd fetch a long article. For now, generate a "Deep Dive".
         val topic = if (topicId != null) Topic(topicId, topicId) else _selectedTopics.value.firstOrNull() ?: Topic("GEN", "General")
         
         val deepDiveText = """
             Here is a deep dive into ${topic.displayName}.
             
             1. **History**: It started as a small concept but grew into a standard. Understanding the roots helps you appreciate the modern abstractions.
             
             2. **Core Concepts**:
                - *Abstraction*: Hiding complex reality while exposing only the necessary parts.
                - *Encapsulation*: Keeping fields within a class private, then providing access via public methods.
                - *Inheritance*: Creating new classes from existing ones.
                - *Polymorphism*: The ability of a message to be displayed in more than one form.
                
             3. **Advanced Usage**:
                When dealing with large scale systems, consistency and availability often conflict (CAP Theorem).
                Optimizing for throughput might sacrifice latency.
                
             4. **Best Practices**:
                - Always favor composition over inheritance.
                - Keep methods small and focused (Single Responsibility Principle).
                - Test early, test often.
                
             (This is a placeholder for a much longer, fetched article about ${topic.displayName}.)
         """.trimIndent()
         
         return KnowledgeNugget(
             id = "deep_${System.currentTimeMillis()}",
             topic = topic,
             difficulty = Difficulty.ADVANCED,
             shortText = "Deep Dive: ${topic.displayName}",
             detailedText = deepDiveText
         )
    }

    fun clearAll() {
        _fetchedNuggets.value = emptyList()
        prefs.edit().remove("cached_nuggets").apply()
        android.util.Log.d("FocusKnowledge", "Cleared all cached nuggets.")
    }
    
    fun removeTopic(topic: Topic) {
        val currentTopics = _selectedTopics.value.toMutableSet()
        val existingTopic = currentTopics.find { it.id == topic.id }
        
        if (existingTopic != null) {
            // 1. Remove from Selected Topics
            currentTopics.remove(existingTopic)
            updateTopics(currentTopics)
            
            // 2. Remove associated Nuggets from Cache
            val currentNuggets = _fetchedNuggets.value
            val updatedNuggets = currentNuggets.filter { it.topic.id != topic.id }
            
            if (currentNuggets.size != updatedNuggets.size) {
                _fetchedNuggets.value = updatedNuggets
                saveNuggetsToPrefs(updatedNuggets)
                android.util.Log.d("FocusKnowledge", "Removed ${currentNuggets.size - updatedNuggets.size} nuggets for topic ${topic.displayName}")
            }
        }
    }
    
    fun toggleTopic(topic: Topic) {
        val current = _selectedTopics.value.toMutableSet()
        // Check by ID to handle custom topics properly
        val existing = current.find { it.id == topic.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(topic)
        }
        updateTopics(current)
    }
    
    fun addCustomTopic(topicName: String) {
        val id = topicName.uppercase().replace(" ", "_")
        val topic = Topic(id, topicName)
        val current = _selectedTopics.value.toMutableSet()
        current.add(topic)
        updateTopics(current)
    }
    
    // PERSISTENCE HELPERS
    
    private fun updateTopics(topics: Set<Topic>) {
        _selectedTopics.value = topics
        prefs.edit().putStringSet("selected_topics", topics.map { it.id }.toSet()).apply()
    }
    
    private fun saveNuggetsToPrefs(nuggets: List<KnowledgeNugget>) {
        if (nuggets.isEmpty()) return
        // Don't save errors
        val validNuggets = nuggets.filter { it.id != "error" }
        val json = gson.toJson(validNuggets)
        prefs.edit().putString("cached_nuggets", json).apply()
        System.out.println("FocusKnowledge: Saved ${validNuggets.size} nuggets to cache")
    }
    
    private fun loadNuggetsFromPrefs() {
        val json = prefs.getString("cached_nuggets", null)
        if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<KnowledgeNugget>>() {}.type
                val cached: List<KnowledgeNugget> = gson.fromJson(json, type)
                if (cached.isNotEmpty()) {
                    // Shuffle loaded cache too!
                    _fetchedNuggets.tryEmit(cached.shuffled())
                    System.out.println("FocusKnowledge: Loaded ${cached.size} nuggets from cache (shuffled)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveQuizzesToPrefs(quizzes: List<QuizQuestion>) {
        if (quizzes.isEmpty()) return
        val json = gson.toJson(quizzes)
        prefs.edit().putString("cached_quizzes", json).apply()
    }

    private fun loadQuizzesFromPrefs() {
        val json = prefs.getString("cached_quizzes", null)
        if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<QuizQuestion>>() {}.type
                val cached: List<QuizQuestion> = gson.fromJson(json, type)
                if (cached.isNotEmpty()) {
                    _fetchedQuizzes.tryEmit(cached.shuffled())
                    android.util.Log.d("FocusKnowledge", "Loaded ${cached.size} quizzes from cache")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
