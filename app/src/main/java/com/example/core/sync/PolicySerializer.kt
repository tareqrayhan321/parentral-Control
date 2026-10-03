package com.example.core.sync

import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import com.example.core.policy.PolicyControls
import java.time.DayOfWeek

/**
 * Converts a Policy to/from plain maps/lists that Firestore can store.
 * Apps and schedules are stored as LISTS (not maps keyed by package name) so that dots in
 * package names can never be mistaken for Firestore field paths.
 * Pure Kotlin: no Firebase types, fully unit-testable.
 */
object PolicySerializer {

    fun toRemoteMap(policy: Policy, controls: PolicyControls? = null): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "apps" to policy.apps.values.map { appToMap(it) },
            "schedules" to policy.schedules.values.map { scheduleToMap(it) }
        )
        if (controls != null) map["controls"] = controlsToMap(controls)
        return map
    }

    private fun controlsToMap(c: PolicyControls): Map<String, Any?> = mapOf(
        "supervised" to c.supervised,
        "camera" to c.cameraBlocked,
        "install" to c.installBlocked,
        "dns" to c.mandatoryDns,
        "dnsHost" to c.dnsHost,
        "lockdown" to c.lockdown
    )

    private val hostRegex = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

    /** Null when the document carries no controls (leave the device settings alone). */
    fun controlsFromRemote(data: Map<String, Any?>): PolicyControls? {
        val m = data["controls"] as? Map<*, *> ?: return null
        val d = PolicyControls()
        val host = (m["dnsHost"] as? String)?.takeIf { hostRegex.matches(it) } ?: d.dnsHost
        return PolicyControls(
            supervised = m["supervised"] as? Boolean ?: d.supervised,
            cameraBlocked = m["camera"] as? Boolean ?: d.cameraBlocked,
            installBlocked = m["install"] as? Boolean ?: d.installBlocked,
            mandatoryDns = m["dns"] as? Boolean ?: d.mandatoryDns,
            dnsHost = host,
            lockdown = m["lockdown"] as? Boolean ?: d.lockdown
        )
    }

    private fun appToMap(a: AppPolicy): Map<String, Any?> = mapOf(
        "pkg" to a.packageName,
        "name" to a.displayName,
        "mode" to a.mode.name,
        "limit" to a.dailyLimitMinutes?.toLong(),
        "schedules" to a.scheduleIds,
        "enabled" to a.enabled
    )

    private fun scheduleToMap(s: Schedule): Map<String, Any?> = mapOf(
        "id" to s.id,
        "name" to s.name,
        "start" to s.startTime.toTotalMinutes().toLong(),
        "end" to s.endTime.toTotalMinutes().toLong(),
        "days" to s.activeDays.map { it.value.toLong() },
        "enabled" to s.enabled,
        "blocked" to s.blockedPackages.toList()
    )

    /** Malformed entries are skipped rather than failing the whole policy. */
    fun fromRemoteMap(version: Int, updatedAtEpochMs: Long, data: Map<String, Any?>): Policy {
        val apps = (data["apps"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.let(::mapToApp) }
            .associateBy { it.packageName }
        val schedules = (data["schedules"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.let(::mapToSchedule) }
            .associateBy { it.id }
        return Policy(version, updatedAtEpochMs, apps, schedules)
    }

    private fun mapToApp(m: Map<*, *>): AppPolicy? = runCatching {
        AppPolicy(
            packageName = m["pkg"] as String,
            displayName = m["name"] as String,
            mode = RestrictionMode.valueOf(m["mode"] as String),
            dailyLimitMinutes = (m["limit"] as? Number)?.toInt(),
            scheduleIds = (m["schedules"] as? List<*>).orEmpty().filterIsInstance<String>(),
            enabled = m["enabled"] as? Boolean ?: true
        )
    }.getOrNull()

    private fun mapToSchedule(m: Map<*, *>): Schedule? = runCatching {
        Schedule(
            id = m["id"] as String,
            name = m["name"] as String,
            startTime = TimeOfDay.fromTotalMinutes((m["start"] as Number).toInt()),
            endTime = TimeOfDay.fromTotalMinutes((m["end"] as Number).toInt()),
            activeDays = (m["days"] as? List<*>).orEmpty()
                .mapNotNull { (it as? Number)?.toInt() }
                .filter { it in 1..7 }
                .map { DayOfWeek.of(it) }
                .toSet(),
            enabled = m["enabled"] as? Boolean ?: true,
            blockedPackages = (m["blocked"] as? List<*>).orEmpty().filterIsInstance<String>().toSet()
        )
    }.getOrNull()
}
