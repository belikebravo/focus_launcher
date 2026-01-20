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

    private suspend fun fetchInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedInfos: List<ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        // Fetch Usage Stats
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // 24 hours
        val usageStatsMap = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        ).associateBy { it.packageName }

        resolvedInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val stats = usageStatsMap[packageName]
            
            AppModel(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = packageName,
                isFavorite = false,
                isBlocked = false,
                usageDuration = stats?.totalTimeInForeground ?: 0L,
                lastUsedTimestamp = stats?.lastTimeUsed ?: 0L
            )
        }
    }
}
