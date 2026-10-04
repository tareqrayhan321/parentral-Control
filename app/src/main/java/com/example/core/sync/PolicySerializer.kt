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
        val result = linkedMapOf<String, Any?>(
            "apps" to policy.apps.values.map { appToMap(it) },
            "schedules" to policy.schedules.values.map { scheduleToMap(it) }
        )
        controls?.let { result["controls"] = controlsToRemoteMap(it) }
        return result
    }

    fun controlsToRemoteMap(controls: PolicyControls): Map<String, Any?> = mapOf(
        "supervised" to controls.supervised,
        "cameraBlocked" to controls.cameraBlocked,
        "installBlocked" to controls.installBlocked,
        "mandatoryDns" to controls.mandatoryDns,
        "dnsHost" to controls.dnsHost,
        "lockdown" to controls.lockdown
    )

    /** Missing controls are valid for old policy documents; malformed present controls are ignored. */
    fun controlsFromRemoteMap(data: Map<String, Any?>): PolicyControls? {
        val raw = data["controls"] as? Map<*, *> ?: return null
        val expectedKeys = setOf("supervised", "cameraBlocked", "installBlocked", "mandatoryDns", "dnsHost", "lockdown")
        val keys = raw.keys.filterIsInstance<String>().toSet()
        if (keys != expectedKeys) return null

        val supervised = raw["supervised"] as? Boolean ?: return null
        val cameraBlocked = raw["cameraBlocked"] as? Boolean ?: return null
        val installBlocked = raw["installBlocked"] as? Boolean ?: return null
        val mandatoryDns = raw["mandatoryDns"] as? Boolean ?: return null
        val dnsHost = raw["dnsHost"] as? String ?: return null
        val lockdown = raw["lockdown"] as? Boolean ?: return null
        if (!DNS_HOST_REGEX.matches(dnsHost)) return null

        return PolicyControls(supervised, cameraBlocked, installBlocked, mandatoryDns, dnsHost, lockdown)
    }

    private val DNS_HOST_REGEX = Regex("^[A-Za-z0-9.-]{1,253}$")
    /** Compatibility alias retained for callers from the earlier sync implementation. */
    fun controlsFromRemote(data: Map<String, Any?>): PolicyControls? = controlsFromRemoteMap(data)

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
