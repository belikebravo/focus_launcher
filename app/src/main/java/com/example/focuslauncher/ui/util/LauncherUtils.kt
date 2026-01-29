package com.example.focuslauncher.ui.util

import android.content.Context
import android.widget.Toast
import com.example.focuslauncher.data.AppModel

import android.content.Intent
import com.example.focuslauncher.data.SettingsRepository
import com.example.focuslauncher.ui.LearningOverlayActivity

fun launchApp(context: Context, app: AppModel) {
    if (app.isBlocked) {
        Toast.makeText(context, "App is blocked for focus", Toast.LENGTH_SHORT).show()
        return
    }

    val settingsRepo = SettingsRepository(context)
    if (settingsRepo.isAppExcluded(app.packageName) || app.packageName == context.packageName) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
        return
    }
    
    // 0. Exclusions (Camera, Work, etc)
    // Hardcoded Camera/Teams List
    val exemptPackages = listOf(
        "com.nothing.camera", 
        "com.android.camera", 
        "com.google.android.GoogleCamera", 
        "com.samsung.android.camera", 
        "com.oneplus.camera",
        "com.apple.camera",
        "com.microsoft.teams"
    )
    
    val isCamera = exemptPackages.contains(app.packageName) || app.packageName.contains("camera", ignoreCase = true)
    val isYouTubeMusic = app.packageName == "com.google.android.apps.youtube.music"
    
    // 1. Explicit Allow List (Overrides everything, including Strict)
    if (isCamera || isYouTubeMusic) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
        return
    }

    // Strict Check (YouTube/Social)
    val strictKeywords = listOf("instagram", "facebook", "snapchat", "youtube")
    val isStrict = strictKeywords.any { app.packageName.contains(it, ignoreCase = true) } || app.packageName == "com.google.android.youtube"
    
    // 2. Strict Blocking
    if (isStrict) {
        // Fall through to blocking logic below.
        // We do NOT check isWork here, because Personal YouTube > Work Profile Exemption per user request.
    } else {
        // 3. Work Profile Exemption (For non-strict apps)
        if (app.isWork) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
            return
        }
    }

    val shoppingApps = setOf("com.amazon.mShop.android.shopping", "com.flipkart.android", "com.myntra.android")
    
    // Knowledge Nugget Logic - Universal Blocking
    val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
    val standardWaitTime = prefs.getInt("standard_wait_time_seconds", 10).toLong()
    val strictWaitTimeMinutes = prefs.getInt("focus_wait_time_minutes", 5)
    
    var requiredTime = standardWaitTime
    if (shoppingApps.contains(app.packageName) || isStrict) {
         requiredTime = strictWaitTimeMinutes * 60L
    }
    
    val intent = Intent(context, LearningOverlayActivity::class.java).apply {
        putExtra("TARGET_PACKAGE", app.packageName)
        putExtra("REQUIRED_TIME_SECONDS", requiredTime)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
