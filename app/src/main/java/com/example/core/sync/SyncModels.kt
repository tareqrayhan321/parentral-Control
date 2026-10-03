package com.example.core.sync

import com.example.core.apps.InstalledApp
import com.example.core.model.Policy
import com.example.core.policy.PolicyControls
import org.json.JSONObject

data class ParentAccount(val uid: String, val email: String?)
data class PairingToken(val token: String, val parentUid: String, val expiresAtEpochMs: Long)
data class ClaimResult(val parentUid: String, val childUid: String)
data class RemoteDevice(val deviceId: String, val deviceName: String, val appVersion: String)
data class RemotePolicy(val version: Int, val policy: Policy, val controls: PolicyControls?)
data class RemoteHeartbeat(
    val lastSeenEpochMs: Long?,
    val ackedPolicyVersion: Int,
    val isDeviceOwner: Boolean,
    val accessibilityEnabled: Boolean
)
data class RemoteInventory(val apps: List<InstalledApp>)

/** Content of the pairing QR. The token is the secret; parentUid is verified by Firestore rules. */
data class PairingQr(val token: String, val parentUid: String, val parentName: String) {
    fun toJson(): String = JSONObject()
        .put("v", 2)
        .put("token", token)
        .put("parentUid", parentUid)
        .put("parentName", parentName)
        .toString()

    companion object {
        fun fromJson(json: String): PairingQr? = try {
            val o = JSONObject(json)
            if (o.optInt("v") != 2) null else {
                val token = o.getString("token")
                val parentUid = o.getString("parentUid")
                if (token.length < 32 || parentUid.isBlank()) null
                else PairingQr(token, parentUid, o.optString("parentName", "Parent's Phone"))
            }
        } catch (e: Exception) {
            null
        }
    }
}
