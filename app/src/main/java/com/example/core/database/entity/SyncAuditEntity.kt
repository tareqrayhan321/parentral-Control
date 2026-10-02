package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_audit")
data class SyncAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val event: String,
    val policyVersion: Int,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
