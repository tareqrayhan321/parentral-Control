package com.example.core.model

import java.util.Locale

/**
 * Immutable representation of a time of day (hour and minute).
 */
data class TimeOfDay(
    val hour: Int,
    val minute: Int
) : Comparable<TimeOfDay> {

    init {
        require(hour in 0..23) { "Hour must be between 0 and 23. Received: $hour" }
        require(minute in 0..59) { "Minute must be between 0 and 59. Received: $minute" }
    }

    /** Total minutes elapsed from 00:00 (midnight). Range: 0..1439. */
    fun toTotalMinutes(): Int = hour * 60 + minute

    /** Formatted 24-hour string (e.g. "22:30"). */
    fun to24HourString(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    /** Formatted 12-hour AM/PM string (e.g. "10:30 PM"). */
    fun to12HourString(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
        return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
    }

    override fun compareTo(other: TimeOfDay): Int =
        toTotalMinutes().compareTo(other.toTotalMinutes())

    companion object {
        fun now(): TimeOfDay {
            val localTime = java.time.LocalTime.now()
            return TimeOfDay(localTime.hour, localTime.minute)
        }

        fun fromTotalMinutes(totalMinutes: Int): TimeOfDay {
            val normalized = ((totalMinutes % 1440) + 1440) % 1440
            return TimeOfDay(normalized / 60, normalized % 60)
        }
    }
}
