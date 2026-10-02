package com.example.ui.viewmodel

import android.app.Application
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
import com.example.core.enrollment.PairingPayload
import com.example.core.enrollment.QrCodeGenerator
import com.example.core.model.AppPolicy
import com.example.core.model.ChildDevice
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import com.example.core.security.AndroidPinSecurityManager
import com.example.core.security.PinSecurityManager
import com.example.core.security.PinVerificationResult
import com.example.core.usage.AndroidUsageRepository
import com.example.core.usage.UsageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class AppMode {
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
    private val enrollmentManager: EnrollmentManager = DefaultEnrollmentManager(application, policyRepository)
) : AndroidViewModel(application) {

    private val _appMode = MutableStateFlow(AppMode.CHILD)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

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

    private val _qrRemainingSeconds = MutableStateFlow(900)
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

    init {
        viewModelScope.launch {
            try {
                loadInitialData()
                enforcementManager.initializeDeviceEnforcement()
                refreshUsage()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in initialization", e)
            }
        }
    }

    private suspend fun loadInitialData() {
        try {
            _deviceInfo.value = policyRepository.getDevice()
            _isDeviceOwner.value = deviceOwnerManager.isDeviceOwner()
            _isSupervised.value = deviceOwnerManager.isSupervisionActive()
            _isCameraBlocked.value = deviceOwnerManager.isCameraDisabled()
            _isInstallBlocked.value = deviceOwnerManager.isAppInstallBlocked()
            _isMandatoryDnsEnforced.value = deviceOwnerManager.isMandatoryDnsEnforced()
            _enforcedDnsHost.value = deviceOwnerManager.getEnforcedDnsHost()

            // If no apps exist in initial policy, seed common popular apps with default profiles
            val currentPolicy = policyRepository.getCurrentPolicy()
            if (currentPolicy.apps.isEmpty()) {
                seedDefaultApps()
            }

            // Generate initial pairing QR
            generatePairingQr()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading initial data", e)
        }
    }

    private suspend fun seedDefaultApps() {
        val sampleApps = listOf(
            AppPolicy("com.google.android.youtube", "YouTube", RestrictionMode.LIMITED, 45),
            AppPolicy("com.zhiliaoapp.musically", "TikTok", RestrictionMode.BLOCKED),
            AppPolicy("com.instagram.android", "Instagram", RestrictionMode.LIMITED, 30),
            AppPolicy("com.facebook.katana", "Facebook", RestrictionMode.BLOCKED),
            AppPolicy("com.android.chrome", "Chrome Browser", RestrictionMode.ALLOWED),
            AppPolicy("com.google.android.apps.docs", "Google Docs", RestrictionMode.ALLOWED),
            AppPolicy("com.duolingo", "Duolingo", RestrictionMode.ALLOWED),
            AppPolicy("com.roblox.client", "Roblox", RestrictionMode.LIMITED, 60),
            AppPolicy("com.mojang.minecraftpe", "Minecraft", RestrictionMode.LIMITED, 60)
        )
        val initialSchedules = listOf(
            Schedule(
                id = "bedtime",
                name = "Bedtime Lockdown",
                startTime = TimeOfDay(21, 0),
                endTime = TimeOfDay(7, 0),
                activeDays = DayOfWeek.values().toSet(),
                enabled = true
            ),
            Schedule(
                id = "homework",
                name = "Study Hours",
                startTime = TimeOfDay(16, 0),
                endTime = TimeOfDay(18, 0),
                activeDays = setOf(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY
                ),
                enabled = true
            )
        )

        val newPolicy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = sampleApps.associateBy { it.packageName },
            schedules = initialSchedules.associateBy { it.id }
        )
        policyRepository.applyNewPolicyAtomic(newPolicy)

        // Seed some sample usage for realism
        val todayStr = LocalDate.now().toString()
        policyRepository.recordTodayUsage("com.google.android.youtube", todayStr, 25)
        policyRepository.recordTodayUsage("com.instagram.android", todayStr, 32) // Exceeded!
        policyRepository.recordTodayUsage("com.duolingo", todayStr, 15)
        policyRepository.recordTodayUsage("com.android.chrome", todayStr, 10)
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

    fun toggleInstantLockdown(locked: Boolean) {
        viewModelScope.launch {
            _instantLockdown.value = locked
            val currentPolicy = policyRepository.getCurrentPolicy()

            // When instant lockdown is active, all non-system apps are blocked
            val updatedApps = currentPolicy.apps.mapValues { (_, app) ->
                if (locked) app.copy(mode = RestrictionMode.BLOCKED)
                else app.copy(mode = if (app.dailyLimitMinutes != null) RestrictionMode.LIMITED else RestrictionMode.ALLOWED)
            }

            val newPolicy = currentPolicy.copy(
                version = currentPolicy.version + 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                apps = updatedApps
            )

            policyRepository.applyNewPolicyAtomic(newPolicy)
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = if (locked) "Instant Lockdown Enabled! All apps paused." else "Lockdown Released. Normal limits restored."
        }
    }

    fun toggleSupervision(active: Boolean) {
        viewModelScope.launch {
            _isSupervised.value = active
            deviceOwnerManager.setSupervisionActive(active)
            policyRepository.logSyncAudit(
                event = "SUPERVISION_TOGGLED",
                version = policy.value.version,
                details = if (active) "Parental Supervision enabled with tamper protection" else "Supervision paused"
            )
            enforcementManager.enforceCurrentPolicy()
            _statusMessage.value = if (active) "Parental Supervision Active." else "Supervision Paused."
        }
    }

    fun toggleCameraRestriction(blocked: Boolean) {
        viewModelScope.launch {
            _isCameraBlocked.value = blocked
            deviceOwnerManager.setCameraDisabled(blocked)
            policyRepository.logSyncAudit(
                event = "CAMERA_RESTRICTION",
                version = policy.value.version,
                details = if (blocked) "Camera disabled on child device" else "Camera access allowed"
            )
            _statusMessage.value = if (blocked) "Camera access blocked." else "Camera access restored."
        }
    }

    fun toggleInstallRestriction(blocked: Boolean) {
        viewModelScope.launch {
            _isInstallBlocked.value = blocked
            deviceOwnerManager.setAppInstallBlocked(blocked)
            policyRepository.logSyncAudit(
                event = "APP_INSTALL_RESTRICTION",
                version = policy.value.version,
                details = if (blocked) "App installation blocked" else "App installation permitted"
            )
            _statusMessage.value = if (blocked) "App installation blocked." else "App installation permitted."
        }
    }

    fun setMandatoryDns(enabled: Boolean, dnsHost: String) {
        viewModelScope.launch {
            _isMandatoryDnsEnforced.value = enabled
            _enforcedDnsHost.value = dnsHost
            deviceOwnerManager.setMandatoryDns(enabled, dnsHost)
            policyRepository.logSyncAudit(
                event = "MANDATORY_DNS_CONFIGURED",
                version = policy.value.version,
                details = if (enabled) "Mandatory DNS locked to $dnsHost (Private DNS Settings restricted)" else "Mandatory DNS disabled"
            )
            _statusMessage.value = if (enabled) "Mandatory DNS Active ($dnsHost)" else "Mandatory DNS Disabled."
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

    fun generatePairingQr() {
        try {
            val payload = PairingPayload.create(
                parentId = "parent_tareq",
                childDeviceName = "Child Tablet",
                validityMinutes = 15
            )
            val json = payload.toJson()
            _qrPayloadJson.value = json
            _qrBitmap.value = QrCodeGenerator.generateQrBitmap(json, 512)
            _qrRemainingSeconds.value = 900

            qrCountdownJob?.cancel()
            qrCountdownJob = viewModelScope.launch {
                var rem = 900
                while (rem > 0) {
                    delay(1000L)
                    rem--
                    _qrRemainingSeconds.value = rem
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error generating pairing QR", e)
        }
    }

    fun simulateEnrollmentWithQr(qrContent: String) {
        viewModelScope.launch {
            val result = enrollmentManager.processEnrollmentQr(qrContent)
            when (result) {
                is com.example.core.enrollment.EnrollmentResult.Success -> {
                    _deviceInfo.value = result.device
                    _statusMessage.value = "Device paired successfully as '${result.device.deviceName}'!"
                }
                is com.example.core.enrollment.EnrollmentResult.Expired -> {
                    _statusMessage.value = "Pairing QR has expired. Please regenerate."
                }
                is com.example.core.enrollment.EnrollmentResult.SignatureMismatch -> {
                    _statusMessage.value = "QR signature verification failed."
                }
                is com.example.core.enrollment.EnrollmentResult.InvalidQr -> {
                    _statusMessage.value = "Invalid QR code format."
                }
            }
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            usageRepository.refreshTodayUsage()
            enforcementManager.enforceCurrentPolicy()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
