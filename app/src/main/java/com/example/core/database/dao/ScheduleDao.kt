package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule ORDER BY name ASC")
    fun observeAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule")
    suspend fun getAllSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedule WHERE id NOT IN (:keepIds)")
    suspend fun deleteOrphanedSchedules(keepIds: List<String>)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun deleteScheduleById(id: String)

    @Query("DELETE FROM schedule")
    suspend fun clearAll()
}
