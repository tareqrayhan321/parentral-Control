package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.AppPolicy
import com.example.core.model.RestrictionMode

@Entity(tableName = "app_policy")
data class AppPolicyEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val mode: String, // "ALLOWED", "BLOCKED", "LIMITED"
    val dailyLimitMinutes: Int?,
    val scheduleIdsJoined: String, // Comma-separated schedule IDs
    val isSuspended: Boolean = false,
    val enabled: Boolean = true,
    val updatedAt: Long
) {
    fun toDomain(): AppPolicy {
        val scheduleIds = if (scheduleIdsJoined.isBlank()) {
            emptyList()
        } else {
            scheduleIdsJoined.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val restrictionMode = try {
            RestrictionMode.valueOf(mode)
        } catch (_: Exception) {
            RestrictionMode.ALLOWED
        }
        return AppPolicy(
            packageName = packageName,
            displayName = displayName,
            mode = restrictionMode,
            dailyLimitMinutes = dailyLimitMinutes,
            scheduleIds = scheduleIds,
            enabled = enabled
        )
    }

    companion object {
        fun fromDomain(domain: AppPolicy, isSuspended: Boolean = false): AppPolicyEntity {
            return AppPolicyEntity(
                packageName = domain.packageName,
                displayName = domain.displayName,
                mode = domain.mode.name,
                dailyLimitMinutes = domain.dailyLimitMinutes,
                scheduleIdsJoined = domain.scheduleIds.joinToString(","),
                isSuspended = isSuspended,
                enabled = domain.enabled,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
