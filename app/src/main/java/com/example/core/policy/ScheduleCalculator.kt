package com.example.core.policy

import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Calculates transition points for schedules so that AlarmManager can wake up
 * precisely when a schedule begins or ends, minimizing battery consumption.
 */
object ScheduleCalculator {

    /**
     * Calculates the nearest upcoming transition (start or end) among the given schedules
     * starting strictly after [fromDateTime].
     *
     * Scans from yesterday (to catch the end of a midnight-crossing schedule that began
     * yesterday) up to 7 days in the future.
     * Returns null if no schedules are enabled or have active days.
     */
    fun findNextTransition(
        schedules: Collection<Schedule>,
        fromDateTime: ZonedDateTime
    ): ZonedDateTime? {
        val enabledSchedules = schedules.filter { it.enabled && it.activeDays.isNotEmpty() }
        if (enabledSchedules.isEmpty()) return null

        val zone = fromDateTime.zone
        var nearestTransition: ZonedDateTime? = null

        // Start at yesterday: a schedule such as 22:00 -> 07:00 that started yesterday
        // ends today at 07:00, and that end transition must not be missed.
        for (dayOffset in -1..7) {
            val targetDate = fromDateTime.toLocalDate().plusDays(dayOffset.toLong())

            for (schedule in enabledSchedules) {
                // Check Schedule Start Transition
                if (schedule.activeDays.contains(targetDate.dayOfWeek)) {
                    val startZdt = ZonedDateTime.of(
                        targetDate,
                        LocalTime.of(schedule.startTime.hour, schedule.startTime.minute),
                        zone
                    )
                    if (startZdt.isAfter(fromDateTime)) {
                        if (nearestTransition == null || startZdt.isBefore(nearestTransition)) {
                            nearestTransition = startZdt
                        }
                    }
                }

                // Check Schedule End Transition
                val endDay = if (schedule.crossesMidnight) {
                    targetDate.plusDays(1)
                } else {
                    targetDate
                }

                if (schedule.activeDays.contains(targetDate.dayOfWeek)) {
                    val endZdt = ZonedDateTime.of(
                        endDay,
                        LocalTime.of(schedule.endTime.hour, schedule.endTime.minute),
                        zone
                    )
                    if (endZdt.isAfter(fromDateTime)) {
                        if (nearestTransition == null || endZdt.isBefore(nearestTransition)) {
                            nearestTransition = endZdt
                        }
                    }
                }
            }

            // Optimization: if we already found a transition in an earlier day, we don't need to look beyond
            if (nearestTransition != null && nearestTransition.toLocalDate().isBefore(targetDate)) {
                break
            }
        }

        return nearestTransition
    }
}
