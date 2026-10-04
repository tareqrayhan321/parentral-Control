package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.core.sync.AppCheckInstaller
import com.example.ui.components.ConnectToParentDialog
import com.example.ui.components.PinDialog
import com.example.ui.screens.ChildDashboardScreen
import com.example.ui.screens.DeviceProtectionSetupDialog
import com.example.ui.screens.DeviceRoleSelectionScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppMode
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PinDialogState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.ensureSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppCheckInstaller.install(this)

        setContent {
            MyApplicationTheme {
                val appMode by viewModel.appMode.collectAsState()
                val parentTab by viewModel.parentTab.collectAsState()
                val pinDialogState by viewModel.pinDialogState.collectAsState()
                val policy by viewModel.policy.collectAsState()
                val todayUsage by viewModel.todayUsage.collectAsState()
                val deviceInfo by viewModel.deviceInfo.collectAsState()
                val isDeviceOwner by viewModel.isDeviceOwner.collectAsState()
                val isSupervised by viewModel.isSupervised.collectAsState()
                val isCameraBlocked by viewModel.isCameraBlocked.collectAsState()
                val isInstallBlocked by viewModel.isInstallBlocked.collectAsState()
                val isMandatoryDnsEnforced by viewModel.isMandatoryDnsEnforced.collectAsState()
                val enforcedDnsHost by viewModel.enforcedDnsHost.collectAsState()
                val instantLockdown by viewModel.instantLockdown.collectAsState()
                val qrBitmap by viewModel.qrBitmap.collectAsState()
                val qrRemainingSeconds by viewModel.qrRemainingSeconds.collectAsState()
                val qrPayloadJson by viewModel.qrPayloadJson.collectAsState()
                val audits by viewModel.recentAudits.collectAsState()
                val parentAccountLabel by viewModel.parentAccountLabel.collectAsState()
                val childSyncStatus by viewModel.childSyncStatus.collectAsState()
                val childStatusLabel by viewModel.childStatusLabel.collectAsState()
                val isChildConnectedToParent by viewModel.isChildConnectedToParent.collectAsState()
                val connectedParentName by viewModel.connectedParentName.collectAsState()
                val showConnectDialog by viewModel.showConnectDialog.collectAsState()
                val showProtectionDialog by viewModel.showProtectionDialog.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(statusMessage) {
                    statusMessage?.let {
                        snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
                        viewModel.clearStatusMessage()
                    }
                }

                // If in Parent Mode, back press returns safely to Child Mode
                BackHandler(enabled = appMode == AppMode.PARENT) {
                    viewModel.switchToChildMode()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (appMode) {
                            AppMode.ROLE_SELECTION -> {
                                DeviceRoleSelectionScreen(
                                    onRoleSelected = { viewModel.selectDeviceRole(it) }
                                )
                            }
                            AppMode.CHILD -> {
                                ChildDashboardScreen(
                                    policy = policy,
                                    todayUsage = todayUsage,
                                    isDeviceOwner = isDeviceOwner,
                                    isSupervised = isSupervised,
                                    isCameraBlocked = isCameraBlocked,
                                    isMandatoryDnsEnforced = isMandatoryDnsEnforced,
                                    enforcedDnsHost = enforcedDnsHost,
                                    deviceName = deviceInfo?.deviceName,
                                    isChildConnectedToParent = isChildConnectedToParent,
                                    connectedParentName = connectedParentName,
                                    onOpenConnectDialog = { viewModel.openConnectDialog() },
                                    onUnpairDevice = { viewModel.unpairChildDevice() },
                                    onSwitchRoleRequested = { viewModel.openRoleSelection() },
                                    onOpenProtectionSetup = { viewModel.openProtectionSetup() },
                                    onOpenParentLogin = { viewModel.requestSwitchToParentMode() },
                                    onRefreshUsage = { viewModel.refreshUsage() },
                                    syncStatus = childSyncStatus
                                )
                            }
                            AppMode.PARENT -> {
                                ParentDashboardScreen(
                                    currentTab = parentTab,
                                    policy = policy,
                                    todayUsage = todayUsage,
                                    deviceInfo = deviceInfo,
                                    isDeviceOwner = isDeviceOwner,
                                    isSupervised = isSupervised,
                                    isCameraBlocked = isCameraBlocked,
                                    isInstallBlocked = isInstallBlocked,
                                    isMandatoryDnsEnforced = isMandatoryDnsEnforced,
                                    enforcedDnsHost = enforcedDnsHost,
                                    instantLockdown = instantLockdown,
                                    qrBitmap = qrBitmap,
                                    qrRemainingSeconds = qrRemainingSeconds,
                                    audits = audits,
                                    parentAccountLabel = parentAccountLabel,
                                    childStatusLabel = childStatusLabel,
                                    onSignIn = { viewModel.signInParent(this@MainActivity) },
                                    onSignOut = { viewModel.signOutParent() },
                                    onSelectTab = { viewModel.selectParentTab(it) },
                                    onSwitchToChildMode = { viewModel.switchToChildMode() },
                                    onToggleSupervision = { viewModel.toggleSupervision(it) },
                                    onToggleCameraRestriction = { viewModel.toggleCameraRestriction(it) },
                                    onToggleInstallRestriction = { viewModel.toggleInstallRestriction(it) },
                                    onSetMandatoryDns = { enabled, host ->
                                        viewModel.setMandatoryDns(enabled, host)
                                    },
                                    onToggleInstantLockdown = { viewModel.toggleInstantLockdown(it) },
                                    onUpdateAppRestriction = { pkg, mode, limit ->
                                        viewModel.updateAppRestriction(pkg, mode, limit)
                                    },
                                    onSetAppTimeWindowBlock = { pkg, sh, sm, eh, em ->
                                        viewModel.setAppTimeWindowBlock(pkg, sh, sm, eh, em)
                                    },
                                    onRemoveAppTimeWindowBlock = { pkg ->
                                        viewModel.removeAppTimeWindowBlock(pkg)
                                    },
                                    onToggleSchedule = { id, enabled ->
                                        viewModel.toggleSchedule(id, enabled)
                                    },
                                    onAddSchedule = { viewModel.addOrUpdateSchedule(it) },
                                    onDeleteSchedule = { viewModel.deleteSchedule(it) },
                                    onRegenerateQr = { viewModel.generatePairingQr() },
                                    onPushSync = { viewModel.pushSyncToChild() },
                                    onUnpairChild = { viewModel.unpairChildDevice() },
                                    onSwitchRoleRequested = { viewModel.openRoleSelection() },
                                    onChangePinRequested = { viewModel.requestChangePin() }
                                )
                            }
                        }

                        // Connect to Parent Phone Dialog on Child Device
                        if (showConnectDialog) {
                            ConnectToParentDialog(
                                onDismiss = { viewModel.closeConnectDialog() },
                                onConnectWithQr = { qr ->
                                    viewModel.connectChildWithQr(qr)
                                }
                            )
                        }

                        // Full Device Protection Permissions Wizard Dialog
                        if (showProtectionDialog) {
                            DeviceProtectionSetupDialog(
                                onDismiss = { viewModel.closeProtectionSetup() }
                            )
                        }

                        // PIN Protection Dialog (Setup, Unlock, Lockout)
                        PinDialog(
                            state = pinDialogState,
                            onDismiss = { viewModel.dismissPinDialog() },
                            onSubmitPin = { viewModel.submitPin(it) },
                            onSetupPin = { viewModel.setupNewPin(it) },
                            onChangePin = { old, new -> viewModel.changePin(old, new) }
                        )
                    }
                }
            }
        }
    }
}
