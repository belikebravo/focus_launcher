package com.example.focuslauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.example.focuslauncher.data.local.AppDatabase
import com.example.focuslauncher.data.local.BlockedApp
import com.example.focuslauncher.data.local.FavoriteApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AppRepository(val context: Context) {

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val favoriteDao by lazy { db.favoriteDao() }
    private val blockedDao by lazy { db.blockedAppDao() }
    
    val contextPackageName: String = context.packageName

    fun getApps(): Flow<List<AppModel>> = flow {
        emit(fetchInstalledApps())
    }.combine(favoriteDao.getAllFavorites()) { installed, favorites ->
        val favoriteSet = favorites.map { it.packageName }.toSet()
        installed to favoriteSet
    }.combine(blockedDao.getAllBlockedApps()) { (installed, favoriteSet), blocked ->
        val blockedSet = blocked.map { it.packageName }.toSet()
        installed.map { app ->
            app.copy(
                isFavorite = favoriteSet.contains(app.packageName),
                isBlocked = blockedSet.contains(app.packageName)
            )
        }.sortedWith(
            compareByDescending<AppModel> { it.isFavorite } // Favorites first
                .thenBy { it.label.lowercase() }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun toggleFavorite(app: AppModel) {
        withContext(Dispatchers.IO) {
            if (app.isFavorite) {
                favoriteDao.removeFavorite(app.packageName)
            } else {
                favoriteDao.addFavorite(FavoriteApp(app.packageName))
            }
        }
    }

    suspend fun toggleBlocked(app: AppModel) {
        withContext(Dispatchers.IO) {
            if (app.isBlocked) {
                blockedDao.delete(BlockedApp(app.packageName))
            } else {
                blockedDao.insert(BlockedApp(app.packageName))
            }
        }
    }

    private val launcherApps by lazy { context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps }
    private val userManager by lazy { context.getSystemService(Context.USER_SERVICE) as android.os.UserManager }

    private suspend fun fetchInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<AppModel>()
        val profiles = launcherApps.profiles

        // Fetch Usage Stats for ALL profiles? 
        // UsageStatsManager in strict mode only returns for current user usually unless system app.
        // But for a launcher, we mostly display.
        // We will try to fetch stats for current user first.
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // 24 hours
        val usageStatsMap = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        ).associateBy { it.packageName }
        
        for (userHandle in profiles) {
            val activities = launcherApps.getActivityList(null, userHandle)
            for (activity in activities) {
                val packageName = activity.applicationInfo.packageName
                // Avoid duplicates (if any) or handle same package in different profile?
                // For a launcher, usually we distinguish them (e.g. with a briefcase icon).
                // For now, we just list them.
                
                val stats = usageStatsMap[packageName] // Note: usage stats might be limited to current user
                val isWorkProfile = !android.os.Process.myUserHandle().equals(userHandle)
                
                appList.add(AppModel(
                    label = activity.label.toString() + (if (isWorkProfile) " (Work)" else ""),
                    packageName = packageName,
                    isWork = isWorkProfile,
                    isFavorite = false,
                    isBlocked = false, // Will be updated by flow combine
                    usageDuration = stats?.totalTimeInForeground ?: 0L,
                    lastUsedTimestamp = stats?.lastTimeUsed ?: 0L
                ))
            }
        }
        // Sync Work Apps to SharedPreferences for AppBlockingService
        val workPackages = appList.filter { it.isWork }.map { it.packageName }.toSet()
        val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("cached_work_packages", workPackages).apply()
        
        appList
    }
}
