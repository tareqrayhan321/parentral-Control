package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.ChildDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDeviceDao {

    @Query("SELECT * FROM child_device LIMIT 1")
    fun observeCurrentDevice(): Flow<ChildDeviceEntity?>

    @Query("SELECT * FROM child_device LIMIT 1")
    suspend fun getCurrentDevice(): ChildDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDevice(device: ChildDeviceEntity)

    @Query("UPDATE child_device SET policyVersion = :version, lastSynchronizedAt = :syncTime WHERE deviceId = :deviceId")
    suspend fun updatePolicyVersion(deviceId: String, version: Int, syncTime: Long)

    @Query("DELETE FROM child_device")
    suspend fun clearDevice()
}
