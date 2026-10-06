package com.example.core.policy

import android.content.Context
import android.provider.Settings

/**
 * The REAL Private DNS state, read from the OS (not from what the parent asked for).
 * Any app may read these global settings; only a Device Owner can change and lock them.
 */
object PrivateDnsState {
    data class Snapshot(val active: Boolean, val host: String)

    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_HOST = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"

    fun read(context: Context): Snapshot {
        val cr = context.contentResolver
        val mode = runCatching { Settings.Global.getString(cr, KEY_MODE) }.getOrNull()
        val host = runCatching { Settings.Global.getString(cr, KEY_HOST) }.getOrNull().orEmpty()
        return Snapshot(active = mode == MODE_HOSTNAME && host.isNotBlank(), host = host)
    }
}
