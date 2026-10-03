package com.example.core.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.core.apps.InstalledAppsProvider
import com.example.core.database.repository.PolicyRepository
import com.example.core.model.EnrollmentStatus
import kotlinx.coroutines.CancellationException
import com.example.core.enrollment.EnrollmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Runs on the CHILD phone: pulls policy from the parent, reports status, uploads the app inventory,
 * and notices when the parent unlinks the device.
 *
 * Remote and local policy versions are deliberately independent:
 *  - the remote version (set by the parent through Firestore) is remembered in prefs and only ever grows;
 *  - the local Room version is bumped on every apply so Room's own monotonic check always passes.
 */
class ChildSyncController(
    private val context: Context,
    private val gateway: SyncGateway,
    private val repository: PolicyRepository,
    private val installedApps: InstalledAppsProvider,
    private val enrollmentManager: EnrollmentManager,
    private val isDeviceOwner: () -> Boolean,
    private val enforcePolicy: suspend () -> Unit
) {
    // The controller owns its scope so it keeps running no matter which caller (service, UI) started it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _linkLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once when the parent has unlinked this device (local enrollment is already cleared). */
    val linkLost: SharedFlow<Unit> = _linkLost.asSharedFlow()

    private val prefs = context.getSharedPreferences("child_sync_prefs", Context.MODE_PRIVATE)
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return   // idempotent: service and UI may both call this
        job = scope.launch {
            try {
                runSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Child sync stopped unexpectedly", e)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runSync() = coroutineScope {
        if (!gateway.isAvailable) return@coroutineScope
        val device = repository.getDevice() ?: return@coroutineScope
        if (device.enrollmentStatus != EnrollmentStatus.ENROLLED) return@coroutineScope
        val parentUid = device.parentId
        val childUid = device.deviceId
        if (gateway.currentUid() != childUid) {
            Log.w(TAG, "Signed-in uid does not match enrolled device id; sync disabled.")
            return@coroutineScope
        }

        launch { watchPolicy(parentUid, childUid) }
        launch { watchLink(parentUid, childUid) }
        launch { heartbeatLoop(parentUid, childUid) }
        launch {
            gateway.uploadInventory(parentUid, childUid, installedApps.listLaunchableApps())
                .onFailure { Log.w(TAG, "Inventory upload failed", it) }
        }
    }

    private suspend fun watchPolicy(parentUid: String, childUid: String) {
        resilient(gateway.observePolicy(parentUid, childUid)) { remote ->
            if (remote == null) return@resilient
            val acked = prefs.getInt(KEY_ACKED, 0)
            if (remote.version <= acked) return@resilient   // stale or replayed

            val local = repository.getCurrentPolicy()
            val applied = repository.applyNewPolicyAtomic(
                remote.policy.copy(version = local.version + 1, updatedAtEpochMs = System.currentTimeMillis())
            )
            if (applied) {
                prefs.edit().putInt(KEY_ACKED, remote.version).apply()
                repository.logSyncAudit("POLICY_APPLIED_FROM_PARENT", remote.version, "Applied remote policy v${remote.version}")
                enforcePolicy()
                sendHeartbeat(parentUid, childUid)
            }
        }
    }

    private suspend fun watchLink(parentUid: String, childUid: String) {
        resilient(gateway.observeLinked(parentUid, childUid)) { linked ->
            if (!linked) {
                Log.w(TAG, "Parent unlinked this device.")
                enrollmentManager.unenrollDevice()
                _linkLost.emit(Unit)
                stop()
            }
        }
    }

    private suspend fun heartbeatLoop(parentUid: String, childUid: String) {
        while (true) {
            sendHeartbeat(parentUid, childUid)
            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    private suspend fun sendHeartbeat(parentUid: String, childUid: String) {
        gateway.sendHeartbeat(
            parentUid = parentUid,
            childUid = childUid,
            ackedPolicyVersion = prefs.getInt(KEY_ACKED, 0),
            isDeviceOwner = isDeviceOwner(),
            accessibilityEnabled = isAccessibilityEnabled(),
            appVersion = appVersion()
        ).onFailure { Log.w(TAG, "Heartbeat failed", it) }
    }

    private fun isAccessibilityEnabled(): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(context.packageName) == true

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Re-subscribes after listener errors (offline, token refresh) instead of dying silently. */
    private suspend fun <T> resilient(flow: Flow<T>, onEach: suspend (T) -> Unit) {
        while (true) {
            try {
                flow.collect { onEach(it) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listener error, retrying in ${RETRY_DELAY_MS / 1000}s", e)
                delay(RETRY_DELAY_MS)
            }
        }
    }

    companion object {
        private const val TAG = "ChildSyncController"
        private const val KEY_ACKED = "acked_remote_version"
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L
        private const val RETRY_DELAY_MS = 30_000L
    }
}
