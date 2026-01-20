package com.example.focuslauncher.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedApp: BlockedApp)

    @Delete
    suspend fun delete(blockedApp: BlockedApp)
    
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName)")
    suspend fun isBlocked(packageName: String): Boolean
}
