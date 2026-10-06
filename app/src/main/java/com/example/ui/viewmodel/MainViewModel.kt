package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.child.admin.AndroidDeviceOwnerManager
import com.example.child.admin.DeviceOwnerManager
import com.example.child.enforcement.DefaultPolicyEnforcementManager
import com.example.child.enforcement.PolicyEnforcementManager
import com.example.core.database.ChildDatabase
import com.example.core.database.entity.SyncAuditEntity
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.enrollment.DefaultEnrollmentManager
import com.example.core.enrollment.EnrollmentManager
import com.example.core.enrollment.QrCodeGenerator
import com.example.core.apps.InstalledApp
import com.example.core.model.AppPolicy
import com.example.core.model.ChildDevice
import com.example.core.model.EnrollmentStatus
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import com.example.core.security.AndroidPinSecurityManager
import com.example.core.security.PinSecurityManager
import com.example.core.security.PinVerificationResult
import com.example.core.policy.ControlsApplier
import com.example.core.policy.ControlsStore
import com.example.core.policy.PolicyControls
import com.example.core.sync.ChildStatusFormatter
import com.example.child.protection.DeviceProtectionManager
import com.example.core.sync.ChildSyncController
import com.example.core.sync.ChildSyncProvider
import com.example.core.sync.FirebaseSyncGateway
import com.example.core.sync.PairingQr
import com.example.core.sync.RemoteHeartbeat
import com.example.core.sync.SyncGateway
import com.example.core.usage.AndroidUsageRepository
import com.example.core.usage.UsageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class AppMode {
    ROLE_SELECTION,
    WELCOME,
    CHILD,
    PARENT
}

enum class ParentTab {
    DASHBOARD,
    APPS,
    HABITS,
    SCHEDULES,
    PAIRING,
    AUDIT
}

sealed interface PinDialogState {
    data object Hidden : PinDialogState
    data object SetupInitialPin : PinDialogState
    data class EnterPin(val reason: String, val attemptsLeft: Int? = null) : PinDialogState
    data class LockedOut(val secondsRemaining: Long) : PinDialogState
    data object ChangePin : PinDialogState
}

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val database: ChildDatabase = ChildDatabase.getInstance(application),
    private val policyRepository: PolicyRepository = RoomPolicyRepository(database),
    private val usageRepository: UsageRepository = AndroidUsageRepository(application, policyRepository),
    private val pinSecurityManager: PinSecurityManager = AndroidPinSecurityManager(application),
    private val deviceOwnerManager: DeviceOwnerManager = AndroidDeviceOwnerManager(application),
    private val enforcementManager: PolicyEnforcementManager = DefaultPolicyEnforcementManager(application, policyRepository, usageRepository, deviceOwnerManager),
    private val enrollmentManager: EnrollmentManager = DefaultEnrollmentManager(application, policyRepository),
    private val installedAppsProvider: com.example.core.apps.InstalledAppsProvider =
        com.example.core.apps.AndroidInstalledAppsProvider(application),
    private val syncGateway: SyncGateway = FirebaseSyncGateway(application)
) : AndroidViewModel(application) {

    private val rolePrefs = application.getSharedPreferences("device_role_prefs", Context.MODE_PRIVATE)

    private val _appMode = MutableStateFlow(
        when (rolePrefs.getString("device_role", null)) {
            // Parent role is only usable while a Google account is signed in.
            "PARENT" -> if (syncGateway.currentParent() != null) AppMode.PARENT else AppMode.WELCOME
            // Child phones never keep a Google login (they pair as anonymous users), so go straight in.
            "CHILD" -> AppMode.CHILD
            // First launch: Google sign-in screen first, then "Who's going to use this device?".
            else -> if (syncGateway.currentParent() != null) AppMode.ROLE_SELECTION else AppMode.WELCOME
        }
    )
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()


    private val _isChildConnectedToParent = MutableStateFlow(false)
    val isChildConnectedToParent: StateFlow<Boolean> = _isChildConnectedToParent.asStateFlow()

    private val _connectedParentName = MutableStateFlow("Parent's Phone")
    val connectedParentName: StateFlow<String> = _connectedParentName.asStateFlow()

    private val _showConnectDialog = MutableStateFlow(false)
    val showConnectDialog: StateFlow<Boolean> = _showConnectDialog.asStateFlow()

    private val _showProtectionDialog = MutableStateFlow(false)
    val showProtectionDialog: StateFlow<Boolean> = _showProtectionDialog.asStateFlow()

    private val _parentTab = MutableStateFlow(ParentTab.DASHBOARD)
    val parentTab: StateFlow<ParentTab> = _parentTab.asStateFlow()

    private val _pinDialogState = MutableStateFlow<PinDialogState>(PinDialogState.Hidden)
    val pinDialogState: StateFlow<PinDialogState> = _pinDialogState.asStateFlow()

    private val _isDeviceOwner = MutableStateFlow(false)
    val isDeviceOwner: StateFlow<Boolean> = _isDeviceOwner.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _qrPayloadJson = MutableStateFlow("")
    val qrPayloadJson: StateFlow<String> = _qrPayloadJson.asStateFlow()

    private val _qrRemainingSeconds = MutableStateFlow(0)
    val qrRemainingSeconds: StateFlow<Int> = _qrRemainingSeconds.asStateFlow()

    private val _instantLockdown = MutableStateFlow(false)
    val instantLockdown: StateFlow<Boolean> = _instantLockdown.asStateFlow()

    private val _isSupervised = MutableStateFlow(true)
    val isSupervised: StateFlow<Boolean> = _isSupervised.asStateFlow()

    private val _isCameraBlocked = MutableStateFlow(false)
    val isCameraBlocked: StateFlow<Boolean> = _isCameraBlocked.asStateFlow()

    private val _isInstallBlocked = MutableStateFlow(true)
    val isInstallBlocked: StateFlow<Boolean> = _isInstallBlocked.asStateFlow()

    private val _isMandatoryDnsEnforced = MutableStateFlow(true)
    val isMandatoryDnsEnforced: StateFlow<Boolean> = _isMandatoryDnsEnforced.asStateFlow()

    private val _enforcedDnsHost = MutableStateFlow("family-filter-dns.cleanbrowsing.org")
    val enforcedDnsHost: StateFlow<String> = _enforcedDnsHost.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val policy: StateFlow<Policy> = policyRepository.observeCurrentPolicy()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Policy(version = 1, updatedAtEpochMs = System.currentTimeMillis())
        )

    val todayUsage: StateFlow<Map<String, Int>> = policyRepository.observeTodayUsage(LocalDate.now().toString())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val recentAudits: StateFlow<List<SyncAuditEntity>> = policyRepository.observeRecentAudits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _deviceInfo = MutableStateFlow<ChildDevice?>(null)
    val deviceInfo: StateFlow<ChildDevice?> = _deviceInfo.asStateFlow()

    private var countdownJob: Job? = null
    private var qrCountdownJob: Job? = null

    // ---- backend sync state ----
    private val _childHeartbeat = MutableStateFlow<RemoteHeartbeat?>(null)

    private val _parentAccountLabel = MutableStateFlow(syncGateway.currentParent()?.let { it.email ?: it.uid })
    val parentAccountLabel: StateFlow<String?> = _parentAccountLabel.asStateFlow()

    private val _parentDisplayName = MutableStateFlow(syncGateway.currentParent()?.displayName)
    val parentDisplayName: StateFlow<String?> = _parentDisplayName.asStateFlow()

    private val _parentPhotoUrl = MutableStateFlow(syncGateway.currentParent()?.photoUrl)
    val parentPhotoUrl: StateFlow<String?> = _parentPhotoUrl.asStateFlow()

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    val childStatusLabel: StateFlow<String> = combine(_deviceInfo, _childHeartbeat, clock) { device, hb, now ->
        ChildStatusFormatter.format(device != null, hb, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Waiting for child connection...")

    // ---- multi-child (parent phone) ----
    // Every child linked to this parent. `deviceInfo` / `policy` / usage always describe the SELECTED child.
    private val _children = MutableStateFlow<List<ChildDevice>>(emptyList())
    val children: StateFlow<List<ChildDevice>> = _children.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String?>(rolePrefs.getString(SELECTED_CHILD_KEY, null))
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    private val _childHeartbeats = MutableStateFlow<Map<String, RemoteHeartbeat?>>(emptyMap())
    val childHeartbeats: StateFlow<Map<String, RemoteHeartbeat?>> = _childHeartbeats.asStateFlow()

    val childStatusLabels: StateFlow<Map<String, String>> = combine(_childHeartbeats, clock) { map, now ->
        map.mapValues { (_, hb) -> ChildStatusFormatter.format(true, hb, now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Edits made to the selected child's rules that were not sent yet. */
    private val _hasUnsentChanges = MutableStateFlow(false)
    val hasUnsentChanges: StateFlow<Boolean> = _hasUnsentChanges.asStateFlow()

    /** Child the parent wants to switch to while unsent edits exist (UI asks what to do). */
    private val _pendingChildSwitch = MutableStateFlow<String?>(null)
    val pendingChildSwitch: StateFlow<String?> = _pendingChildSwitch.asStateFlow()

    private val _isSwitchingChild = MutableStateFlow(false)
    val isSwitchingChild: StateFlow<Boolean> = _isSwitchingChild.asStateFlow()

    private var childrenLoaded = false

    private var parentSyncJob: Job? = null

    private val childSync: ChildSyncController = ChildSyncProvider.get(application)

    // Device-level switches (camera, installs, DNS, supervision, lockdown). Parent phone: desired state that is
    // pushed to the child. Child phone (or standalone use): applied to this phone through Device Owner.
    val childSyncStatus: StateFlow<String> = childSync.status

    private val controlsStore = ControlsStore(application)
    private val controlsApplier = ControlsApplier(deviceOwnerManager, controlsStore)

    init {
        viewModelScope.launch {
            childSync.linkLost.collect {
                // Only clear the UI when local enrollment has also been removed; this avoids
                // briefly losing the child state while the background reset is being persisted.
                val stillEnrolled = policyRepository.getDevice()?.isPaired == true
                if (!stillEnrolled) {
                    _isChildConnectedToParent.value = false
                    _deviceInfo.value = null
                    _statusMessage.value = "This device is no longer linked. Scan the parent's QR code to connect again."
                }
            }
        }
    }

    /** Child side: keep the foreground service (which hosts sync) running while paired. */
    private fun ensureChildServiceRunning() {
        DeviceProtectionManager.startProtectionService(getApplication())
        childSync.start()
    }

    private fun isParentRole(): Boolean = rolePrefs.getString("device_role", null) == "PARENT"

    init {
        // Independent of the rest of initialization: a failure or delay there must never stop sync from starting.
        viewModelScope.launch { ensureSyncInternal() }
        viewModelScope.launch {
            try {
                loadInitialData()
                if (!isParentRole()) enforcementManager.initializeDeviceEnforcement()
                refreshUsage()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in initialization", e)
            }
        }
    }

    private suspend fun loadInitialData() {
        try {
            val currentDevice = policyRepository.getDevice()
            _deviceInfo.value = currentDevice
            _isChildConnectedToParent.value = currentDevice?.isPaired == true
            if (currentDevice != null && currentDevice.isPaired) {
                _connectedParentName.value = "Parent's Phone"
            }
            refreshDeviceOwnerFlags()

            // Populate the policy with the apps that are really installed on this device
            syncInstalledApps()

            // Parent phones create a pairing QR only once signed in
            if (isParentRole() && syncGateway.currentParent() != null) generatePairingQr()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading initial data", e)
        }
    }

    /** Re-reads live Device Owner / restriction state from the system. */
    private fun refreshDeviceOwnerFlags() {
        val stored = controlsStore.get()
        _instantLockdown.value = stored.lockdown
        if (isParentRole()) {
            // Parent phone: show the settings it will send, and the CHILD's Device Owner status.
            _isDeviceOwner.value = _childHeartbeat.value?.isDeviceOwner ?: false
            _isSupervised.value = stored.supervised
            _isCameraBlocked.value = stored.cameraBlocked
            _isInstallBlocked.value = stored.installBlocked
            _isMandatoryDnsEnforced.value = stored.mandatoryDns
            _enforcedDnsHost.value = stored.dnsHost
        } else {
            _isDeviceOwner.value = deviceOwnerManager.isDeviceOwner()
            _isSupervised.value = deviceOwnerManager.isSupervisionActive()
            _isCameraBlocked.value = deviceOwnerManager.isCameraDisabled()
            _isInstallBlocked.value = deviceOwnerManager.isAppInstallBlocked()
            _isMandatoryDnsEnforced.value = deviceOwnerManager.isMandatoryDnsEnforced()
            _enforcedDnsHost.value = deviceOwnerManager.getEnforcedDnsHost()
        }
    }

    /**
     * Adds every launchable installed app that has no policy yet, as ALLOWED.
     * Existing policies are never modified or removed. The policy version is bumped
     * only when something was actually added.
     */
    private suspend fun syncInstalledApps() {
        // Only a CHILD phone lists its own apps. While the role is still undecided (first launch) or
        // PARENT, this phone's installed apps must never enter the rules: a parent's rules describe the
        // CHILD's apps (from the child's uploaded inventory).
        if (rolePrefs.getString("device_role", null) != "CHILD") return
        mergeNewApps(installedAppsProvider.listLaunchableApps())
    }

    private suspend fun mergeNewApps(candidates: List<InstalledApp>) {
        val currentPolicy = policyRepository.getCurrentPolicy()
        val newApps = candidates
            .filter { it.packageName !in currentPolicy.apps }
            .map { AppPolicy(it.packageName, it.displayName, RestrictionMode.ALLOWED) }
        if (newApps.isEmpty()) return

        val isFirstRun = currentPolicy.apps.isEmpty() && currentPolicy.schedules.isEmpty()
        val schedules = if (isFirstRun) defaultSchedules() else currentPolicy.schedules.values.toList()

        policyRepository.applyNewPolicyAtomic(
            Policy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                apps = currentPolicy.apps + newApps.associateBy { it.packageName },
                schedules = schedules.associateBy { it.id }
            )
        )
    }

    /**
     * Parent phone: makes the rules list match the CHILD's real installed apps.
     * - apps the child has but we don't know yet are added as ALLOWED;
     * - ALLOWED apps the child no longer has are dropped (stale / uninstalled);
     * - apps with a real rule (LIMITED / BLOCKED) are always kept, so a reinstall can never silently lose a block.
     */
    private suspend fun mergeChildInventory(apps: List<InstalledApp>) {
        if (apps.isEmpty()) return
        val inventory = apps.associateBy { it.packageName }
        val current = policyRepository.getCurrentPolicy()
        val kept = current.apps.filter { (pkg, rule) -> pkg in inventory || rule.mode != RestrictionMode.ALLOWED }
        val added = apps.filter { it.packageName !in current.apps }
            .map { AppPolicy(it.packageName, it.displayName, RestrictionMode.ALLOWED) }
        if (added.isEmpty() && kept.size == current.apps.size) return

        val isFirstRun = current.apps.isEmpty() && current.schedules.isEmpty()
        val schedules = if (isFirstRun) defaultSchedules() else current.schedules.values.toList()
        policyRepository.applyNewPolicyAtomic(
            Policy(
                version = current.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                apps = kept + added.associateBy { it.packageName },
                schedules = schedules.associateBy { it.id }
            )
        )
    }

    private fun defaultSchedules(): List<Schedule> = listOf(
        Schedule(
            id = "bedtime",
            name = "Bedtime Lockdown",
            startTime = TimeOfDay(21, 0),
            endTime = TimeOfDay(7, 0),
            activeDays = DayOfWeek.values().toSet(),
            enabled = false
        ),
        Schedule(
            id = "homework",
            name = "Study Hours",
            startTime = TimeOfDay(16, 0),
            endTime = TimeOfDay(18, 0),
            activeDays = setOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
            ),
            enabled = false
        )
    )

    private suspend fun ensureSyncInternal() {
        try {
            if (!syncGateway.isAvailable) return
            if (isParentRole()) {
                startParentObservers()
                return
            }
            val device = policyRepository.getDevice()
            if (device != null && device.isPaired) {
                ensureChildServiceRunning()
            } else {
                // Not (or no longer) paired: never keep showing "Linked" from stale in-memory state.
                _isChildConnectedToParent.value = false
                _deviceInfo.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not start sync", e)
        }
    }

    /** Called whenever the app comes to the foreground; starting is idempotent. */
    fun ensureSync() {
        viewModelScope.launch { ensureSyncInternal() }
    }

    private suspend fun retrying(block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MainViewModel", "Sync listener error, retrying", e)
                delay(30_000L)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startParentObservers() {
        parentSyncJob?.cancel()
        childrenLoaded = false
        val parent = syncGateway.currentParent() ?: return
        parentSyncJob = viewModelScope.launch {
            launch {
                retrying {
                    syncGateway.observeDevices().collect { list ->
                        val devices = list
                            .sortedWith(compareBy({ it.pairedAtEpochMs ?: Long.MAX_VALUE }, { it.deviceId }))
                            .map {
                                ChildDevice(
                                    deviceId = it.deviceId,
                                    parentId = parent.uid,
                                    deviceName = it.deviceName,
                                    appVersion = it.appVersion,
                                    lastSeenEpochMs = it.pairedAtEpochMs ?: 0L,
                                    policyVersion = 0,
                                    enrollmentStatus = EnrollmentStatus.ENROLLED
                                )
                            }
                        val previousCount = _children.value.size
                        val firstEmission = !childrenLoaded
                        childrenLoaded = true
                        _children.value = devices
                        _isChildConnectedToParent.value = devices.isNotEmpty()
                        // A pairing QR is single-use: once a child claimed it, show a fresh one for the next child.
                        if (!firstEmission && devices.size > previousCount) generatePairingQr()
                        reconcileSelection(devices)
                    }
                }
            }
            launch {
                retrying {
                    _children.map { l -> l.map { it.deviceId } }.distinctUntilChanged()
                        .flatMapLatest { ids ->
                            if (ids.isEmpty()) flowOf(emptyMap<String, RemoteHeartbeat?>())
                            else combine(ids.map { id -> syncGateway.observeHeartbeat(id).map { hb -> id to hb } }) { arr ->
                                arr.toMap()
                            }
                        }
                        .collect { _childHeartbeats.value = it }
                }
            }
            launch {
                retrying {
                    _deviceInfo.map { it?.deviceId }.distinctUntilChanged()
                        .flatMapLatest { id -> if (id == null) flowOf(null) else syncGateway.observeHeartbeat(id) }
                        .collect {
                            _childHeartbeat.value = it
                            _isDeviceOwner.value = it?.isDeviceOwner ?: false
                        }
                }
            }
            launch {
                retrying {
                    val deviceIds = _deviceInfo.map { it?.deviceId }.distinctUntilChanged()
                    val dates = clock.map { LocalDate.now().toString() }.distinctUntilChanged()
                    combine(deviceIds, dates) { id, date -> id to date }
                        .flatMapLatest { (id, date) ->
                            if (id == null) flowOf(null to date)
                            else syncGateway.observeUsage(id, date).map { it to date }
                        }
                        .collect { (minutes, date) ->
                            minutes?.forEach { (pkg, min) -> policyRepository.recordTodayUsage(pkg, date, min) }
                        }
                }
            }
            launch {
                retrying {
                    _deviceInfo.map { it?.deviceId }.distinctUntilChanged()
                        .flatMapLatest { id -> if (id == null) flowOf(null) else syncGateway.observeInventory(id) }
                        .collect { inv -> if (inv != null) mergeChildInventory(inv.apps) }
                }
            }
        }
    }

    // ------------------------------------------------------------ multi-child management

    /** Keeps the selected child valid and makes the local working copy belong to it. */
    private suspend fun reconcileSelection(devices: List<ChildDevice>) {
        if (_isSwitchingChild.value) return
        val target = devices.firstOrNull { it.deviceId == _selectedChildId.value } ?: devices.firstOrNull()
        if (target == null) {
            _selectedChildId.value = null
            _deviceInfo.value = null
            _hasUnsentChanges.value = false
            return
        }
        val workingId = rolePrefs.getString(WORKING_COPY_KEY, null)
        if (workingId == target.deviceId) {
            // Local rules were already loaded from the server for this exact child (or edited by the parent since).
            rolePrefs.edit()
                .putString(SELECTED_CHILD_KEY, target.deviceId)
                .apply()
            _selectedChildId.value = target.deviceId
            _deviceInfo.value = target
        } else {
            // Never adopt whatever is in the local database as this child's data: it may hold this very
            // phone's own apps/usage. Always load the child's real rules from the server first.
            _hasUnsentChanges.value = false
            // Offline right now? Keep trying instead of showing nothing (or, worse, stale local data).
            var attempts = 0
            while (!switchWorkingCopy(target) && attempts < 20 && _children.value.any { it.deviceId == target.deviceId }) {
                attempts++
                delay(15_000L)
            }
        }
    }

    /** Loads [target]'s rules from the server into the local working copy and makes it the selected child. */
    private suspend fun switchWorkingCopy(target: ChildDevice): Boolean {
        _isSwitchingChild.value = true
        try {
            val fetched = syncGateway.fetchPolicy(target.deviceId)
            if (fetched.isFailure) {
                _statusMessage.value = "Could not load ${target.deviceName}. Check your connection and try again."
                return false
            }
            val remote = fetched.getOrNull()
            // Stop the per-child observers while the working copy is swapped.
            _deviceInfo.value = null
            _childHeartbeat.value = null
            policyRepository.resetWorkingCopy(
                remote?.policy ?: Policy(version = 0, updatedAtEpochMs = System.currentTimeMillis())
            )
            controlsStore.set(remote?.controls ?: PolicyControls())
            refreshDeviceOwnerFlags()
            rolePrefs.edit()
                .putString(WORKING_COPY_KEY, target.deviceId)
                .putString(SELECTED_CHILD_KEY, target.deviceId)
                .apply()
            _selectedChildId.value = target.deviceId
            _hasUnsentChanges.value = false
            _deviceInfo.value = target
            return true
        } finally {
            _isSwitchingChild.value = false
        }
    }

    /** Parent picked another child. Unsent edits are never dropped silently. */
    fun requestSelectChild(childId: String) {
        if (!isParentRole() || childId == _selectedChildId.value) return
        if (_children.value.none { it.deviceId == childId }) return
        if (_hasUnsentChanges.value) {
            _pendingChildSwitch.value = childId
            return
        }
        viewModelScope.launch {
            _children.value.firstOrNull { it.deviceId == childId }?.let { switchWorkingCopy(it) }
        }
    }

    fun confirmChildSwitch(sendRulesFirst: Boolean) {
        val id = _pendingChildSwitch.value ?: return
        _pendingChildSwitch.value = null
        viewModelScope.launch {
            if (sendRulesFirst) {
                val current = _deviceInfo.value
                if (current != null && !pushRulesTo(current)) return@launch
            }
            _hasUnsentChanges.value = false
            _children.value.firstOrNull { it.deviceId == id }?.let { switchWorkingCopy(it) }
        }
    }

    fun cancelChildSwitch() {
        _pendingChildSwitch.value = null
    }

    /** Unlinks one child. Rules enforce that only the owning parent may delete the device document. */
    fun removeChild(childId: String) {
        viewModelScope.launch {
            if (!isParentRole()) return@launch
            val child = _children.value.firstOrNull { it.deviceId == childId } ?: return@launch
            syncGateway.unlinkDevice(childId)
                .onSuccess {
                    if (_pendingChildSwitch.value == childId) _pendingChildSwitch.value = null
                    if (_selectedChildId.value == childId) _hasUnsentChanges.value = false
                    _statusMessage.value = "${child.deviceName} was removed."
                }
                .onFailure { _statusMessage.value = "Could not remove ${child.deviceName}: ${it.message}" }
        }
    }

    fun renameChild(childId: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name.length > 40) {
            _statusMessage.value = "Name must be 1-40 characters."
            return
        }
        viewModelScope.launch {
            if (_children.value.none { it.deviceId == childId }) return@launch
            syncGateway.renameDevice(childId, name)
                .onFailure { _statusMessage.value = "Could not rename: ${it.message}" }
        }
    }

    /** Today's total minutes for one child (null until that child has reported). */
    fun observeChildTodayMinutes(childId: String): Flow<Int?> =
        syncGateway.observeUsage(childId, LocalDate.now().toString()).map { it?.values?.sum() }

    private fun markRulesEdited() {
        if (isParentRole()) _hasUnsentChanges.value = true
    }

    fun signInParent(activity: Activity) {
        viewModelScope.launch {
            syncGateway.signInParentWithGoogle(activity)
                .onSuccess { account ->
                    _parentAccountLabel.value = account.email ?: account.uid
                    _parentDisplayName.value = account.displayName
                    _parentPhotoUrl.value = account.photoUrl
                    // Signed in: now the parent role is confirmed (saves role, starts sync, makes QR).
                    selectDeviceRole(AppMode.PARENT)
                    _statusMessage.value = "Signed in as ${account.email ?: account.uid}."
                }
                .onFailure { e ->
                    Log.e("MainViewModel", "Google sign-in failed", e)
                    _statusMessage.value = "Google sign-in failed: ${e.message}"
                }
        }
    }

    fun signOutParent() {
        viewModelScope.launch {
            parentSyncJob?.cancel()
            syncGateway.signOut()
            _parentAccountLabel.value = null
            _parentDisplayName.value = null
            _parentPhotoUrl.value = null
            com.example.core.profile.ProfilePhotoLoader.clearCache(getApplication())
            _deviceInfo.value = null
            _childHeartbeat.value = null
            _children.value = emptyList()
            _childHeartbeats.value = emptyMap()
            _selectedChildId.value = null
            _pendingChildSwitch.value = null
            _hasUnsentChanges.value = false
            rolePrefs.edit().remove(SELECTED_CHILD_KEY).apply()
            _qrBitmap.value = null
            _qrPayloadJson.value = ""
            // Parent screens require Google sign-in, so go back to the login gate.
            if (_appMode.value == AppMode.PARENT) _appMode.value = AppMode.WELCOME
            _statusMessage.value = "Signed out."
        }
    }

    fun isPinConfigured(): Boolean = pinSecurityManager.isPinConfigured()

    fun requestSwitchToParentMode() {
        if (!pinSecurityManager.isPinConfigured()) {
            _pinDialogState.value = PinDialogState.SetupInitialPin
            return
        }

        val remainingLockout = pinSecurityManager.getRemainingLockoutSeconds()
        if (remainingLockout > 0) {
            startLockoutCountdown(remainingLockout)
            return
        }

        _pinDialogState.value = PinDialogState.EnterPin("Enter Parent PIN to unlock dashboard")
    }

    fun requestChangePin() {
        _pinDialogState.value = PinDialogState.ChangePin
    }

    fun switchToChildMode() {
        _appMode.value = AppMode.CHILD
        _pinDialogState.value = PinDialogState.Hidden
        _statusMessage.value = "Child Mode active."
    }

    fun selectParentTab(tab: ParentTab) {
        _parentTab.value = tab
    }

    fun submitPin(enteredPin: String) {
        val result = pinSecurityManager.verifyPin(enteredPin)
        when (result) {
            is PinVerificationResult.Success -> {
                _pinDialogState.value = PinDialogState.Hidden
                _appMode.value = AppMode.PARENT
                _statusMessage.value = "Parent Mode authenticated."
            }
            is PinVerificationResult.Incorrect -> {
                _pinDialogState.value = PinDialogState.EnterPin(
                    reason = "Incorrect PIN. Try again.",
                    attemptsLeft = result.attemptsRemainingBeforeLockout
                )
            }
            is PinVerificationResult.LockedOut -> {
                startLockoutCountdown(result.secondsRemaining)
            }
            is PinVerificationResult.NotConfigured -> {
                _pinDialogState.value = PinDialogState.SetupInitialPin
            }
        }
    }

    fun setupNewPin(pin: String) {
        if (pinSecurityManager.setupPin(pin)) {
            _pinDialogState.value = PinDialogState.Hidden
            _appMode.value = AppMode.PARENT
            _statusMessage.value = "Parent PIN created successfully."
        } else {
            _statusMessage.value = "PIN must be at least 4 digits."
        }
    }

    fun changePin(oldPin: String, newPin: String) {
        if (pinSecurityManager.changePin(oldPin, newPin)) {
            _pinDialogState.value = PinDialogState.Hidden
            _statusMessage.value = "PIN updated successfully."
        } else {
            _statusMessage.value = "Incorrect old PIN or invalid new PIN."
        }
    }

    fun dismissPinDialog() {
        _pinDialogState.value = PinDialogState.Hidden
    }

    private fun startLockoutCountdown(seconds: Long) {
        _pinDialogState.value = PinDialogState.LockedOut(seconds)
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var rem = seconds
            while (rem > 0) {
                _pinDialogState.value = PinDialogState.LockedOut(rem)
                delay(1000L)
                rem--
            }
            _pinDialogState.value = PinDialogState.EnterPin("Lockout ended. Enter Parent PIN.")
        }
    }

    fun updateAppRestriction(
        packageName: String,
        newMode: RestrictionMode,
        dailyLimitMinutes: Int? = null
    ) {
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val existingApp = currentPolicy.apps[packageName]
            val updatedApp = existingApp?.copy(
                mode = newMode,
                dailyLimitMinutes = dailyLimitMinutes
            ) ?: AppPolicy(
                packageName = packageName,
                displayName = packageName.substringAfterLast("."),
                mode = newMode,
                dailyLimitMinutes = dailyLimitMinutes
            )

            val updatedApps = currentPolicy.apps.toMutableMap()
            updatedApps[packageName] = updatedApp

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                apps = updatedApps
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Updated ${updatedApp.displayName}: ${updatedApp.mode.name}"
        }
    }

    fun toggleSchedule(scheduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val existing = currentPolicy.schedules[scheduleId] ?: return@launch
            val updated = existing.copy(enabled = enabled)

            val updatedSchedules = currentPolicy.schedules.toMutableMap()
            updatedSchedules[scheduleId] = updated

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                schedules = updatedSchedules
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Schedule '${updated.name}' ${if (enabled) "enabled" else "disabled"}."
        }
    }

    fun addOrUpdateSchedule(schedule: Schedule) {
        if (schedule.startTime == schedule.endTime) {
            _statusMessage.value = "Start and end time cannot be the same."
            return
        }
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val updatedSchedules = currentPolicy.schedules.toMutableMap()
            updatedSchedules[schedule.id] = schedule

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                schedules = updatedSchedules
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Schedule '${schedule.name}' saved."
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val updatedSchedules = currentPolicy.schedules.toMutableMap()
            updatedSchedules.remove(scheduleId)

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                schedules = updatedSchedules
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Schedule removed."
        }
    }

    /** Saves the new controls; on a parent phone they wait for the push, otherwise they are applied here. */
    private fun commitControls(updated: PolicyControls) {
        _isSupervised.value = updated.supervised
        _isCameraBlocked.value = updated.cameraBlocked
        _isInstallBlocked.value = updated.installBlocked
        _isMandatoryDnsEnforced.value = updated.mandatoryDns
        _enforcedDnsHost.value = updated.dnsHost
        _instantLockdown.value = updated.lockdown
        if (isParentRole()) { controlsStore.set(updated); markRulesEdited() } else controlsApplier.apply(updated)
    }

    private fun parentHint() = if (isParentRole()) " Press 'Send rules' to apply on the child." else ""

    fun toggleInstantLockdown(locked: Boolean) {
        viewModelScope.launch {
            // A flag, not a rewrite of the app rules: releasing the lockdown restores every rule exactly.
            commitControls(controlsStore.get().copy(lockdown = locked))
            policyRepository.logSyncAudit(
                event = "INSTANT_LOCKDOWN",
                version = policy.value.version,
                details = if (locked) "Instant lockdown enabled" else "Instant lockdown released"
            )
            if (isParentRole()) {
                // Lockdown must reach the child immediately, so it is sent without waiting for a manual push.
                pushSyncToChild()
            } else {
                enforcementManager.enforceCurrentPolicy()
                _statusMessage.value = if (locked) "Instant Lockdown Enabled! All apps paused." else "Lockdown Released. Normal limits restored."
            }
        }
    }

    fun toggleSupervision(active: Boolean) {
        viewModelScope.launch {
            commitControls(controlsStore.get().copy(supervised = active))
            policyRepository.logSyncAudit(
                event = "SUPERVISION_TOGGLED",
                version = policy.value.version,
                details = if (active) "Parental Supervision enabled with tamper protection" else "Supervision paused"
            )
            if (!isParentRole()) enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = (if (active) "Parental Supervision Active." else "Supervision Paused.") + parentHint()
        }
    }

    fun toggleCameraRestriction(blocked: Boolean) {
        viewModelScope.launch {
            commitControls(controlsStore.get().copy(cameraBlocked = blocked))
            policyRepository.logSyncAudit(
                event = "CAMERA_RESTRICTION",
                version = policy.value.version,
                details = if (blocked) "Camera disabled on child device" else "Camera access allowed"
            )
            _statusMessage.value = (if (blocked) "Camera access blocked." else "Camera access restored.") + parentHint()
        }
    }

    fun toggleInstallRestriction(blocked: Boolean) {
        viewModelScope.launch {
            commitControls(controlsStore.get().copy(installBlocked = blocked))
            policyRepository.logSyncAudit(
                event = "APP_INSTALL_RESTRICTION",
                version = policy.value.version,
                details = if (blocked) "App installation blocked" else "App installation permitted"
            )
            _statusMessage.value = (if (blocked) "App installation blocked." else "App installation permitted.") + parentHint()
        }
    }

    fun setMandatoryDns(enabled: Boolean, dnsHost: String) {
        viewModelScope.launch {
            commitControls(controlsStore.get().copy(mandatoryDns = enabled, dnsHost = dnsHost))
            policyRepository.logSyncAudit(
                event = "MANDATORY_DNS_CONFIGURED",
                version = policy.value.version,
                details = if (enabled) "Mandatory DNS locked to $dnsHost (Private DNS Settings restricted)" else "Mandatory DNS disabled"
            )
            if (isParentRole()) {
                // DNS must reach the child without a manual 'Send rules' press.
                pushSyncToChild()
                _statusMessage.value = if (enabled) "Mandatory DNS sent to child ($dnsHost)" else "Mandatory DNS turn-off sent to child."
            } else {
                _statusMessage.value = when {
                    !enabled -> "Mandatory DNS Disabled."
                    deviceOwnerManager.isDeviceOwner() -> "Mandatory DNS Active ($dnsHost)"
                    else -> "DNS not locked: Device Owner is not set on this phone."
                }
            }
        }
    }

    fun setAppTimeWindowBlock(
        packageName: String,
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int,
        enabled: Boolean = true
    ) {
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val app = currentPolicy.apps[packageName]
            val appName = app?.displayName ?: packageName.substringAfterLast(".")
            val scheduleId = "block_${packageName.replace(".", "_").takeLast(20)}"

            val schedule = Schedule(
                id = scheduleId,
                name = "$appName Block Window",
                startTime = TimeOfDay(startHour, startMin),
                endTime = TimeOfDay(endHour, endMin),
                activeDays = DayOfWeek.values().toSet(),
                enabled = enabled,
                blockedPackages = setOf(packageName)
            )

            val updatedSchedules = currentPolicy.schedules.toMutableMap()
            updatedSchedules[scheduleId] = schedule

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                schedules = updatedSchedules
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Scheduled block for $appName: %02d:%02d to %02d:%02d".format(startHour, startMin, endHour, endMin)
        }
    }

    fun removeAppTimeWindowBlock(packageName: String) {
        viewModelScope.launch {
            val currentPolicy = policyRepository.getCurrentPolicy()
            val scheduleId = "block_${packageName.replace(".", "_").takeLast(20)}"

            val updatedSchedules = currentPolicy.schedules.toMutableMap()
            updatedSchedules.remove(scheduleId)

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                schedules = updatedSchedules
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            markRulesEdited()
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = "Removed scheduled time block for app."
        }
    }

    /** Stable random identity of this parent device, created once and persisted. */
    private fun getOrCreateParentId(): String {
        rolePrefs.getString("parent_id", null)?.let { return it }
        val id = "parent_" + java.util.UUID.randomUUID().toString().take(12)
        rolePrefs.edit().putString("parent_id", id).apply()
        return id
    }

    fun generatePairingQr() {
        viewModelScope.launch {
            if (!syncGateway.isAvailable) {
                _statusMessage.value = "Firebase is not configured in this build (google-services.json missing)."
                return@launch
            }
            syncGateway.createPairingToken(validityMinutes = PAIRING_VALIDITY_MINUTES)
                .onSuccess { token ->
                    val json = PairingQr(token.token, token.parentUid, "Parent's Phone").toJson()
                    _qrPayloadJson.value = json
                    _qrBitmap.value = QrCodeGenerator.generateQrBitmap(json, 512)
                    val total = (PAIRING_VALIDITY_MINUTES * 60).toInt()
                    _qrRemainingSeconds.value = total

                    qrCountdownJob?.cancel()
                    qrCountdownJob = viewModelScope.launch {
                        var rem = total
                        while (rem > 0) {
                            delay(1000L)
                            rem--
                            _qrRemainingSeconds.value = rem
                        }
                        _qrBitmap.value = null
                        _qrPayloadJson.value = ""
                    }
                }
                .onFailure { e ->
                    _qrBitmap.value = null
                    _qrPayloadJson.value = ""
                    _statusMessage.value = e.message ?: "Could not create pairing QR."
                }
        }
    }

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    /** First screen: Google sign-in. On success the user continues to the Parent/Kid choice. */
    fun signInFromWelcome(activity: Activity) {
        if (_isSigningIn.value) return
        _isSigningIn.value = true
        viewModelScope.launch {
            // Signing in with Google replaces this phone's anonymous identity, and that identity IS the
            // pairing. So a phone that is already paired as a child must be unpaired first.
            val pairedChild = rolePrefs.getString("device_role", null) == "CHILD" &&
                policyRepository.getDevice()?.isPaired == true
            if (pairedChild) {
                _statusMessage.value = "This phone is paired as a child device. Use Disconnect/Unpair first if you want to use it as a parent phone."
                _isSigningIn.value = false
                return@launch
            }
            syncGateway.signInParentWithGoogle(activity)
                .onSuccess { account ->
                    _parentAccountLabel.value = account.email ?: account.uid
                    _parentDisplayName.value = account.displayName
                    _parentPhotoUrl.value = account.photoUrl
                    _appMode.value = AppMode.ROLE_SELECTION
                    _statusMessage.value = "Signed in as ${account.email ?: account.uid}."
                }
                .onFailure { e ->
                    Log.e("MainViewModel", "Google sign-in failed", e)
                    _statusMessage.value = "Google sign-in failed: ${e.message}"
                }
            _isSigningIn.value = false
        }
    }

    /**
     * Second screen: Parent or Kid.
     * Parent needs a Google login. Kid: the Google login only proved who is setting the phone up; it is
     * removed from this phone, because the backend identifies a child phone as an ANONYMOUS user.
     */
    fun chooseRole(role: AppMode) {
        if (role == AppMode.PARENT) {
            if (syncGateway.currentParent() == null) _appMode.value = AppMode.WELCOME
            else selectDeviceRole(AppMode.PARENT)
        } else {
            viewModelScope.launch {
                if (syncGateway.currentParent() != null) {
                    parentSyncJob?.cancel()
                    syncGateway.signOut()
                    _parentAccountLabel.value = null
                    _parentDisplayName.value = null
                    _parentPhotoUrl.value = null
                    com.example.core.profile.ProfilePhotoLoader.clearCache(getApplication())
                    _childHeartbeat.value = null
                    _qrBitmap.value = null
                    _qrPayloadJson.value = ""
                }
                selectDeviceRole(AppMode.CHILD)
            }
        }
    }

    fun selectDeviceRole(role: AppMode) {
        rolePrefs.edit().putString("device_role", role.name).apply()
        _appMode.value = role
        if (role == AppMode.PARENT) {
            _parentTab.value = ParentTab.DASHBOARD
            childSync.stop()
            if (syncGateway.currentParent() != null) {
                startParentObservers()
                generatePairingQr()
            }
        } else if (role == AppMode.CHILD) {
            parentSyncJob?.cancel()
            viewModelScope.launch {
                try { syncInstalledApps() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Log.e("MainViewModel", "Listing installed apps failed", e)
                }
            }
            if (syncGateway.isAvailable && _deviceInfo.value != null) ensureChildServiceRunning()
        }
        _statusMessage.value = if (role == AppMode.PARENT) "Configured as Parent Device" else "Configured as Child Device"
    }

    fun openRoleSelection() {
        _appMode.value = AppMode.ROLE_SELECTION
    }

    fun openConnectDialog() {
        _showConnectDialog.value = true
    }

    fun closeConnectDialog() {
        _showConnectDialog.value = false
    }

    fun openProtectionSetup() {
        _showProtectionDialog.value = true
    }

    fun closeProtectionSetup() {
        _showProtectionDialog.value = false
    }

    fun connectChildWithQr(qrContent: String) {
        viewModelScope.launch {
            val qr = PairingQr.fromJson(qrContent)
            if (qr == null) {
                _statusMessage.value = "Invalid QR code."
                return@launch
            }
            if (!syncGateway.isAvailable) {
                _statusMessage.value = "Firebase is not configured in this build."
                return@launch
            }
            val name = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Child Phone"
            val version = try {
                getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "?"
            } catch (e: Exception) { "?" }

            syncGateway.claimPairing(qr, name, version)
                .onSuccess { claim ->
                    val device = ChildDevice(
                        deviceId = claim.childUid,
                        parentId = claim.parentUid,
                        deviceName = name,
                        appVersion = version,
                        lastSeenEpochMs = System.currentTimeMillis(),
                        policyVersion = 0,
                        enrollmentStatus = EnrollmentStatus.ENROLLED
                    )
                    policyRepository.saveDevice(device)
                    policyRepository.logSyncAudit("DEVICE_PAIRED", 0, "Paired with parent ${claim.parentUid}")
                    _deviceInfo.value = device
                    _isChildConnectedToParent.value = true
                    _connectedParentName.value = qr.parentName
                    _showConnectDialog.value = false
                    _statusMessage.value = "Successfully paired with parent."
                    // Restart sync so it runs with THIS pairing's parent/child ids (start() alone is a no-op
                    // while an older pairing's loops are still running).
                    childSync.stop()
                    ensureChildServiceRunning()
                }
                .onFailure { e ->
                    Log.e("MainViewModel", "Pairing failed", e)
                    _statusMessage.value = "Pairing failed: ${e.message}"
                }
        }
    }

    fun unpairChildDevice() {
        viewModelScope.launch {
            if (isParentRole()) {
                val id = _deviceInfo.value?.deviceId ?: return@launch
                syncGateway.unlinkDevice(id)
                    .onSuccess { _hasUnsentChanges.value = false; _statusMessage.value = "Child device unlinked." }
                    .onFailure { _statusMessage.value = "Unlink failed: ${it.message}" }
                return@launch
            }
            childSync.stop()
            enrollmentManager.unenrollDevice()
            _isChildConnectedToParent.value = false
            _deviceInfo.value = null
            _statusMessage.value = "Device disconnected from parent. (The parent phone still lists it until the parent unlinks it.)"
        }
    }

    fun pushSyncToChild() {
        val device = _deviceInfo.value
        if (device == null) {
            _statusMessage.value = "No child device is linked."
            return
        }
        viewModelScope.launch { pushRulesTo(device) }
    }

    /** Sends the current working copy to [device] (always one of this parent's own children). */
    private suspend fun pushRulesTo(device: ChildDevice): Boolean {
        if (_children.value.none { it.deviceId == device.deviceId }) {
            _statusMessage.value = "That child is no longer linked."
            return false
        }
        val local = policyRepository.getCurrentPolicy()
        var ok = false
        syncGateway.pushPolicy(device.deviceId, local, controlsStore.get())
            .onSuccess { remoteVersion ->
                policyRepository.logSyncAudit("POLICY_PUSHED", remoteVersion, "Pushed policy as remote v$remoteVersion to ${device.deviceId}")
                _hasUnsentChanges.value = false
                _statusMessage.value = "Rules sent to ${device.deviceName} (v$remoteVersion). It applies them when online."
                ok = true
            }
            .onFailure { e ->
                Log.e("MainViewModel", "Push failed", e)
                _statusMessage.value = "Could not send rules: ${e.message}"
            }
        return ok
    }

    fun refreshUsage() {
        viewModelScope.launch {
            try { refreshDeviceOwnerFlags() } catch (e: Exception) { Log.e("MainViewModel", "Device state refresh failed", e) }
            // A parent phone shows the CHILD's usage (synced from Firestore); it must never measure
            // or enforce its own usage against the child's policy.
            if (!isParentRole()) {
                usageRepository.refreshTodayUsage()
                enforcementManager.enforceCurrentPolicy()
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private companion object {
        const val PAIRING_VALIDITY_MINUTES = 10L
        const val SELECTED_CHILD_KEY = "selected_child_id"
        const val WORKING_COPY_KEY = "working_copy_child_id_v2"   // v2: forces one clean reload from the server
    }
}
