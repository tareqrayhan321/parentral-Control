package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.DailyUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {

    @Query("SELECT * FROM daily_usage WHERE dateString = :dateString")
    fun observeDailyUsageForDate(dateString: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE dateString = :dateString")
    suspend fun getDailyUsageForDate(dateString: String): List<DailyUsageEntity>

    @Query("SELECT usedMinutes FROM daily_usage WHERE packageName = :packageName AND dateString = :dateString LIMIT 1")
    suspend fun getUsageForPackage(packageName: String, dateString: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordUsage(usage: DailyUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordUsageAll(usages: List<DailyUsageEntity>)

    @Query("DELETE FROM daily_usage WHERE dateString < :olderThanDateString")
    suspend fun purgeOldUsage(olderThanDateString: String)

    @Query("DELETE FROM daily_usage")
    suspend fun clearAll()
}
