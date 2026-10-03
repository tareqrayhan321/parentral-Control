package com.example.core.sync

import android.app.Activity
import com.example.core.apps.InstalledApp
import com.example.core.model.Policy
import com.example.core.policy.PolicyControls
import kotlinx.coroutines.flow.Flow

/** Everything the app needs from the backend. The UI never touches Firebase directly. */
interface SyncGateway {
    /** False when google-services.json is missing / Firebase is not configured. */
    val isAvailable: Boolean

    // ---- parent side ----
    fun currentParent(): ParentAccount?
    suspend fun signInParentWithGoogle(activity: Activity): Result<ParentAccount>
    suspend fun signOut()
    suspend fun createPairingToken(validityMinutes: Long = 10): Result<PairingToken>
    /** Returns the new REMOTE policy version. */
    suspend fun pushPolicy(deviceId: String, policy: Policy, controls: PolicyControls): Result<Int>
    fun observeDevices(): Flow<List<RemoteDevice>>
    fun observeHeartbeat(deviceId: String): Flow<RemoteHeartbeat?>
    fun observeInventory(deviceId: String): Flow<RemoteInventory?>
    /** Minutes per package for the given local date (YYYY-MM-DD), or null if the child has not reported yet. */
    fun observeUsage(deviceId: String, dateString: String): Flow<Map<String, Int>?>
    suspend fun unlinkDevice(deviceId: String): Result<Unit>

    // ---- child side ----
    fun currentUid(): String?
    suspend fun claimPairing(qr: PairingQr, deviceName: String, appVersion: String): Result<ClaimResult>
    fun observePolicy(parentUid: String, childUid: String): Flow<RemotePolicy?>
    /** true = device doc exists on the server, false = parent unlinked it. Cache-only misses are ignored. */
    fun observeLinked(parentUid: String, childUid: String): Flow<Boolean>
    suspend fun sendHeartbeat(
        parentUid: String, childUid: String, ackedPolicyVersion: Int,
        isDeviceOwner: Boolean, accessibilityEnabled: Boolean, appVersion: String
    ): Result<Unit>
    suspend fun uploadInventory(parentUid: String, childUid: String, apps: List<InstalledApp>): Result<Unit>
    suspend fun uploadUsage(parentUid: String, childUid: String, dateString: String, minutes: Map<String, Int>): Result<Unit>
}
