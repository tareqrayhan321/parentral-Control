package com.example.core.model

import java.time.DayOfWeek

/**
 * A scheduled restriction period (e.g., Bedtime, Homework hours).
 * Can cross midnight (e.g. 22:00 -> 07:00).
 */
data class Schedule(
    val id: String,
    val name: String,
    val startTime: TimeOfDay,
    val endTime: TimeOfDay,
    val activeDays: Set<DayOfWeek>,
    val enabled: Boolean = true,
    val blockedPackages: Set<String> = emptySet()
) {
    /**
     * Returns true if the schedule spans across midnight (e.g. 22:00 -> 07:00).
     */
    val crossesMidnight: Boolean
        get() = startTime.toTotalMinutes() > endTime.toTotalMinutes()

    /**
     * Checks whether this schedule is in effect at a given time and day of week.
     *
     * @param currentTime Current time of day (hour and minute).
     * @param currentDay Current day of week.
     */
    fun isActiveAt(currentTime: TimeOfDay, currentDay: DayOfWeek): Boolean {
        if (!enabled || activeDays.isEmpty()) return false

        val currentMin = currentTime.toTotalMinutes()
        val startMin = startTime.toTotalMinutes()
        val endMin = endTime.toTotalMinutes()

        return if (!crossesMidnight) {
            // Same day schedule: e.g. 09:00 -> 17:00
            currentDay in activeDays && currentMin >= startMin && currentMin < endMin
        } else {
            // Midnight-crossing schedule: e.g. 22:00 -> 07:00
            // Evening portion (from 22:00 to 23:59): belongs to today's active schedule
            val isEveningPortion = currentDay in activeDays && currentMin >= startMin

            // Morning portion (from 00:00 to 06:59): belongs to yesterday's active schedule
            val yesterday = currentDay.minus(1)
            val isMorningPortion = yesterday in activeDays && currentMin < endMin

            isEveningPortion || isMorningPortion
        }
    }
}
