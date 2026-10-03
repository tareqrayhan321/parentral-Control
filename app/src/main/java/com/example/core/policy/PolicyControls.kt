package com.example.core.policy

import android.content.Context
import com.example.child.admin.DeviceOwnerManager
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode

/**
 * Device-level switches the parent controls. Synced to the child together with the app/schedule policy.
 * Kept separate from [Policy] so the Room schema does not change.
 */
data class PolicyControls(
    val supervised: Boolean = true,
    val cameraBlocked: Boolean = false,
    val installBlocked: Boolean = true,
    val mandatoryDns: Boolean = true,
    val dnsHost: String = DEFAULT_DNS_HOST,
    val lockdown: Boolean = false
) {
    companion object {
        const val DEFAULT_DNS_HOST = "family-filter-dns.cleanbrowsing.org"
    }
}

/**
 * Instant lockdown is a FLAG, not a rewrite of the stored app rules: while it is on every tracked app is
 * evaluated as blocked, and when it is released the original BLOCKED / LIMITED / ALLOWED rules are untouched.
 */
fun Policy.withLockdown(lockdown: Boolean): Policy =
    if (!lockdown) this
    else copy(apps = apps.mapValues { (_, app) -> app.copy(mode = RestrictionMode.BLOCKED, enabled = true) })

/** Persists the controls on this phone (parent: desired state; child: last state received). */
class ControlsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("policy_controls_prefs", Context.MODE_PRIVATE)

    fun get(): PolicyControls = PolicyControls(
        supervised = prefs.getBoolean("supervised", true),
        cameraBlocked = prefs.getBoolean("camera_blocked", false),
        installBlocked = prefs.getBoolean("install_blocked", true),
        mandatoryDns = prefs.getBoolean("mandatory_dns", true),
        dnsHost = prefs.getString("dns_host", null) ?: PolicyControls.DEFAULT_DNS_HOST,
        lockdown = prefs.getBoolean("lockdown", false)
    )

    fun set(c: PolicyControls) {
        prefs.edit()
            .putBoolean("supervised", c.supervised)
            .putBoolean("camera_blocked", c.cameraBlocked)
            .putBoolean("install_blocked", c.installBlocked)
            .putBoolean("mandatory_dns", c.mandatoryDns)
            .putString("dns_host", c.dnsHost)
            .putBoolean("lockdown", c.lockdown)
            .apply()
    }
}

/** Stores the controls and applies them to THIS phone through Device Owner. Only changed values are re-applied. */
class ControlsApplier(
    private val deviceOwner: DeviceOwnerManager,
    private val store: ControlsStore
) {
    fun apply(c: PolicyControls) {
        store.set(c)
        if (deviceOwner.isSupervisionActive() != c.supervised) deviceOwner.setSupervisionActive(c.supervised)
        if (deviceOwner.isCameraDisabled() != c.cameraBlocked) deviceOwner.setCameraDisabled(c.cameraBlocked)
        if (deviceOwner.isAppInstallBlocked() != c.installBlocked) deviceOwner.setAppInstallBlocked(c.installBlocked)
        if (deviceOwner.isMandatoryDnsEnforced() != c.mandatoryDns || deviceOwner.getEnforcedDnsHost() != c.dnsHost) {
            deviceOwner.setMandatoryDns(c.mandatoryDns, c.dnsHost)
        }
        // lockdown needs no Device Owner call: PolicyEnforcementManager / AccessibilityService read it from the store.
    }
}
