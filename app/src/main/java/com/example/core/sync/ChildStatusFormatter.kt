package com.example.core.sync

/** Turns a heartbeat into the label shown on the parent dashboard. Pure and testable. */
object ChildStatusFormatter {
    const val ONLINE_WINDOW_MS = 20 * 60 * 1000L

    fun format(hasChild: Boolean, hb: RemoteHeartbeat?, nowMs: Long): String {
        if (!hasChild) return "Waiting for child connection..."
        val last = hb?.lastSeenEpochMs ?: return "Linked • waiting for first report from child"
        val ageMin = ((nowMs - last).coerceAtLeast(0L) / 60_000L)
        val state = if (nowMs - last <= ONLINE_WINDOW_MS) "🟢 Online" else "⚪ Offline"
        val ago = if (ageMin < 1) "just now" else "$ageMin min ago"
        val owner = if (hb.isDeviceOwner) "Device Owner" else "no Device Owner"
        return "$state • last report $ago • rules v${hb.ackedPolicyVersion} • $owner"
    }
}
