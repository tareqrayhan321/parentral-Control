package com.example.core.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.example.core.database.repository.PolicyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

interface UsageRepository {
    /**
     * Checks if PACKAGE_USAGE_STATS permission is currently granted.
     */
    fun hasUsageStatsPermission(): Boolean

    /**
     * Queries and refreshes active usage for all tracked packages for today's local date.
     * Writes updated values to Room database.
     *
     * @return Map of packageName to used minutes today.
     */
    suspend fun refreshTodayUsage(): Map<String, Int>

    /**
     * Returns today's usage in minutes for a specific package.
     */
    suspend fun getTodayUsageMinutes(packageName: String): Int

    /**
     * Resets today's tracked usage in the database (called on midnight transition).
     */
    suspend fun handleMidnightReset()
}

class AndroidUsageRepository(
    private val context: Context,
    private val policyRepository: PolicyRepository
) : UsageRepository {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    override fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun refreshTodayUsage(): Map<String, Int> = withContext(Dispatchers.IO) {
        val todayStr = LocalDate.now().toString()
        if (!hasUsageStatsPermission() || usageStatsManager == null) {
            // Return cached Room usage if permission is not yet granted
            return@withContext policyRepository.getTodayUsageMap(todayStr)
        }

        val startOfDayMillis = ZonedDateTime.now(ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
            .toEpochMilli()
        val nowMillis = System.currentTimeMillis()

        val usageMap = calculateUsageFromEvents(startOfDayMillis, nowMillis)

        // Save each package's usage to Room
        for ((pkg, minutes) in usageMap) {
            policyRepository.recordTodayUsage(pkg, todayStr, minutes)
        }

        policyRepository.getTodayUsageMap(todayStr)
    }

    override suspend fun getTodayUsageMinutes(packageName: String): Int = withContext(Dispatchers.IO) {
        val todayStr = LocalDate.now().toString()
        val currentUsageMap = refreshTodayUsage()
        currentUsageMap[packageName] ?: 0
    }

    override suspend fun handleMidnightReset(): Unit = withContext(Dispatchers.IO) {
        // Query to initialize new day stats
        refreshTodayUsage()
        Unit
    }

    /**
     * Computes foreground usage by pairing ACTIVITY_RESUMED and ACTIVITY_PAUSED/STOPPED events.
     * Prevents double-counting and accurately counts time for apps currently in the foreground.
     */
    fun calculateUsageFromEvents(startTime: Long, endTime: Long): Map<String, Int> {
        val usm = usageStatsManager ?: return emptyMap()
        val usageEvents = usm.queryEvents(startTime, endTime) ?: return emptyMap()

        val event = UsageEvents.Event()
        val lastResumeTime = mutableMapOf<String, Long>()
        val totalForegroundMillis = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val eventType = event.eventType
            val eventTime = event.timeStamp

            when (eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTime[pkg] = eventTime
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumedAt = lastResumeTime.remove(pkg)
                    if (resumedAt != null && eventTime >= resumedAt) {
                        val sessionDuration = eventTime - resumedAt
                        val currentTotal = totalForegroundMillis[pkg] ?: 0L
                        totalForegroundMillis[pkg] = currentTotal + sessionDuration
                    }
                }
            }
        }

        // Account for any app currently still in foreground at endTime
        for ((pkg, resumedAt) in lastResumeTime) {
            if (endTime >= resumedAt) {
                val ongoingDuration = endTime - resumedAt
                val currentTotal = totalForegroundMillis[pkg] ?: 0L
                totalForegroundMillis[pkg] = currentTotal + ongoingDuration
            }
        }

        // Fallback: If queryEvents returned no events (e.g. system pruned events),
        // use queryUsageStats as a robust fallback
        if (totalForegroundMillis.isEmpty()) {
            val statsList = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            if (!statsList.isNullOrEmpty()) {
                for (stat in statsList) {
                    if (stat.totalTimeInForeground > 0) {
                        totalForegroundMillis[stat.packageName] = stat.totalTimeInForeground
                    }
                }
            }
        }

        // Convert milliseconds to whole minutes
        return totalForegroundMillis.mapValues { (_, millis) ->
            (millis / 60_000L).toInt()
        }
    }
}
