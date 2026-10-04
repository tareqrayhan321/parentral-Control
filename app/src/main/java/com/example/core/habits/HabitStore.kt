package com.example.core.habits

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

/** Local persistence for habits (SharedPreferences, JSON). */
class HabitStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("habit_tracker", Context.MODE_PRIVATE)

    fun load(): List<Habit> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { habitFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<Habit>): List<Habit> {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return list
    }

    fun add(habit: Habit): List<Habit> = save(load() + habit)

    fun delete(id: String): List<Habit> = save(load().filterNot { it.id == id })

    fun setDone(id: String, date: LocalDate, done: Boolean): List<Habit> = save(
        load().map {
            if (it.id != id) it
            else it.copy(
                completedDates = if (done) it.completedDates + date.toString()
                else it.completedDates - date.toString()
            )
        }
    )

    private companion object {
        const val KEY = "habits_json"
    }
}
