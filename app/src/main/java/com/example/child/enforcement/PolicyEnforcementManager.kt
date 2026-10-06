package com.example.child.enforcement

import android.content.Context
import android.util.Log
import com.example.child.admin.AndroidDeviceOwnerManager
import com.example.child.admin.DeviceOwnerManager
import com.example.child.scheduler.MidnightResetScheduler
import com.example.child.scheduler.ScheduleAlarmScheduler
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.RestrictionDecision
import com.example.core.model.TimeOfDay
import com.example.core.policy.ControlsStore
import com.example.core.policy.DefaultPolicyEngine
import com.example.core.policy.PolicyEngine
import com.example.core.usage.AndroidUsageRepository
import com.example.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime

interface PolicyEnforcementManager {
    /**
     * Evaluates current policy against local time, active schedules, and today's usage,
     * and synchronizes package suspension states via DevicePolicyManager.
     *
     * @return Map of packageName to its RestrictionDecision.
     */
    suspend fun enforceCurrentPolicy(): Map<String, RestrictionDecision>

    /**
     * Initializes device protections (uninstall block, tamper restrictions)
     * and sets up recurring midnight and schedule transition alarms.
     */
    suspend fun initializeDeviceEnforcement()
}

class DefaultPolicyEnforcementManager(
    private val context: Context,
    private val policyRepository: PolicyRepository = RoomPolicyRepository(ChildDatabase.getInstance(context)),
    private val usageRepository: UsageRepository = AndroidUsageRepository(context, policyRepository),
    private val deviceOwnerManager: DeviceOwnerManager = AndroidDeviceOwnerManager(context),
    private val policyEngine: PolicyEngine = DefaultPolicyEngine(),
    private val controlsStore: ControlsStore = ControlsStore(context)
) : PolicyEnforcementManager {

    override suspend fun initializeDeviceEnforcement() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing device enforcement...")

        // Apply Android Enterprise anti-tampering restrictions
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.enforceDeviceProtections()
        }

        // Arm recurring midnight reset alarm
        MidnightResetScheduler.scheduleNextMidnightAlarm(context)

        // Arm next schedule transition alarm
        ScheduleAlarmScheduler.scheduleNextTransition(context)

        // Enforce current policy immediately
        enforceCurrentPolicy()
        Unit
    }

    override suspend fun enforceCurrentPolicy(): Map<String, RestrictionDecision> = withContext(Dispatchers.IO) {
        val policy = policyRepository.getCurrentPolicy()
        val now = ZonedDateTime.now()
        val currentTime = TimeOfDay(now.hour, now.minute)
        val currentDay = now.dayOfWeek

        val todayUsageMap = usageRepository.refreshTodayUsage()

        val decisions = policyEngine.evaluateAll(
            policy = policy,
            currentTime = currentTime,
            currentDay = currentDay,
            todayUsageMap = todayUsageMap
        )

        val allTrackedPackages = policy.apps.keys
        val packagesToSuspend = if (controlsStore.get().lockdown) {
            allTrackedPackages
        } else {
            policyEngine.findPackagesToSuspend(decisions).toSet()
        }

        // Apply suspension via DevicePolicyManager
        // Self-heal mandatory DNS (child was offline when the parent turned it on, etc.).
        // Works with Device Owner or with the adb-granted WRITE_SECURE_SETTINGS permission.
        deviceOwnerManager.reassertMandatoryDns()
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.syncSuspendedPackages(
                desiredSuspendedPackages = packagesToSuspend,
                allTrackedPackages = allTrackedPackages
            )
        }

        // Update Room DB suspension status
        for (pkg in allTrackedPackages) {
            val shouldSuspend = pkg in packagesToSuspend
            policyRepository.updateAppSuspension(pkg, shouldSuspend)
        }

        policyRepository.logSyncAudit(
            event = "ENFORCEMENT_CYCLE_COMPLETED",
            version = policy.version,
            details = "Suspended ${packagesToSuspend.size} / ${allTrackedPackages.size} apps."
        )

        Log.i(TAG, "Enforcement complete. Suspended: $packagesToSuspend")
        decisions
    }

    companion object {
        private const val TAG = "PolicyEnforcement"
    }
}
