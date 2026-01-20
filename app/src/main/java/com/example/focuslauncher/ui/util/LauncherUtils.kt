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
    
    val shoppingApps = setOf("com.amazon.mShop.android.shopping", "com.flipkart.android", "com.myntra.android")
    
    // Knowledge Nugget Logic - Universal Blocking
    // Default 10s for everyone, 5 mins (300s) for Shopping
    
    val requiredTime = if (shoppingApps.contains(app.packageName)) 300L else 10L
    
    val intent = Intent(context, LearningOverlayActivity::class.java).apply {
        putExtra("TARGET_PACKAGE", app.packageName)
        putExtra("REQUIRED_TIME_SECONDS", requiredTime)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
