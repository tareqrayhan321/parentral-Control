package com.example.core.policy

import android.content.Context

/** Persists the desired device-level controls independently from the per-app policy. */
class ControlsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): PolicyControls = PolicyControls(
        supervised = prefs.getBoolean(KEY_SUPERVISED, true),
        cameraBlocked = prefs.getBoolean(KEY_CAMERA_BLOCKED, false),
        installBlocked = prefs.getBoolean(KEY_INSTALL_BLOCKED, true),
        mandatoryDns = prefs.getBoolean(KEY_MANDATORY_DNS, true),
        dnsHost = prefs.getString(KEY_DNS_HOST, PolicyControls.DEFAULT_DNS_HOST)
            ?.takeIf(String::isNotBlank) ?: PolicyControls.DEFAULT_DNS_HOST,
        lockdown = prefs.getBoolean(KEY_LOCKDOWN, false)
    )

    fun set(controls: PolicyControls) {
        prefs.edit()
            .putBoolean(KEY_SUPERVISED, controls.supervised)
            .putBoolean(KEY_CAMERA_BLOCKED, controls.cameraBlocked)
            .putBoolean(KEY_INSTALL_BLOCKED, controls.installBlocked)
            .putBoolean(KEY_MANDATORY_DNS, controls.mandatoryDns)
            .putString(KEY_DNS_HOST, controls.dnsHost)
            .putBoolean(KEY_LOCKDOWN, controls.lockdown)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "policy_controls_prefs"
        private const val KEY_SUPERVISED = "supervised"
        private const val KEY_CAMERA_BLOCKED = "camera_blocked"
        private const val KEY_INSTALL_BLOCKED = "install_blocked"
        private const val KEY_MANDATORY_DNS = "mandatory_dns"
        private const val KEY_DNS_HOST = "dns_host"
        private const val KEY_LOCKDOWN = "lockdown"
    }
}
