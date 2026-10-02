package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_device")
data class ChildDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val parentId: String,
    val deviceName: String,
    val enrollmentStatus: String,
    val policyVersion: Int,
    val lastSynchronizedAt: Long
)
