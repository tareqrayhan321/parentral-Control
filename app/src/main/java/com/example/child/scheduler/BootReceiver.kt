package com.example.child.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles device boot events to re-arm exact schedule alarms and re-apply policy restrictions.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Log.i(TAG, "Device boot completed. Re-arming schedule alarms and re-evaluating policy.")

            // Re-arm habit reminder alarms (independent of child-mode setup)
            try {
                com.example.core.habits.HabitAlarmScheduler.rescheduleAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error re-arming habit alarms", e)
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = ChildDatabase.getInstance(context)
                    val repository = RoomPolicyRepository(db)

                    val enforcementManager = com.example.child.enforcement.DefaultPolicyEnforcementManager(context)
                    enforcementManager.initializeDeviceEnforcement()

                    // Resume monitoring + backend sync after reboot
                    if (repository.getDevice() != null) {
                        com.example.child.protection.DeviceProtectionManager.startProtectionService(context)
                    }

                    Log.i(TAG, "Boot initialization and policy enforcement complete.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling boot completed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
