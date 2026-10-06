package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.ChildDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDeviceDao {

    // ORDER BY is deliberate. A phone can hold two rows: the local placeholder written by the first policy
    // apply (empty parentId) and the real paired device. A bare LIMIT 1 returns the OLDEST row, i.e. the
    // placeholder, so sync compared the Firebase uid with "local_child_device" and stopped.
    // Order: real parent-bound rows first, enrolled before unenrolled, most recent first.
    @Query("SELECT * FROM child_device ORDER BY (parentId = '') ASC, (enrollmentStatus = 'ENROLLED') DESC, lastSynchronizedAt DESC, deviceId ASC LIMIT 1")
    fun observeCurrentDevice(): Flow<ChildDeviceEntity?>

    @Query("SELECT * FROM child_device ORDER BY (parentId = '') ASC, (enrollmentStatus = 'ENROLLED') DESC, lastSynchronizedAt DESC, deviceId ASC LIMIT 1")
    suspend fun getCurrentDevice(): ChildDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDevice(device: ChildDeviceEntity)

    @Query("UPDATE child_device SET policyVersion = :version, lastSynchronizedAt = :syncTime WHERE deviceId = :deviceId")
    suspend fun updatePolicyVersion(deviceId: String, version: Int, syncTime: Long)

    @Query("DELETE FROM child_device")
    suspend fun clearDevice()

    /** Keeps the table at ONE row: removes every device row except [deviceId]. */
    @Query("DELETE FROM child_device WHERE deviceId != :deviceId")
    suspend fun deleteOthers(deviceId: String)
}
