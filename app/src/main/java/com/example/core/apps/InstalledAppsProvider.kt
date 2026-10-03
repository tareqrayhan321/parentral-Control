package com.example.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

data class InstalledApp(val packageName: String, val displayName: String)

interface InstalledAppsProvider {
    /** User-launchable apps installed on this device, excluding this app itself. */
    fun listLaunchableApps(): List<InstalledApp>
}

class AndroidInstalledAppsProvider(private val context: Context) : InstalledAppsProvider {

    override fun listLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(pkg, info.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.displayName.lowercase() }
    }
}
