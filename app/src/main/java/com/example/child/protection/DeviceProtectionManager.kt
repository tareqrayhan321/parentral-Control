package com.example.child.protection

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.child.admin.ChildDeviceAdminReceiver
import com.example.child.service.ParentalAccessibilityService
import com.example.child.service.ParentalMonitoringService

data class PermissionStatus(
    val id: String,
    val title: String,
    val titleBn: String,
    val description: String,
    val descriptionBn: String,
    val isGranted: Boolean,
    val isCrucial: Boolean = true
)

object DeviceProtectionManager {

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, ChildDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isAdminActive(getAdminComponent(context)) == true
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, ParentalAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedComponentName, ignoreCase = true) || it.contains(context.packageName)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun getAllPermissionsStatus(context: Context): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                id = "admin",
                title = "Device Administrator",
                titleBn = "ডিভাইস অ্যাডমিনিস্ট্রেটর পারমিশন",
                description = "Prevents the child from uninstalling the parental control app or disabling protection.",
                descriptionBn = "সন্তান যাতে অভিভাবকের অনুমতি ছাড়া এই অ্যাপটি আনইনস্টল বা বন্ধ করতে না পারে।",
                isGranted = isDeviceAdminActive(context),
                isCrucial = true
            ),
            PermissionStatus(
                id = "usage",
                title = "Usage Access",
                titleBn = "অ্যাপ ব্যবহারের হিসাব (Usage Access)",
                description = "Monitors daily screen time, tracks app usage, and enforces time limits.",
                descriptionBn = "কোন অ্যাপ কতক্ষণ ব্যবহার করা হয়েছে তা পর্যবেক্ষণ ও সময়সীমা বজায় রাখতে প্রয়োজন।",
                isGranted = hasUsageStatsPermission(context),
                isCrucial = true
            ),
            PermissionStatus(
                id = "overlay",
                title = "Display Over Other Apps",
                titleBn = "অন্যান্য অ্যাপের উপর প্রদর্শন (Overlay)",
                description = "Displays the blocking screen immediately when a restricted app is opened.",
                descriptionBn = "ব্লক করা অ্যাপ খুললে সাথে সাথে সতর্কবার্তা ও ব্লকিং স্ক্রিন দেখানোর জন্য।",
                isGranted = hasOverlayPermission(context),
                isCrucial = true
            ),
            PermissionStatus(
                id = "accessibility",
                title = "Accessibility Service",
                titleBn = "এক্সেসিবিলিটি সার্ভিস (রিয়েল-টাইম ইন্টারসেপ্ট)",
                description = "Instantly intercepts and blocks restricted apps and prevents bypassing restrictions.",
                descriptionBn = "ব্লক করা অ্যাপ বা বেডটাইমে অ্যাপ খোলার সাথে সাথে তা আটকে দেয়।",
                isGranted = isAccessibilityServiceEnabled(context),
                isCrucial = true
            ),
            PermissionStatus(
                id = "battery",
                title = "Ignore Battery Optimization",
                titleBn = "ব্যাটারি অপটিমাইজেশন বন্ধ",
                description = "Ensures parental protection runs continuously in the background without being killed.",
                descriptionBn = "অ্যান্ড্রয়েড যাতে ব্যাকগ্রাউন্ডে সুরক্ষা বন্ধ করে না দেয়।",
                isGranted = isIgnoringBatteryOptimizations(context),
                isCrucial = false
            ),
            PermissionStatus(
                id = "notification",
                title = "Notifications",
                titleBn = "নোটিফিকেশন পারমিশন",
                description = "Sends screen time reminders, bedtime alerts, and active supervision status.",
                descriptionBn = "স্ক্রিন টাইম শেষ হওয়ার সতর্কবার্তা ও স্থিতি নোটিফিকেশন দেখাতে প্রয়োজন।",
                isGranted = hasNotificationPermission(context),
                isCrucial = false
            )
        )
    }

    fun isAllCrucialGranted(context: Context): Boolean {
        return isDeviceAdminActive(context) &&
                hasUsageStatsPermission(context) &&
                hasOverlayPermission(context) &&
                isAccessibilityServiceEnabled(context)
    }

    // Intents to launch respective settings
    fun createDeviceAdminIntent(context: Context): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdminComponent(context))
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Parental Control requires Device Administrator permission to enforce app time limits, block camera, and prevent unauthorized uninstallation."
        )
        return intent
    }

    fun createUsageAccessIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        return intent
    }

    fun createOverlayIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun createAccessibilityIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun createNotificationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun startProtectionService(context: Context) {
        try {
            val intent = Intent(context, ParentalMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceProtectionManager", "Failed to start protection service", e)
        }
    }
}
