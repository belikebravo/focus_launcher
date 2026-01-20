package com.example.focuslauncher.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteApp(
    @PrimaryKey val packageName: String
)
