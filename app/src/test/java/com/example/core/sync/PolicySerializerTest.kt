package com.example.core.sync

import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class PolicySerializerTest {

    private val policy = Policy(
        version = 3,
        updatedAtEpochMs = 1L,
        apps = listOf(
            AppPolicy("com.example.game", "Game", RestrictionMode.LIMITED, 45, listOf("bed")),
            AppPolicy("com.example.chat", "Chat", RestrictionMode.BLOCKED),
            AppPolicy("com.example.docs", "Docs", RestrictionMode.ALLOWED, enabled = false)
        ).associateBy { it.packageName },
        schedules = listOf(
            Schedule("bed", "Bedtime", TimeOfDay(22, 0), TimeOfDay(7, 0),
                setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), enabled = false, blockedPackages = setOf("com.example.game"))
        ).associateBy { it.id }
    )

    @Test
    fun `round trip preserves apps and schedules`() {
        val map = PolicySerializer.toRemoteMap(policy)
        val back = PolicySerializer.fromRemoteMap(7, 99L, map)
        assertEquals(7, back.version)
        assertEquals(policy.apps, back.apps)
        assertEquals(policy.schedules, back.schedules)
    }

    @Test
    fun `numbers from Firestore arrive as Long and still parse`() {
        val map = mapOf(
            "apps" to listOf(mapOf("pkg" to "a.b", "name" to "AB", "mode" to "LIMITED", "limit" to 30L,
                "schedules" to emptyList<String>(), "enabled" to true)),
            "schedules" to listOf(mapOf("id" to "s", "name" to "S", "start" to 1320L, "end" to 420L,
                "days" to listOf(1L, 7L), "enabled" to true, "blocked" to emptyList<String>()))
        )
        val p = PolicySerializer.fromRemoteMap(1, 0L, map)
        assertEquals(30, p.apps["a.b"]?.dailyLimitMinutes)
        assertEquals(TimeOfDay(22, 0), p.schedules["s"]?.startTime)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), p.schedules["s"]?.activeDays)
    }

    @Test
    fun `malformed entries are skipped, valid ones kept`() {
        val map = mapOf(
            "apps" to listOf(
                mapOf("pkg" to "ok.app", "name" to "OK", "mode" to "ALLOWED"),
                mapOf("pkg" to "bad.limit", "name" to "Bad", "mode" to "LIMITED"),   // LIMITED without limit
                mapOf("pkg" to "bad.mode", "name" to "Bad2", "mode" to "NOPE"),
                "garbage"
            ),
            "schedules" to listOf(mapOf("id" to "x"))                                // missing fields
        )
        val p = PolicySerializer.fromRemoteMap(1, 0L, map)
        assertEquals(setOf("ok.app"), p.apps.keys)
        assertTrue(p.schedules.isEmpty())
    }

    @Test
    fun `package names with dots stay intact`() {
        val map = PolicySerializer.toRemoteMap(policy)
        val pkgs = (map["apps"] as List<*>).map { (it as Map<*, *>)["pkg"] }
        assertTrue("com.example.game" in pkgs)
    }

    @Test
    fun `controls round trip`() {
        val c = com.example.core.policy.PolicyControls(
            supervised = false, cameraBlocked = true, installBlocked = false,
            mandatoryDns = true, dnsHost = "dns.example.org", lockdown = true
        )
        val map = PolicySerializer.toRemoteMap(policy, c)
        assertEquals(c, PolicySerializer.controlsFromRemote(map))
    }

    @Test
    fun `no controls in the document means leave the device alone`() {
        assertEquals(null, PolicySerializer.controlsFromRemote(PolicySerializer.toRemoteMap(policy)))
    }

    @Test
    fun `invalid dns host falls back to the default`() {
        val map = mapOf("controls" to mapOf("dnsHost" to "bad host; rm -rf", "lockdown" to true))
        val c = PolicySerializer.controlsFromRemote(map)!!
        assertEquals(com.example.core.policy.PolicyControls.DEFAULT_DNS_HOST, c.dnsHost)
        assertTrue(c.lockdown)
    }
}
