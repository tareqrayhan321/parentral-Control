package com.example.core.policy

import android.util.Log
import com.example.child.admin.DeviceOwnerManager

/** Applies a received controls bundle through the existing Android Enterprise manager. */
class ControlsApplier(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val store: ControlsStore
) {
    fun apply(controls: PolicyControls) {
        // Persist the desired state first so UI and lockdown enforcement stay consistent even if a
        // particular Android API is unavailable on this device.
        store.set(controls)
        applySafely("supervision") { deviceOwnerManager.setSupervisionActive(controls.supervised) }
        applySafely("camera restriction") { deviceOwnerManager.setCameraDisabled(controls.cameraBlocked) }
        applySafely("install restriction") { deviceOwnerManager.setAppInstallBlocked(controls.installBlocked) }
        applySafely("mandatory DNS") {
            if (!deviceOwnerManager.setMandatoryDns(controls.mandatoryDns, controls.dnsHost)) {
                Log.w(TAG, "Device Owner reported that mandatory DNS could not be fully applied.")
            }
        }
    }

    private inline fun applySafely(label: String, action: () -> Unit) {
        runCatching(action).onFailure { Log.e(TAG, "Failed applying $label control", it) }
    }

    private companion object {
        const val TAG = "ControlsApplier"
    }
}
