package com.example.core.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.core.apps.InstalledAppsProvider
import com.example.core.database.repository.PolicyRepository
import com.example.core.policy.PolicyControls
import com.example.core.policy.PrivateDnsState
import com.example.core.usage.UsageRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import com.example.core.enrollment.EnrollmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
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
    private val usage: UsageRepository,
    private val enrollmentManager: EnrollmentManager,
    private val isDeviceOwner: () -> Boolean,
    private val applyControls: (PolicyControls) -> Unit,
    private val enforcePolicy: suspend () -> Unit
) {
    // The controller owns its scope so it keeps running no matter which caller (service, UI) started it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow("Sync not started yet")
    /** Human-readable last sync result, shown on the child dashboard so problems are visible without a debugger. */
    val status: StateFlow<String> = _status.asStateFlow()

    private fun now(): String = LocalTime.now().withNano(0).toString()

    // replay = 1: a reset detected by the background service before the UI exists is still delivered to it.
    private val _linkLost = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
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
                _status.value = "Sync error: ${e.message}"
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runSync() = coroutineScope {
        if (!gateway.isAvailable) { _status.value = "Firebase is not configured in this build"; return@coroutineScope }
        val device = repository.getDevice()
        // isPaired = ENROLLED *and* bound to a parent. The local placeholder row (empty parentId) is not a pairing.
        if (device == null || !device.isPaired) {
            _status.value = "Not paired with a parent"
            return@coroutineScope
        }
        _status.value = "Connecting..."
        _linkLost.resetReplayCache()   // a valid pairing exists again: forget any older "link lost" event
        val parentUid = device.parentId
        val childUid = device.deviceId
        // Firebase restores the saved sign-in a moment after start-up (slowly on old tablets): wait before judging.
        var waited = 0
        while (gateway.currentUid() == null && waited < SIGN_IN_WAIT_SECONDS) { delay(1_000L); waited++ }
        val signedInUid = gateway.currentUid()
        if (signedInUid != childUid) {
            // The sign-in this phone paired with is gone (signed out, app data cleared, restored backup...),
            // so the old pairing can never work again. Reset it so the phone goes back to "scan the QR code"
            // instead of being stuck on a dead link.
            Log.w(TAG, "Signed-in uid (${signedInUid?.take(6)}) does not match enrolled device id (${childUid.take(6)}); resetting the pairing.")
            _status.value = "Pairing reset: scan the parent's QR code again."
            enrollmentManager.unenrollDevice()
            _linkLost.emit(Unit)
            return@coroutineScope
        }
        // The acknowledged REMOTE policy version belongs to ONE pairing. A new pairing starts at remote version 1,
        // so a number left over from an earlier pairing would make every new policy look "stale" and be ignored.
        if (prefs.getString(KEY_ACKED_DEVICE, null) != childUid) {
            prefs.edit().putInt(KEY_ACKED, 0).putString(KEY_ACKED_DEVICE, childUid).apply()
        }

        launch { watchPolicy(parentUid, childUid) }
        launch { watchLink(parentUid, childUid) }
        launch { heartbeatLoop(parentUid, childUid) }
        launch { usageLoop(parentUid, childUid) }
        launch { inventoryLoop(parentUid, childUid) }
    }

    /**
     * Reports this (child) phone's real installed apps to the parent: right away, then again whenever the
     * list changes (install / uninstall), so the parent always sees the child's apps and never anything else.
     */
    private suspend fun inventoryLoop(parentUid: String, childUid: String) {
        var lastSent: Set<Pair<String, String>>? = null
        while (true) {
            try {
                val apps = installedApps.listLaunchableApps()
                val signature = apps.map { it.packageName to it.displayName }.toSet()
                if (apps.isNotEmpty() && signature != lastSent) {
                    gateway.uploadInventory(parentUid, childUid, apps)
                        .onSuccess { lastSent = signature }
                        .onFailure { Log.w(TAG, "Inventory upload failed", it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Inventory refresh failed", e)
            }
            delay(INVENTORY_INTERVAL_MS)
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
                _status.value = "Rules v${remote.version} applied at ${now()}"
                // Controls first: enforcement below reads the lockdown flag from the store.
                remote.controls?.let { c ->
                    try { applyControls(c) } catch (e: Exception) { Log.e(TAG, "Applying controls failed", e) }
                }
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
        var lastSentAt = 0L
        var lastDns: PrivateDnsState.Snapshot? = null
        while (true) {
            // Report right away when the real DNS state changes, otherwise on the normal interval.
            val dns = PrivateDnsState.read(context)
            val due = System.currentTimeMillis() - lastSentAt >= HEARTBEAT_INTERVAL_MS
            if (due || dns != lastDns) {
                sendHeartbeat(parentUid, childUid)
                lastSentAt = System.currentTimeMillis()
                lastDns = dns
            }
            delay(DNS_WATCH_INTERVAL_MS)
        }
    }

    /** Uploads today's per-app minutes every few minutes, only when something changed. */
    private suspend fun usageLoop(parentUid: String, childUid: String) {
        var lastSent: Map<String, Int>? = null
        var lastDate: String? = null
        while (true) {
            try {
                val today = LocalDate.now().toString()
                val minutes = usage.refreshTodayUsage().filterValues { it > 0 }
                if (minutes.isNotEmpty() && (minutes != lastSent || today != lastDate)) {
                    gateway.uploadUsage(parentUid, childUid, today, minutes)
                        .onSuccess { lastSent = minutes; lastDate = today }
                        .onFailure { Log.w(TAG, "Usage upload failed", it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Usage refresh failed", e)
            }
            delay(USAGE_INTERVAL_MS)
        }
    }

    private suspend fun sendHeartbeat(parentUid: String, childUid: String) {
        val dns = PrivateDnsState.read(context)
        gateway.sendHeartbeat(
            parentUid = parentUid,
            childUid = childUid,
            ackedPolicyVersion = prefs.getInt(KEY_ACKED, 0),
            isDeviceOwner = isDeviceOwner(),
            accessibilityEnabled = isAccessibilityEnabled(),
            appVersion = appVersion(),
            dnsActive = dns.active,
            dnsHost = dns.host
        ).onSuccess { _status.value = "Report sent at ${now()}" }
            .onFailure {
                Log.w(TAG, "Heartbeat failed", it)
                _status.value = "Report failed: ${it.message}"
            }
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
                _status.value = "Connection problem: ${e.message}"
                delay(RETRY_DELAY_MS)
            }
        }
    }

    companion object {
        private const val TAG = "ChildSyncController"
        private const val KEY_ACKED = "acked_remote_version"
        private const val KEY_ACKED_DEVICE = "acked_remote_version_device"
        private const val SIGN_IN_WAIT_SECONDS = 20
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L
        private const val DNS_WATCH_INTERVAL_MS = 60 * 1000L
        private const val RETRY_DELAY_MS = 30_000L
        private const val USAGE_INTERVAL_MS = 5 * 60 * 1000L
        private const val INVENTORY_INTERVAL_MS = 5 * 60 * 1000L
    }
}
