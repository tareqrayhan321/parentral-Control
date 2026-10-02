package com.example.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "daily_usage",
    primaryKeys = ["packageName", "dateString"]
)
data class DailyUsageEntity(
    val packageName: String,
    val dateString: String, // Format: YYYY-MM-DD
    val usedMinutes: Int,
    val lastCalculatedTimestamp: Long
)
