package com.example.core.model

/**
 * Complete synchronized policy bundle for a managed device.
 */
data class Policy(
    val version: Int,
    val updatedAtEpochMs: Long,
    val apps: Map<String, AppPolicy> = emptyMap(),
    val schedules: Map<String, Schedule> = emptyMap()
) {
    companion object {
        fun empty(): Policy = Policy(
            version = 0,
            updatedAtEpochMs = 0L,
            apps = emptyMap(),
            schedules = emptyMap()
        )
    }
}
