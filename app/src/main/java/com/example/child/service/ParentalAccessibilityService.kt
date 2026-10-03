package com.example.child.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.child.ui.AppBlockActivity
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.BlockReason
import com.example.core.model.RestrictionDecision
import com.example.core.model.TimeOfDay
import com.example.core.policy.ControlsStore
import com.example.core.policy.DefaultPolicyEngine
import com.example.core.policy.withLockdown
import com.example.core.policy.PolicyEngine
import com.example.core.usage.AndroidUsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class ParentalAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var policyRepository: RoomPolicyRepository
    private lateinit var usageRepository: AndroidUsageRepository
    private val policyEngine: PolicyEngine = DefaultPolicyEngine()

    private var lastBlockedPackage: String? = null
    private var lastBlockedTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val database = ChildDatabase.getInstance(applicationContext)
        policyRepository = RoomPolicyRepository(database)
        usageRepository = AndroidUsageRepository(applicationContext, policyRepository)
        Log.i(TAG, "ParentalAccessibilityService started.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore our own app and system launcher/emergency dialer
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher", ignoreCase = true)
        ) {
            return
        }

        // Throttle rapid repeated events for the same blocked package within 1.5 seconds
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockedTimestamp) < 1500L) {
            return
        }

        serviceScope.launch {
            checkAndEnforceApp(packageName)
        }
    }

    private suspend fun checkAndEnforceApp(packageName: String) {
        val policy = policyRepository.getCurrentPolicy()
            .withLockdown(ControlsStore(applicationContext).get().lockdown)

        val now = ZonedDateTime.now()
        val currentTime = TimeOfDay(now.hour, now.minute)
        val currentDay = now.dayOfWeek

        val usageMap = usageRepository.refreshTodayUsage()
        val decision = policyEngine.evaluate(
            packageName = packageName,
            currentTime = currentTime,
            currentDay = currentDay,
            usageTodayMinutes = usageMap[packageName] ?: 0,
            policy = policy
        )

        when (decision) {
            is RestrictionDecision.Blocked -> {
                val (reasonStr, msg) = when (decision.reason) {
                    BlockReason.MANUALLY_BLOCKED ->
                        Pair("ALWAYS_BLOCKED", "This app has been blocked by the parent.")
                    BlockReason.SCHEDULE_ACTIVE ->
                        Pair("BEDTIME_SCHEDULE", decision.details ?: "Bedtime or study schedule is currently active.")
                    BlockReason.DAILY_LIMIT_REACHED ->
                        Pair("LIMIT_EXCEEDED", decision.details ?: "Daily screen time limit reached for this app.")
                }
                triggerBlock(packageName, reasonStr, msg)
            }
            is RestrictionDecision.Allowed -> {
                lastBlockedPackage = null
            }
        }
    }

    private fun triggerBlock(packageName: String, reason: String, message: String) {
        lastBlockedPackage = packageName
        lastBlockedTimestamp = System.currentTimeMillis()

        Log.w(TAG, "Blocking app: $packageName. Reason: $reason - $message")

        // First return to Home screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Then launch the AppBlockActivity
        val blockIntent = Intent(applicationContext, AppBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppBlockActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AppBlockActivity.EXTRA_BLOCK_REASON, reason)
            putExtra(AppBlockActivity.EXTRA_BLOCK_MESSAGE, message)
        }
        startActivity(blockIntent)
    }

    override fun onInterrupt() {
        Log.i(TAG, "ParentalAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "ParentalAccessibilityService destroyed.")
    }

    companion object {
        private const val TAG = "ParentalAccessibility"
    }
}
