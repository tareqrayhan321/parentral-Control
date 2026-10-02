package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import java.time.DayOfWeek

@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val activeDaysJoined: String, // Comma-separated enum names e.g. "MONDAY,TUESDAY"
    val enabled: Boolean = true,
    val blockedPackagesJoined: String = "" // Comma-separated package names, empty = all apps
) {
    fun toDomain(): Schedule {
        val days = if (activeDaysJoined.isBlank()) {
            emptySet()
        } else {
            activeDaysJoined.split(",")
                .map { it.trim() }
                .mapNotNull { dayName ->
                    try {
                        DayOfWeek.valueOf(dayName)
                    } catch (_: Exception) {
                        null
                    }
                }.toSet()
        }

        val packages = if (blockedPackagesJoined.isBlank()) {
            emptySet()
        } else {
            blockedPackagesJoined.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        return Schedule(
            id = id,
            name = name,
            startTime = TimeOfDay(startHour, startMinute),
            endTime = TimeOfDay(endHour, endMinute),
            activeDays = days,
            enabled = enabled,
            blockedPackages = packages
        )
    }

    companion object {
        fun fromDomain(domain: Schedule): ScheduleEntity {
            return ScheduleEntity(
                id = domain.id,
                name = domain.name,
                startHour = domain.startTime.hour,
                startMinute = domain.startTime.minute,
                endHour = domain.endTime.hour,
                endMinute = domain.endTime.minute,
                activeDaysJoined = domain.activeDays.joinToString(",") { it.name },
                enabled = domain.enabled,
                blockedPackagesJoined = domain.blockedPackages.joinToString(",")
            )
        }
    }
}
