package com.example.focuslauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
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

    private val launcherApps by lazy { getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps }
    private val packageManagerCompat by lazy { applicationContext.packageManager }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = AppDatabase.getDatabase(this)
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("FocusService", "Service Started")
        // DEBUG: Toast to confirm start
        // android.widget.Toast.makeText(this, "Focus Svc Started", android.widget.Toast.LENGTH_SHORT).show()
        
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
            // Cache favorites to reduce DB hits, update periodically if needed
            // For simplicity in this loop, we fetch fresh or assume favorites don't change rapidly during deep focus
            
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    android.util.Log.e("FocusService", "Error in loop", e)
                }
                delay(1000) // Check every 1 second
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60) // Look back 1 hour to ensure we catch most recent stats
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        if (stats.isNullOrEmpty()) return

        val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
        val topPackage = sortedStats.firstOrNull()?.packageName ?: return
        
        android.util.Log.d("FocusService", "Checking package: $topPackage")
        
        // 0. EXPLICIT EXCLUSIONS (High Priority)
        // BLIND UNBLOCK for debugging - circumventing all complex logic
        val debugExemptions = listOf("com.nothing.camera", "com.microsoft.teams")
        if (debugExemptions.contains(topPackage.trim())) {
             // DEBUG: Toast
             // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Blind Exempt: $topPackage", android.widget.Toast.LENGTH_SHORT).show() }
             return
        }
        
        // Don't block ourselves or system UI
        if (topPackage == packageName || topPackage == "com.android.systemui") return

        if (isCameraApp(topPackage)) {
            // DEBUG: Toast
            // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Cam Allowed", android.widget.Toast.LENGTH_SHORT).show() }
            return
        }

        if (isYouTubeMusic(topPackage)) {
            return
        }
        
        // 1. STRICT APPS (Social/YouTube) - HIGHEST PRIORITY BLOCK
        // These blocked regardless of Focus Mode or Work Profile status (unless typically work apps, but YouTube isn't)
        // Strict Apps List
        val strictKeywords = listOf(
            "instagram",
            "facebook",
            "snapchat",
            "youtube"
        )
        val isStrictApp = strictKeywords.any { topPackage.contains(it, ignoreCase = true) } 
                          || topPackage == "com.google.android.youtube"

        if (isStrictApp) {
             // Correct Preferences File
             val prefs = getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
             val configuredMinutes = prefs.getInt("focus_wait_time_minutes", 5)
             val waitTime = configuredMinutes * 60L
             
             // DEBUG: Toast
             // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Strict Block: $topPackage", android.widget.Toast.LENGTH_SHORT).show() }
             
             blockApp(packageName = topPackage, seconds = waitTime)
             return // STOP HERE
        }

        // 2. WORK PROFILE EXEMPTION (For non-strict apps)
        if (isWorkProfileApp(topPackage)) {
            // DEBUG: Toast
            // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Work Allowed: $topPackage", android.widget.Toast.LENGTH_SHORT).show() }
            return
        }

        // 3. NORMAL APPS (Manual Block OR Focus Mode)
        var shouldBlock = false
        // Read Standard Friction from Settings
        val prefs = getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
        val standardWaitTime = prefs.getInt("standard_wait_time_seconds", 10).toLong()
        var waitTime = standardWaitTime

        val deepFocusEndTime = prefs.getLong("deep_focus_end_time", 0L)
        val isFocusModeEnabled = prefs.getBoolean("focus_mode", false)
        val currentTime = System.currentTimeMillis()
        
        // Fetch favorite status
        val isFavorite = database.favoriteDao().isFavorite(topPackage)
        
        // Manual Block
        val isManuallyBlocked = database.blockedAppDao().isBlocked(topPackage)
        
        if (isManuallyBlocked) {
            shouldBlock = true
        } 
        else if ((isFocusModeEnabled || currentTime < deepFocusEndTime) && !isFavorite) {
            shouldBlock = true
        }
        
        if (shouldBlock) {
             blockApp(packageName = topPackage, seconds = waitTime)
        }
    }

    private fun isYouTubeMusic(packageName: String): Boolean {
        return packageName == "com.google.android.apps.youtube.music"
    }

    private fun isCameraApp(packageName: String): Boolean {
        // 1. Check for specific camera intent capability
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolveInfos = packageManagerCompat.queryIntentActivities(intent, 0)
        
        for (info in resolveInfos) {
            if (info.activityInfo.packageName == packageName) {
                return true
            }
        }
        // 2. Fallback heuristic & Hardcoded Whitelist
        val exemptPackages = listOf(
            "com.nothing.camera", 
            "com.android.camera", 
            "com.google.android.GoogleCamera", 
            "com.samsung.android.camera", 
            "com.oneplus.camera",
            "com.apple.camera",
            "com.microsoft.teams" // Added per user request as workaround
        )
        if (exemptPackages.contains(packageName)) {
             // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Exempt List: $packageName", android.widget.Toast.LENGTH_SHORT).show() }
             return true
        }
        
        if (packageName.contains("camera", ignoreCase = true)) {
            return true
        }
        
        // DEBUG: Why did it fail?
        if (packageName == "com.nothing.camera") {
             scope.launch(Dispatchers.Main) { 
                 android.widget.Toast.makeText(this@AppBlockingService, "FATAL: Code says False for Nothing Cam?", android.widget.Toast.LENGTH_LONG).show() 
             }
        }
        return false
    }

    private fun isWorkProfileApp(packageName: String): Boolean {
        // Strategy: Check the cache populated by the Launcher (AppRepository)
        return try {
            val prefs = getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
            val workPackages = prefs.getStringSet("cached_work_packages", emptySet()) ?: emptySet()
            
            // DEBUG: Toast cache size (Throttle this? No, user needs to see it once)
            // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Work Cache: ${workPackages.size}", android.widget.Toast.LENGTH_SHORT).show() }
            
            val isWork = workPackages.contains(packageName)
            if (isWork) {
                 android.util.Log.d("FocusService", "Exempting Work App (Cached): $packageName")
                 // scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@AppBlockingService, "Work Exempt: $packageName", android.widget.Toast.LENGTH_SHORT).show() }
            }
            isWork
        } catch (e: Exception) {
            android.util.Log.e("FocusService", "Work Cache Check Failed", e)
            false
        }
    }
    
    private fun blockApp(packageName: String, seconds: Long) {
        // DEBUG: Toast
        // scope.launch(Dispatchers.Main) {
        //      android.widget.Toast.makeText(this@AppBlockingService, "Blocking: $packageName", android.widget.Toast.LENGTH_SHORT).show()
        // }
        
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
