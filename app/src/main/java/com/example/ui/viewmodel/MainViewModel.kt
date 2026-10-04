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
    CHILD,
    PARENT
}

enum class ParentTab {
    DASHBOARD,
    APPS,
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
            "PARENT" -> AppMode.PARENT
            "CHILD" -> AppMode.CHILD
            else -> AppMode.CHILD
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

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    val childStatusLabel: StateFlow<String> = combine(_deviceInfo, _childHeartbeat, clock) { device, hb, now ->
        ChildStatusFormatter.format(device != null, hb, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Waiting for child connection...")

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
                _isChildConnectedToParent.value = false
                _deviceInfo.value = null
                _statusMessage.value = "The parent unlinked this device."
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
            _isChildConnectedToParent.value = currentDevice?.enrollmentStatus == EnrollmentStatus.ENROLLED
            if (currentDevice != null && currentDevice.enrollmentStatus == EnrollmentStatus.ENROLLED) {
                _connectedParentName.value = "Parent's Phone (${currentDevice.parentId})"
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
        // A parent phone's policy describes the CHILD's apps (from the child's inventory),
        // never the parent's own installed apps.
        if (isParentRole()) return
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
            if (device != null && device.enrollmentStatus == EnrollmentStatus.ENROLLED) ensureChildServiceRunning()
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
        val parent = syncGateway.currentParent() ?: return
        parentSyncJob = viewModelScope.launch {
            launch {
                retrying {
                    syncGateway.observeDevices().collect { list ->
                        val d = list.firstOrNull()
                        _deviceInfo.value = d?.let {
                            ChildDevice(
                                deviceId = it.deviceId,
                                parentId = parent.uid,
                                deviceName = it.deviceName,
                                appVersion = it.appVersion,
                                lastSeenEpochMs = 0L,
                                policyVersion = 0,
                                enrollmentStatus = EnrollmentStatus.ENROLLED
                            )
                        }
                        _isChildConnectedToParent.value = d != null
                    }
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
                        .collect { inv -> if (inv != null) mergeNewApps(inv.apps) }
                }
            }
        }
    }

    fun signInParent(activity: Activity) {
        viewModelScope.launch {
            syncGateway.signInParentWithGoogle(activity)
                .onSuccess { account ->
                    _parentAccountLabel.value = account.email ?: account.uid
                    _statusMessage.value = "Signed in as ${account.email ?: account.uid}."
                    startParentObservers()
                    generatePairingQr()
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
            _deviceInfo.value = null
            _childHeartbeat.value = null
            _qrBitmap.value = null
            _qrPayloadJson.value = ""
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
        if (isParentRole()) controlsStore.set(updated) else controlsApplier.apply(updated)
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
            _statusMessage.value = (if (enabled) "Mandatory DNS Active ($dnsHost)" else "Mandatory DNS Disabled.") + parentHint()
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
                    .onSuccess { _statusMessage.value = "Child device unlinked." }
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
        viewModelScope.launch {
            val local = policyRepository.getCurrentPolicy()
            syncGateway.pushPolicy(device.deviceId, local, controlsStore.get())
                .onSuccess { remoteVersion ->
                    policyRepository.logSyncAudit("POLICY_PUSHED", remoteVersion, "Pushed policy as remote v$remoteVersion")
                    _statusMessage.value = "Rules sent (v$remoteVersion). The child applies them when it is online."
                }
                .onFailure { e ->
                    Log.e("MainViewModel", "Push failed", e)
                    _statusMessage.value = "Could not send rules: ${e.message}"
                }
        }
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
    }
}
