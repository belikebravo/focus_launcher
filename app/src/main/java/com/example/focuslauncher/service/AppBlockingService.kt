package com.example.focuslauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.focuslauncher.R
import com.example.focuslauncher.data.local.AppDatabase
import com.example.focuslauncher.data.local.BlockedAppDao
import com.example.focuslauncher.ui.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppBlockingService : Service() {

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = AppDatabase.getDatabase(this)
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("FocusService", "Service Started")
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            val favoriteDao = database.favoriteDao()
            // Cache favorites to reduce DB hits, update periodically if needed
            // For simplicity in this loop, we fetch fresh or assume favorites don't change rapidly during deep focus
            
            while (isActive) {
                checkForegroundApp()
                delay(1000) // Check every 1 second
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60) // Look back 1 minute
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        if (stats.isNullOrEmpty()) return

        val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
        val topPackage = sortedStats.firstOrNull()?.packageName ?: return
        
        // Don't block ourselves or system UI
        if (topPackage == packageName || topPackage == "com.android.systemui") return

        // 1. Check Manual Block (Always enforced)
        val isManuallyBlocked = database.blockedAppDao().isBlocked(topPackage)
        if (isManuallyBlocked) {
            blockApp(topPackage, 10L)
            return
        }

        // 2. Check Deep Focus (Time based)
        // We need to fetch Deep Focus state. For simplicity, we can read SharedPrefs directly or check Favorite status if we assume Deep Focus is ON.
        // However, the Service is currently only started WHEN Deep Focus is ON by the UI. 
        // We need to change this if we want PERMANENT blocking.
        // But for now, let's assume if the service is running, we should check blocking rules.
        
        // Wait, the user asked for "even if focus mode is off". 
        // This means the Service should be running ALWAYS if we want to catch manual blocks.
        // But currently HomeScreen only starts it during Deep Focus.
        // I will update HomeScreen later to always start service if there are blocked apps.
        
        // For now, logic-wise:
        val isFavorite = database.favoriteDao().isFavorite(topPackage)
        
        // If Deep Focus is active (we can check SharedPrefs or assume active if this code is running for deep focus loop)
        // To be safe and support "Permanent Block", we should just check if it's blocked.
        // But how do we know if Deep Focus is active here?
        // We can read it from SharedPrefs.
        
        // Correct Preferences File (Must match SettingsRepository)
        val prefs = getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
        val deepFocusEndTime = prefs.getLong("deep_focus_end_time", 0L)
        val isFocusModeEnabled = prefs.getBoolean("focus_mode", false)
        val currentTime = System.currentTimeMillis()
        
        // Strict Apps List (Broad matching)
        val strictKeywords = listOf(
            "instagram",
            "facebook",
            "snapchat",
            "youtube"
        )
        val isStrictApp = strictKeywords.any { topPackage.contains(it, ignoreCase = true) }
        
        var shouldBlock = false
        var waitTime = 10L

        if (isStrictApp) {
             // ALWAYS block strict apps (regardless of switch)
             shouldBlock = true
             
             // Read Configured Wait Time (from same prefs file)
             val configuredMinutes = prefs.getInt("focus_wait_time_minutes", 5)
             waitTime = configuredMinutes * 60L
             
             android.util.Log.d("FocusService", "Strict Blocking: $topPackage for ${waitTime}s")
             
        } else if ((isFocusModeEnabled || currentTime < deepFocusEndTime) && !isFavorite) {
            // Block other apps only if Focus Mode is ON or Deep Focus Timer is running
            shouldBlock = true
            waitTime = 10L // Default friction for regular apps
        }
        
        if (shouldBlock) {
             blockApp(topPackage, waitTime)
        }
    }
    
    private fun blockApp(packageName: String, seconds: Long) {
        val intent = Intent(this, com.example.focuslauncher.ui.LearningOverlayActivity::class.java).apply {
            putExtra("TARGET_PACKAGE", packageName)
            putExtra("REQUIRED_TIME_SECONDS", seconds)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "blocking_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Focus Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Deep Focus Active")
            .setContentText("Monitoring app usage...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
