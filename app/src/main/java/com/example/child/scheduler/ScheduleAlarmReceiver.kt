package com.example.child.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.TimeOfDay
import com.example.core.policy.DefaultPolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Triggered by AlarmManager when an exact schedule transition arrives (e.g. Bedtime starts or ends).
 * Reschedules the next transition and enforces the updated state.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Exact schedule transition alarm fired.")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enforcementManager = com.example.child.enforcement.DefaultPolicyEnforcementManager(context)
                enforcementManager.enforceCurrentPolicy()

                // Arm the next upcoming schedule transition
                ScheduleAlarmScheduler.scheduleNextTransition(context)

                Log.i(TAG, "Schedule transition re-evaluation complete.")
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating schedule transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleAlarmReceiver"
        const val ACTION_SCHEDULE_TRANSITION = "com.example.child.ACTION_SCHEDULE_TRANSITION"
    }
}
