package com.example.focuslauncher.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(app: FavoriteApp)

    @Query("DELETE FROM favorites WHERE packageName = :packageName")
    suspend fun removeFavorite(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE packageName = :packageName)")
    suspend fun isFavorite(packageName: String): Boolean
}
