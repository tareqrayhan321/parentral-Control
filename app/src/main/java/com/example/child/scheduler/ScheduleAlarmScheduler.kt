package com.example.child.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.Schedule
import com.example.core.policy.ScheduleCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

object ScheduleAlarmScheduler {
    private const val TAG = "ScheduleAlarmScheduler"
    private const val SCHEDULE_REQUEST_CODE = 9002

    /**
     * Finds the nearest upcoming schedule transition across all active schedules
     * and registers an exact wake-up alarm with AlarmManager.
     */
    suspend fun scheduleNextTransition(context: Context) = withContext(Dispatchers.IO) {
        val database = ChildDatabase.getInstance(context)
        val repository = RoomPolicyRepository(database)
        val policy = repository.getCurrentPolicy()

        scheduleNextTransitionForSchedules(context, policy.schedules.values)
    }

    /**
     * Internal scheduling method that accepts schedules directly (useful for tests and sync).
     */
    fun scheduleNextTransitionForSchedules(
        context: Context,
        schedules: Collection<Schedule>
    ): ZonedDateTime? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return null

        val now = ZonedDateTime.now()
        val nextTransition = ScheduleCalculator.findNextTransition(schedules, now)

        if (nextTransition == null) {
            Log.i(TAG, "No upcoming schedule transitions found. Cancelling any pending schedule alarm.")
            cancelScheduleAlarm(context)
            return null
        }

        val triggerAtMillis = nextTransition.toInstant().toEpochMilli()
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRANSITION
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SCHEDULE_REQUEST_CODE,
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.i(TAG, "Scheduled exact schedule transition alarm for: $nextTransition")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission missing; scheduled standard transition alarm for: $nextTransition")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled exact schedule transition alarm for: $nextTransition")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule transition alarm", e)
        }

        return nextTransition
    }

    /**
     * Cancels any pending schedule transition alarm.
     */
    fun cancelScheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRANSITION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SCHEDULE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled pending schedule alarm.")
        }
    }
}
