package com.example.child.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.usage.AndroidUsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Triggered at 00:00:00 local calendar midnight to reset daily usage counters
 * and un-suspend apps whose daily limits were exhausted yesterday.
 */
class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Midnight reached! Resetting daily usage counters.")

        // Reschedule for next midnight
        MidnightResetScheduler.scheduleNextMidnightAlarm(context)

        // Perform asynchronous reset and re-evaluation
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ChildDatabase.getInstance(context)
                val repository = RoomPolicyRepository(db)
                val usageRepo = AndroidUsageRepository(context, repository)

                usageRepo.handleMidnightReset()
                val enforcementManager = com.example.child.enforcement.DefaultPolicyEnforcementManager(context)
                enforcementManager.enforceCurrentPolicy()

                repository.logSyncAudit(
                    event = "MIDNIGHT_RESET",
                    version = repository.getCurrentPolicy().version,
                    details = "Daily usage counters reset and policies re-enforced for new date"
                )
                Log.i(TAG, "Midnight reset completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing midnight reset", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "MidnightResetReceiver"
        const val ACTION_MIDNIGHT_RESET = "com.example.child.ACTION_MIDNIGHT_RESET"
    }
}

object MidnightResetScheduler {
    private const val TAG = "MidnightResetScheduler"
    private const val MIDNIGHT_REQUEST_CODE = 9001

    /**
     * Schedules an exact alarm at 00:00:00 local calendar midnight.
     * When it fires, daily usage is reset and daily limited apps are re-evaluated.
     */
    fun scheduleNextMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        val zone = ZoneId.systemDefault()
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)

        val triggerAtMillis = nextMidnight.toInstant().toEpochMilli()

        val intent = Intent(context, MidnightResetReceiver::class.java).apply {
            action = MidnightResetReceiver.ACTION_MIDNIGHT_RESET
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
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
                    Log.i(TAG, "Scheduled exact midnight reset for: $nextMidnight")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission missing; scheduled standard alarm for: $nextMidnight")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled exact midnight reset for: $nextMidnight")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule midnight alarm", e)
        }
    }

    /**
     * Cancels any pending midnight reset alarm.
     */
    fun cancelMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val intent = Intent(context, MidnightResetReceiver::class.java).apply {
            action = MidnightResetReceiver.ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
