package com.example.core.policy

import com.example.core.model.AppPolicy
import com.example.core.model.BlockReason
import com.example.core.model.Policy
import com.example.core.model.RestrictionDecision
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Pure domain Policy Engine that evaluates access decisions for managed applications.
 * Completely independent of Android platform APIs to allow fast, deterministic unit testing.
 */
interface PolicyEngine {

    /**
     * Evaluates whether a specific application is allowed or blocked at this moment.
     *
     * @param packageName Android package name of the app (e.g. "com.google.android.youtube").
     * @param currentTime Current local time of day.
     * @param currentDay Current local day of the week.
     * @param usageTodayMinutes Total minutes the app was actively used today.
     * @param policy The active policy configuration.
     * @return [RestrictionDecision.Allowed] or [RestrictionDecision.Blocked].
     */
    fun evaluate(
        packageName: String,
        currentTime: TimeOfDay,
        currentDay: DayOfWeek,
        usageTodayMinutes: Int,
        policy: Policy
    ): RestrictionDecision

    /**
     * Evaluates all applications configured in the policy at once.
     *
     * @param policy The active policy configuration.
     * @param currentTime Current local time of day.
     * @param currentDay Current local day of week.
     * @param todayUsageMap Map of packageName to used minutes today.
     * @return Map of packageName to its RestrictionDecision.
     */
    fun evaluateAll(
        policy: Policy,
        currentTime: TimeOfDay,
        currentDay: DayOfWeek,
        todayUsageMap: Map<String, Int>
    ): Map<String, RestrictionDecision>

    /**
     * Filters decisions down to the list of package names that MUST be suspended by DevicePolicyManager.
     */
    fun findPackagesToSuspend(decisions: Map<String, RestrictionDecision>): List<String>

    /**
     * Calculates the remaining minutes available today for a limited application.
     * Returns null if the app is unrestricted or manually blocked.
     */
    fun calculateRemainingMinutes(appPolicy: AppPolicy, usageTodayMinutes: Int): Int?
}

/**
 * Standard implementation of [PolicyEngine] enforcing strict priority:
 * 1. Disabled or unmanaged -> ALLOWED
 * 2. MANUALLY_BLOCKED -> BLOCKED (Priority 1)
 * 3. SCHEDULE_ACTIVE -> BLOCKED (Priority 2)
 * 4. DAILY_LIMIT_REACHED -> BLOCKED (Priority 3)
 * 5. Otherwise -> ALLOWED
 */
class DefaultPolicyEngine : PolicyEngine {

    override fun evaluate(
        packageName: String,
        currentTime: TimeOfDay,
        currentDay: DayOfWeek,
        usageTodayMinutes: Int,
        policy: Policy
    ): RestrictionDecision {
        val appPolicy = policy.apps[packageName] ?: return RestrictionDecision.Allowed

        if (!appPolicy.enabled) {
            return RestrictionDecision.Allowed
        }

        // Priority 1: Explicit Manual Block
        if (appPolicy.mode == RestrictionMode.BLOCKED) {
            return RestrictionDecision.Blocked(
                reason = BlockReason.MANUALLY_BLOCKED,
                details = "Manually restricted by parent."
            )
        }

        // Priority 2: Active Time Schedule (Specific app time-window block or device-wide schedule)
        for ((_, schedule) in policy.schedules) {
            if (!schedule.enabled) continue
            val appliesToApp = if (schedule.blockedPackages.isNotEmpty()) {
                packageName in schedule.blockedPackages || schedule.id in appPolicy.scheduleIds
            } else {
                // Device-wide schedule (e.g. Bedtime) applies to all managed apps
                true
            }

            if (appliesToApp && schedule.isActiveAt(currentTime, currentDay)) {
                return RestrictionDecision.Blocked(
                    reason = BlockReason.SCHEDULE_ACTIVE,
                    details = "Blocked during schedule: ${schedule.name} (${schedule.startTime.to12HourString()} - ${schedule.endTime.to12HourString()})"
                )
            }
        }

        // Priority 3: Daily Screen Time Limit
        if (appPolicy.mode == RestrictionMode.LIMITED) {
            val limit = appPolicy.dailyLimitMinutes
            if (limit != null && limit > 0 && usageTodayMinutes >= limit) {
                return RestrictionDecision.Blocked(
                    reason = BlockReason.DAILY_LIMIT_REACHED,
                    details = "Daily limit reached: $usageTodayMinutes / $limit minutes used."
                )
            }
        }

        return RestrictionDecision.Allowed
    }

    override fun evaluateAll(
        policy: Policy,
        currentTime: TimeOfDay,
        currentDay: DayOfWeek,
        todayUsageMap: Map<String, Int>
    ): Map<String, RestrictionDecision> {
        val results = mutableMapOf<String, RestrictionDecision>()
        for ((packageName, _) in policy.apps) {
            val usageMinutes = todayUsageMap[packageName] ?: 0
            results[packageName] = evaluate(
                packageName = packageName,
                currentTime = currentTime,
                currentDay = currentDay,
                usageTodayMinutes = usageMinutes,
                policy = policy
            )
        }
        return results
    }

    override fun findPackagesToSuspend(decisions: Map<String, RestrictionDecision>): List<String> {
        return decisions.entries
            .filter { it.value is RestrictionDecision.Blocked }
            .map { it.key }
    }

    override fun calculateRemainingMinutes(appPolicy: AppPolicy, usageTodayMinutes: Int): Int? {
        if (!appPolicy.enabled || appPolicy.mode != RestrictionMode.LIMITED) return null
        val limit = appPolicy.dailyLimitMinutes ?: return null
        return (limit - usageTodayMinutes).coerceAtLeast(0)
    }
}
