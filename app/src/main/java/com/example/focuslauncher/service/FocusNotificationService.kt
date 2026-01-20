package com.example.focuslauncher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FocusNotificationService : NotificationListenerService() {

    private var isFocusEnabled = false
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "focus_mode") {
            isFocusEnabled = sharedPreferences.getBoolean("focus_mode", false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("focus_settings", android.content.Context.MODE_PRIVATE)
        isFocusEnabled = prefs.getBoolean("focus_mode", false)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences("focus_settings", android.content.Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        if (isFocusEnabled) {
            // Don't block our own notifications
            if (sbn.packageName != packageName) {
                // Cancel the notification
                cancelNotification(sbn.key)
                Log.d("FocusService", "Blocked notification from ${sbn.packageName}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
