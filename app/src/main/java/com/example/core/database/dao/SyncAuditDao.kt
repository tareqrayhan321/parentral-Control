package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.core.database.entity.SyncAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncAuditDao {

    @Query("SELECT * FROM sync_audit ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentAudits(): Flow<List<SyncAuditEntity>>

    @Insert
    suspend fun insertAudit(audit: SyncAuditEntity)

    @Query("DELETE FROM sync_audit WHERE id NOT IN (SELECT id FROM sync_audit ORDER BY timestamp DESC LIMIT 200)")
    suspend fun trimOldAudits()
}
