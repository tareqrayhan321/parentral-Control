package com.example.core.habits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Schedules the exact reminder alarm for a habit; the receiver re-arms the next occurrence. */
object HabitAlarmScheduler {
    const val ACTION_ALARM = "com.example.habit.ALARM"
    const val EXTRA_HABIT_ID = "habit_id"

    fun schedule(context: Context, habit: Habit) {
        cancel(context, habit.id)
        if (!habit.reminderEnabled) return
        val at = nextTriggerMillis(habit, System.currentTimeMillis()) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, habit.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, habitId))
    }

    fun rescheduleAll(context: Context) {
        HabitStore(context).load().forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, HabitAlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(Uri.parse("habit://$habitId"))
            .putExtra(EXTRA_HABIT_ID, habitId)
        return PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
