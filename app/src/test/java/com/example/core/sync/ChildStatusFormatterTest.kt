package com.example.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildStatusFormatterTest {
    private val now = 1_000_000_000L
    private fun hb(ageMs: Long?, v: Int = 4, owner: Boolean = true) =
        RemoteHeartbeat(ageMs?.let { now - it }, v, owner, true)

    @Test fun `no child`() =
        assertEquals("Waiting for child connection...", ChildStatusFormatter.format(false, null, now))

    @Test fun `child linked but no report yet`() =
        assertTrue(ChildStatusFormatter.format(true, null, now).contains("waiting for first report"))

    @Test fun `recent report is online`() {
        val label = ChildStatusFormatter.format(true, hb(5 * 60_000L), now)
        assertTrue(label, label.startsWith("🟢 Online"))
        assertTrue(label, label.contains("5 min ago") && label.contains("rules v4") && label.contains("Device Owner"))
    }

    @Test fun `old report is offline`() =
        assertTrue(ChildStatusFormatter.format(true, hb(60 * 60_000L), now).startsWith("⚪ Offline"))

    @Test fun `pending server timestamp counts as no report`() =
        assertTrue(ChildStatusFormatter.format(true, hb(null), now).contains("waiting for first report"))

    @Test fun `no device owner is stated`() =
        assertTrue(ChildStatusFormatter.format(true, hb(1000L, owner = false), now).contains("no Device Owner"))
}
