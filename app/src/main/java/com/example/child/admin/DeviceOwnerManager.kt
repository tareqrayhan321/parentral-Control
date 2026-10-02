package com.example.child.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

interface DeviceOwnerManager {
    /**
     * True if this app is an active Device Administrator.
     */
    fun isAdminActive(): Boolean

    /**
     * True if this app is the provisioned Device Owner of the device.
     */
    fun isDeviceOwner(): Boolean

    /**
     * Suspends the specified application packages using official Android Enterprise APIs.
     * Suspended apps are dimmed in launchers, cannot be opened, and have notifications muted.
     *
     * @return List of packages that could not be suspended (e.g. system emergency packages).
     */
    fun suspendPackages(packageNames: List<String>): List<String>

    /**
     * Un-suspends the specified application packages so they can be launched again.
     *
     * @return List of packages that could not be un-suspended.
     */
    fun unsuspendPackages(packageNames: List<String>): List<String>

    /**
     * Checks if a specific package is currently suspended.
     */
    fun isPackageSuspended(packageName: String): Boolean

    /**
     * Synchronizes suspended state: suspends packages that must be restricted,
     * and un-suspends packages that are now permitted.
     */
    fun syncSuspendedPackages(desiredSuspendedPackages: Set<String>, allTrackedPackages: Set<String>)

    /**
     * Applies official Android Enterprise tamper protections:
     * - Blocks uninstallation of this parental control app
     * - Disallows force-stopping or clearing data in Settings (DISALLOW_APPS_CONTROL)
     * - Disallows booting into Safe Mode (DISALLOW_SAFE_BOOT)
     * - Disallows manual date/time modification (DISALLOW_CONFIG_DATE_TIME)
     * - Disallows USB debugging (DISALLOW_DEBUGGING_FEATURES)
     */
    fun enforceDeviceProtections()

    /**
     * Removes tamper protections upon legitimate unenrollment by the parent.
     */
    fun removeDeviceProtections()

    /**
     * Checks if parent supervision is actively enforced on this device.
     */
    fun isSupervisionActive(): Boolean

    /**
     * Enables or temporarily pauses parental supervision.
     */
    fun setSupervisionActive(active: Boolean)

    /**
     * Checks if device cameras are restricted by parent policy.
     */
    fun isCameraDisabled(): Boolean

    /**
     * Disables or enables hardware camera access via DevicePolicyManager.
     */
    fun setCameraDisabled(disabled: Boolean)

    /**
     * Checks if side-loading and app installations are blocked.
     */
    fun isAppInstallBlocked(): Boolean

    /**
     * Blocks or allows new application installations.
     */
    fun setAppInstallBlocked(blocked: Boolean)

    /**
     * Checks if mandatory Private DNS filtering is actively enforced.
     */
    fun isMandatoryDnsEnforced(): Boolean

    /**
     * Gets the currently configured mandatory DNS host.
     */
    fun getEnforcedDnsHost(): String

    /**
     * Sets mandatory Private DNS on the device and locks Android settings to prevent child modification.
     */
    fun setMandatoryDns(enabled: Boolean, dnsHost: String): Boolean
}

class AndroidDeviceOwnerManager(
    private val context: Context,
    private val adminComponent: ComponentName = ChildDeviceAdminReceiver.getComponentName(context)
) : DeviceOwnerManager {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val prefs = context.getSharedPreferences("supervision_prefs", Context.MODE_PRIVATE)

    override fun isMandatoryDnsEnforced(): Boolean {
        return prefs.getBoolean("mandatory_dns_enabled", true)
    }

    override fun getEnforcedDnsHost(): String {
        return prefs.getString("mandatory_dns_host", "family-filter-dns.cleanbrowsing.org")
            ?: "family-filter-dns.cleanbrowsing.org"
    }

    override fun setMandatoryDns(enabled: Boolean, dnsHost: String): Boolean {
        prefs.edit()
            .putBoolean("mandatory_dns_enabled", enabled)
            .putString("mandatory_dns_host", dnsHost)
            .apply()

        if (isDeviceOwner() && dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (enabled) {
                    val result = dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, dnsHost)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                    Log.i(TAG, "Mandatory DNS mode set to host: $dnsHost (result code: $result)")
                    return result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                    dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent)
                    Log.i(TAG, "Mandatory DNS cleared; restored opportunistic mode.")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring mandatory Private DNS via DPM", e)
            }
        }
        return true
    }

    override fun isSupervisionActive(): Boolean {
        return prefs.getBoolean("supervision_active", true)
    }

    override fun setSupervisionActive(active: Boolean) {
        prefs.edit().putBoolean("supervision_active", active).apply()
        if (active) {
            enforceDeviceProtections()
        } else {
            removeDeviceProtections()
        }
    }

    override fun isCameraDisabled(): Boolean {
        return try {
            if (isAdminActive() && dpm != null) {
                dpm.getCameraDisabled(adminComponent)
            } else {
                prefs.getBoolean("camera_disabled", false)
            }
        } catch (e: Exception) {
            prefs.getBoolean("camera_disabled", false)
        }
    }

    override fun setCameraDisabled(disabled: Boolean) {
        prefs.edit().putBoolean("camera_disabled", disabled).apply()
        if (isAdminActive() && dpm != null) {
            try {
                dpm.setCameraDisabled(adminComponent, disabled)
                Log.i(TAG, "Camera disabled state set to: $disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle camera state", e)
            }
        }
    }

    override fun isAppInstallBlocked(): Boolean {
        return prefs.getBoolean("app_install_blocked", true)
    }

    override fun setAppInstallBlocked(blocked: Boolean) {
        prefs.edit().putBoolean("app_install_blocked", blocked).apply()
        if (isDeviceOwner() && dpm != null) {
            try {
                if (blocked) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update install apps restriction", e)
            }
        }
    }

    override fun isAdminActive(): Boolean {
        return try {
            dpm?.isAdminActive(adminComponent) == true
        } catch (e: Exception) {
            false
        }
    }

    override fun isDeviceOwner(): Boolean {
        return try {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    override fun suspendPackages(packageNames: List<String>): List<String> {
        if (!isDeviceOwner() || dpm == null || packageNames.isEmpty()) {
            Log.w(TAG, "Cannot suspend packages: not Device Owner or empty list.")
            return packageNames
        }

        // Filter out self and critical dialer/settings packages
        val safeToSuspend = packageNames.filter { it != context.packageName }
        if (safeToSuspend.isEmpty()) return emptyList()

        return try {
            val failedPackages = dpm.setPackagesSuspended(
                adminComponent,
                safeToSuspend.toTypedArray(),
                true
            )
            val failedList = failedPackages?.toList() ?: emptyList()
            Log.i(TAG, "Suspended ${safeToSuspend.size - failedList.size} packages. Failed: $failedList")
            failedList
        } catch (e: Exception) {
            Log.e(TAG, "Exception suspending packages", e)
            packageNames
        }
    }

    override fun unsuspendPackages(packageNames: List<String>): List<String> {
        if (!isDeviceOwner() || dpm == null || packageNames.isEmpty()) {
            return packageNames
        }

        return try {
            val failedPackages = dpm.setPackagesSuspended(
                adminComponent,
                packageNames.toTypedArray(),
                false
            )
            val failedList = failedPackages?.toList() ?: emptyList()
            Log.i(TAG, "Un-suspended ${packageNames.size - failedList.size} packages. Failed: $failedList")
            failedList
        } catch (e: Exception) {
            Log.e(TAG, "Exception un-suspending packages", e)
            packageNames
        }
    }

    override fun isPackageSuspended(packageName: String): Boolean {
        if (!isDeviceOwner() || dpm == null) return false
        return try {
            dpm.isPackageSuspended(adminComponent, packageName)
        } catch (e: Exception) {
            false
        }
    }

    override fun syncSuspendedPackages(
        desiredSuspendedPackages: Set<String>,
        allTrackedPackages: Set<String>
    ) {
        if (!isDeviceOwner() || dpm == null) {
            Log.w(TAG, "Skipping suspension sync: device owner not active.")
            return
        }

        val toSuspend = desiredSuspendedPackages.filter { it != context.packageName }
        val toUnsuspend = allTrackedPackages.filter { it !in desiredSuspendedPackages }

        if (toSuspend.isNotEmpty()) {
            suspendPackages(toSuspend)
        }
        if (toUnsuspend.isNotEmpty()) {
            unsuspendPackages(toUnsuspend)
        }
    }

    override fun enforceDeviceProtections() {
        if (!isDeviceOwner() || dpm == null) {
            Log.w(TAG, "Cannot enforce protections: not Device Owner.")
            return
        }

        try {
            // 1. Prevent uninstallation of the Parental Control app
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            // 2. Prevent child from clearing app storage or force stopping in Settings
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // 3. Prevent child from booting into Safe Mode to disable admin
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

            // 4. Prevent child from altering system date/time to bypass schedules/limits
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)

            // 5. Prevent unauthorized USB debugging
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // 6. Enforce Mandatory Private DNS and disallow child from changing it in Android Settings
            if (isMandatoryDnsEnforced() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, getEnforcedDnsHost())
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }

            Log.i(TAG, "Successfully enforced all Android Enterprise device protections.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed enforcing device protections", e)
        }
    }

    override fun removeDeviceProtections() {
        if (!isDeviceOwner() || dpm == null) return

        try {
            dpm.setUninstallBlocked(adminComponent, context.packageName, false)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent)
            }
            Log.i(TAG, "Device protections removed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing device protections", e)
        }
    }

    companion object {
        private const val TAG = "DeviceOwnerManager"
    }
}
