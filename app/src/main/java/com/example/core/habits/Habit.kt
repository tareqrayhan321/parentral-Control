package com.example.core.habits

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class HabitFrequency { DAILY, WEEKLY, MONTHLY }

data class Habit(
    val id: String,
    val title: String,
    val description: String,
    val frequency: HabitFrequency,
    val reminderEnabled: Boolean,
    val hour: Int,
    val minute: Int,
    val iconKey: String,
    /** First day the habit counts; Weekly/Monthly habits repeat on this weekday / day-of-month. */
    val createdAt: LocalDate,
    val completedDates: Set<String>
) {
    fun isDone(date: LocalDate): Boolean = date.toString() in completedDates

    fun isDueOn(date: LocalDate): Boolean {
        if (date.isBefore(createdAt)) return false
        return when (frequency) {
            HabitFrequency.DAILY -> true
            HabitFrequency.WEEKLY -> date.dayOfWeek == createdAt.dayOfWeek
            HabitFrequency.MONTHLY ->
                date.dayOfMonth == minOf(createdAt.dayOfMonth, date.lengthOfMonth())
        }
    }
}

/** Consecutive days (ending today) on which every due habit was completed. */
fun computeStreak(habits: List<Habit>, today: LocalDate): Int {
    if (habits.isEmpty()) return 0
    val earliest = habits.minOf { it.createdAt }
    var streak = 0
    var day = today
    var guard = 0
    while (!day.isBefore(earliest) && guard < 730) {
        val due = habits.filter { it.isDueOn(day) }
        if (due.isNotEmpty()) {
            if (due.all { it.isDone(day) }) streak++ else if (day != today) break
        }
        day = day.minusDays(1)
        guard++
    }
    return streak
}

/** Next alarm time (epoch millis) strictly after [nowMillis], or null when none is due soon. */
fun nextTriggerMillis(habit: Habit, nowMillis: Long): Long? {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
    val time = LocalTime.of(habit.hour, habit.minute)
    for (offset in 0..62L) {
        val date = now.toLocalDate().plusDays(offset)
        if (!habit.isDueOn(date)) continue
        val at = LocalDateTime.of(date, time)
        if (at.isAfter(now)) return at.atZone(zone).toInstant().toEpochMilli()
    }
    return null
}

internal fun Habit.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("description", description)
    put("frequency", frequency.name)
    put("reminder", reminderEnabled)
    put("hour", hour)
    put("minute", minute)
    put("icon", iconKey)
    put("createdAt", createdAt.toString())
    put("done", JSONArray(completedDates.toList()))
}

internal fun habitFromJson(o: JSONObject): Habit {
    val done = mutableSetOf<String>()
    val arr = o.optJSONArray("done")
    if (arr != null) for (i in 0 until arr.length()) done.add(arr.getString(i))
    return Habit(
        id = o.getString("id"),
        title = o.getString("title"),
        description = o.optString("description", ""),
        frequency = runCatching { HabitFrequency.valueOf(o.optString("frequency")) }
            .getOrDefault(HabitFrequency.DAILY),
        reminderEnabled = o.optBoolean("reminder", false),
        hour = o.optInt("hour", 9),
        minute = o.optInt("minute", 0),
        iconKey = o.optString("icon", "star"),
        createdAt = runCatching { LocalDate.parse(o.optString("createdAt")) }
            .getOrDefault(LocalDate.now()),
        completedDates = done
    )
}
