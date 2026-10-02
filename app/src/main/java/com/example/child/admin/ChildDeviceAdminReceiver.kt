package com.example.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DeviceAdminReceiver that holds active Device Owner privileges on the Child device.
 * Used for package suspension (app blocking), uninstall prevention, and system restrictions.
 */
class ChildDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled on managed child device.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled on child device.")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Profile/Device Owner provisioning complete.")
    }

    companion object {
        private const val TAG = "ChildDeviceAdmin"

        /**
         * Returns the ComponentName for this DeviceAdminReceiver.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, ChildDeviceAdminReceiver::class.java)
        }
    }
}
