package com.example.focuslauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
    
    private val _isFocusModeEnabled = MutableStateFlow(sharedPreferences.getBoolean("focus_mode", false))
    val isFocusModeEnabled: Flow<Boolean> = _isFocusModeEnabled

    private val _deepFocusEndTime = MutableStateFlow(sharedPreferences.getLong("deep_focus_end_time", 0L))
    val deepFocusEndTime: Flow<Long> = _deepFocusEndTime

    fun toggleFocusMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("focus_mode", enabled).apply()
        _isFocusModeEnabled.value = enabled
    }
    
    fun setDeepFocusEndTime(endTime: Long) {
        sharedPreferences.edit().putLong("deep_focus_end_time", endTime).apply()
        _deepFocusEndTime.value = endTime
    }


    


    // Excluded Apps
    private val _excludedApps = MutableStateFlow<Set<String>>(
        sharedPreferences.getStringSet("excluded_apps", setOf("com.android.settings", "com.google.android.dialer", "com.google.android.calculator", "com.android.phone")) ?: emptySet()
    )
    val excludedApps: Flow<Set<String>> = _excludedApps

    fun toggleExcludedApp(packageName: String) {
        val current = _excludedApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _excludedApps.value = current
        sharedPreferences.edit().putStringSet("excluded_apps", current).apply()
    }
    
    fun isAppExcluded(packageName: String): Boolean {
        // Defaults: Settings and Phone are always safe to fallback if list is empty or weird
        if (packageName == "com.android.settings" || packageName.contains("dialer") || packageName.contains("phone")) return true
        return _excludedApps.value.contains(packageName)
    }
    
    // Gemini API Key
    private val _geminiApiKey = MutableStateFlow(sharedPreferences.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: Flow<String> = _geminiApiKey
    
    fun setGeminiApiKey(key: String) {
        sharedPreferences.edit().putString("gemini_api_key", key).apply()
        _geminiApiKey.value = key
    }
    
    // Wait Time Configuration
    private val _focusWaitTimeMinutes = MutableStateFlow(sharedPreferences.getInt("focus_wait_time_minutes", 5)) // Default 5 mins
    val focusWaitTimeMinutes: Flow<Int> = _focusWaitTimeMinutes
    
    fun setFocusWaitTimeMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(1) // enforce min 1 minute
        sharedPreferences.edit().putInt("focus_wait_time_minutes", safeMinutes).apply()
        _focusWaitTimeMinutes.value = safeMinutes
    }

    // Standard Wait Time (Friction)
    private val _standardWaitTimeSeconds = MutableStateFlow(sharedPreferences.getInt("standard_wait_time_seconds", 10))
    val standardWaitTimeSeconds: Flow<Int> = _standardWaitTimeSeconds

    fun setStandardWaitTimeSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(5) // min 5s
        sharedPreferences.edit().putInt("standard_wait_time_seconds", safeSeconds).apply()
        _standardWaitTimeSeconds.value = safeSeconds
    }

    // Quiz Mode
    private val _isQuizModeEnabled = MutableStateFlow(sharedPreferences.getBoolean("quiz_mode_enabled", false))
    val isQuizModeEnabled: Flow<Boolean> = _isQuizModeEnabled

    fun toggleQuizMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("quiz_mode_enabled", enabled).apply()
        _isQuizModeEnabled.value = enabled
    }
}
