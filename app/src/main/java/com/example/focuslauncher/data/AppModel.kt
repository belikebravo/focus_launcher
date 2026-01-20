package com.example.focuslauncher.data

import android.graphics.drawable.Drawable

data class AppModel(
    val label: String,
    val packageName: String,
    // val icon: android.graphics.drawable.Drawable?, // Removed
    val isSystemApp: Boolean = false,
    var isFavorite: Boolean = false,
    var isBlocked: Boolean = false,
    var usageDuration: Long = 0L,
    var lastUsedTimestamp: Long = 0L
)
