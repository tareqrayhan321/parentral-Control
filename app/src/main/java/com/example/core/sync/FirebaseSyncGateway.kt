package com.example.core.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.core.apps.InstalledApp
import com.example.core.model.Policy
import com.example.core.policy.PolicyControls
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

class FirebaseSyncGateway(private val context: Context) : SyncGateway {

    override val isAvailable: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    init {
        AppCheckInstaller.install(context)
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun notConfigured() = IllegalStateException("Firebase is not configured (google-services.json missing).")

    private fun deviceRef(parentUid: String, childUid: String): DocumentReference =
        db.collection("families").document(parentUid).collection("devices").document(childUid)

    // ------------------------------------------------------------------ parent: auth

    override fun currentParent(): ParentAccount? {
        if (!isAvailable) return null
        val u = auth.currentUser ?: return null
        return if (u.isAnonymous) null else ParentAccount(u.uid, u.email, u.displayName)
    }

    override suspend fun signInParentWithGoogle(activity: Activity): Result<ParentAccount> = runCatching {
        if (!isAvailable) throw notConfigured()
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        check(resId != 0) { "default_web_client_id missing: enable Google sign-in in Firebase and re-download google-services.json." }
        val webClientId = context.getString(resId)

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)

        val cred = result.credential
        check(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type."
        }
        val google = GoogleIdTokenCredential.createFrom(cred.data)
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
            .await().user ?: error("Sign-in returned no user.")
        ParentAccount(user.uid, user.email, user.displayName ?: google.displayName)
    }

    override suspend fun signOut() {
        if (isAvailable) auth.signOut()
    }

    // ------------------------------------------------------------------ parent: pairing, policy

    override suspend fun createPairingToken(validityMinutes: Long): Result<PairingToken> = runCatching {
        if (!isAvailable) throw notConfigured()
        val parent = currentParent() ?: error("Sign in with Google first.")

        val family = db.collection("families").document(parent.uid)
        if (!family.get().await().exists()) {
            family.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
        }

        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val expiresAt = System.currentTimeMillis() + validityMinutes * 60_000L
        db.collection("pairings").document(token).set(
            mapOf(
                "parentUid" to parent.uid,
                "status" to "open",
                "expiresAt" to Timestamp(Date(expiresAt)),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        PairingToken(token, parent.uid, expiresAt)
    }

    override suspend fun pushPolicy(deviceId: String, policy: Policy): Result<Int> =
        pushPolicy(deviceId, policy, PolicyControls())

    override suspend fun pushPolicy(deviceId: String, policy: Policy, controls: PolicyControls): Result<Int> = runCatching {
        if (!isAvailable) throw notConfigured()
        val parent = currentParent() ?: error("Sign in with Google first.")
        val ref = deviceRef(parent.uid, deviceId).collection("policy").document("current")
        val payload = PolicySerializer.toRemoteMap(policy, controls)

        // Remote version must be exactly previous + 1 (enforced by Firestore rules).
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val next = (snap.getLong("version") ?: 0L) + 1L
            tx.set(ref, payload + mapOf("version" to next, "updatedAt" to FieldValue.serverTimestamp()))
            next.toInt()
        }.await()
    }

    override fun observeDevices(): Flow<List<RemoteDevice>> = callbackFlow {
        val parent = currentParent()
        if (!isAvailable || parent == null) {
            trySend(emptyList()); awaitClose { }; return@callbackFlow
        }
        val reg = db.collection("families").document(parent.uid).collection("devices")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map {
                    RemoteDevice(it.id, it.getString("deviceName") ?: "Child Device", it.getString("appVersion") ?: "?")
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeHeartbeat(deviceId: String): Flow<RemoteHeartbeat?> {
        val parent = currentParent() ?: return kotlinx.coroutines.flow.flowOf(null)
        return deviceRef(parent.uid, deviceId).collection("status").document("heartbeat").snapshots().map { s ->
            if (!s.exists()) null else RemoteHeartbeat(
                lastSeenEpochMs = s.getTimestamp("lastSeen")?.toDate()?.time,
                ackedPolicyVersion = (s.getLong("ackedPolicyVersion") ?: 0L).toInt(),
                isDeviceOwner = s.getBoolean("isDeviceOwner") ?: false,
                accessibilityEnabled = s.getBoolean("accessibilityEnabled") ?: false
            )
        }
    }

    override fun observeInventory(deviceId: String): Flow<RemoteInventory?> {
        val parent = currentParent() ?: return kotlinx.coroutines.flow.flowOf(null)
        return deviceRef(parent.uid, deviceId).collection("inventory").document("current").snapshots().map { s ->
            if (!s.exists()) null else RemoteInventory(
                (s.get("apps") as? List<*>).orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    val pkg = m["pkg"] as? String ?: return@mapNotNull null
                    InstalledApp(pkg, (m["name"] as? String) ?: pkg)
                }
            )
        }
    }

    override fun observeUsage(deviceId: String, dateString: String): Flow<Map<String, Int>?> {
        val parent = currentParent() ?: return kotlinx.coroutines.flow.flowOf(null)
        return deviceRef(parent.uid, deviceId).collection("usage").document(dateString).snapshots().map { s ->
            if (!s.exists()) null else UsageSerializer.fromRemote(s.get("minutesByPackage") as? Map<*, *>)
        }
    }

    override suspend fun unlinkDevice(deviceId: String): Result<Unit> = runCatching {
        if (!isAvailable) throw notConfigured()
        val parent = currentParent() ?: error("Sign in with Google first.")
        deviceRef(parent.uid, deviceId).delete().await()
    }

    // ------------------------------------------------------------------ child

    override fun currentUid(): String? = if (isAvailable) auth.currentUser?.uid else null

    override suspend fun claimPairing(qr: PairingQr, deviceName: String, appVersion: String): Result<ClaimResult> = runCatching {
        if (!isAvailable) throw notConfigured()

        var user = auth.currentUser
        if (user != null && !user.isAnonymous) {
            error("This phone is signed in as a parent. Use a different phone, or sign out first.")
        }
        if (user == null) user = auth.signInAnonymously().await().user ?: error("Anonymous sign-in failed.")
        val childUid = user.uid

        // One batch: claim the token AND create the device doc. Rules verify both together.
        val batch = db.batch()
        batch.update(
            db.collection("pairings").document(qr.token),
            mapOf("status" to "claimed", "claimedBy" to childUid)
        )
        batch.set(
            deviceRef(qr.parentUid, childUid),
            mapOf(
                "deviceName" to deviceName,
                "platform" to "Android",
                "appVersion" to appVersion,
                "pairedAt" to FieldValue.serverTimestamp(),
                "pairingToken" to qr.token
            )
        )
        batch.commit().await()
        ClaimResult(qr.parentUid, childUid)
    }

    override fun observePolicy(parentUid: String, childUid: String): Flow<RemotePolicy?> =
        deviceRef(parentUid, childUid).collection("policy").document("current").snapshots().map { s ->
            val data = s.data
            if (!s.exists() || data == null) null else {
                val version = (s.getLong("version") ?: 0L).toInt()
                val updated = s.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                RemotePolicy(
                    version = version,
                    policy = PolicySerializer.fromRemoteMap(version, updated, data),
                    controls = PolicySerializer.controlsFromRemoteMap(data)
                )
            }
        }

    override fun observeLinked(parentUid: String, childUid: String): Flow<Boolean> = callbackFlow {
        val reg = deviceRef(parentUid, childUid).addSnapshotListener { s, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            if (s == null) return@addSnapshotListener
            if (s.exists()) trySend(true)
            else if (!s.metadata.isFromCache) trySend(false)   // server confirmed it is gone
        }
        awaitClose { reg.remove() }
    }

    override suspend fun sendHeartbeat(
        parentUid: String, childUid: String, ackedPolicyVersion: Int,
        isDeviceOwner: Boolean, accessibilityEnabled: Boolean, appVersion: String
    ): Result<Unit> = runCatching {
        if (!isAvailable) throw notConfigured()
        deviceRef(parentUid, childUid).collection("status").document("heartbeat").set(
            mapOf(
                "lastSeen" to FieldValue.serverTimestamp(),
                "ackedPolicyVersion" to ackedPolicyVersion.toLong(),
                "isDeviceOwner" to isDeviceOwner,
                "accessibilityEnabled" to accessibilityEnabled,
                "appVersion" to appVersion
            )
        ).await()
    }

    override suspend fun uploadInventory(parentUid: String, childUid: String, apps: List<InstalledApp>): Result<Unit> = runCatching {
        if (!isAvailable) throw notConfigured()
        deviceRef(parentUid, childUid).collection("inventory").document("current").set(
            mapOf(
                "apps" to apps.take(500).map { mapOf("pkg" to it.packageName, "name" to it.displayName) },
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    override suspend fun uploadUsage(
        parentUid: String, childUid: String, dateString: String, minutes: Map<String, Int>
    ): Result<Unit> = runCatching {
        if (!isAvailable) throw notConfigured()
        deviceRef(parentUid, childUid).collection("usage").document(dateString).set(
            mapOf(
                "minutesByPackage" to UsageSerializer.toRemote(minutes),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ------------------------------------------------------------------ helpers

    private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
        val reg = addSnapshotListener { s, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            if (s != null) trySend(s)
        }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val TAG = "FirebaseSyncGateway"
    }
}
