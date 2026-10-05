package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.entity.SyncAuditEntity
import com.example.core.model.AppPolicy
import com.example.core.model.ChildDevice
import com.example.core.model.DnsProvider
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.sync.RemoteHeartbeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.example.core.model.TimeOfDay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import com.example.ui.theme.StatusAllowed
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusLimited
import com.example.ui.viewmodel.ParentTab
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    currentTab: ParentTab,
    policy: Policy,
    todayUsage: Map<String, Int>,
    deviceInfo: ChildDevice?,
    isDeviceOwner: Boolean,
    isSupervised: Boolean,
    isCameraBlocked: Boolean,
    isInstallBlocked: Boolean,
    isMandatoryDnsEnforced: Boolean = true,
    enforcedDnsHost: String = "family-filter-dns.cleanbrowsing.org",
    instantLockdown: Boolean,
    qrBitmap: Bitmap?,
    qrRemainingSeconds: Int,
    audits: List<SyncAuditEntity>,
    onSelectTab: (ParentTab) -> Unit,
    onSwitchToChildMode: () -> Unit,
    onToggleSupervision: (Boolean) -> Unit,
    onToggleCameraRestriction: (Boolean) -> Unit,
    onToggleInstallRestriction: (Boolean) -> Unit,
    onSetMandatoryDns: (Boolean, String) -> Unit,
    onToggleInstantLockdown: (Boolean) -> Unit,
    onUpdateAppRestriction: (String, RestrictionMode, Int?) -> Unit,
    onSetAppTimeWindowBlock: (String, Int, Int, Int, Int) -> Unit,
    onRemoveAppTimeWindowBlock: (String) -> Unit,
    onToggleSchedule: (String, Boolean) -> Unit,
    onAddSchedule: (Schedule) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    parentAccountLabel: String? = null,
    parentName: String? = null,
    parentPhotoUrl: String? = null,
    childStatusLabel: String = "",
    children: List<ChildDevice> = emptyList(),
    selectedChildId: String? = null,
    childStatusLabels: Map<String, String> = emptyMap(),
    childHeartbeats: Map<String, RemoteHeartbeat?> = emptyMap(),
    isSwitchingChild: Boolean = false,
    pendingChildSwitch: String? = null,
    onSelectChild: (String) -> Unit = {},
    onRemoveChild: (String) -> Unit = {},
    onRenameChild: (String, String) -> Unit = { _, _ -> },
    onConfirmChildSwitch: (Boolean) -> Unit = {},
    onCancelChildSwitch: () -> Unit = {},
    observeChildMinutes: (String) -> Flow<Int?> = { emptyFlow() },
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPushSync: () -> Unit = {},
    onUnpairChild: () -> Unit = {},
    onSwitchRoleRequested: () -> Unit = {},
    onRegenerateQr: () -> Unit,
    onChangePinRequested: () -> Unit
) {
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    // True while the full-screen "Add habit" form is open: it needs the whole screen (no floating nav bar).
    var habitFormOpen by remember { mutableStateOf(false) }
    val fullScreenHabitForm = currentTab == ParentTab.HABITS && habitFormOpen

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            // Home draws its own gradient header; other tabs get a simple title bar (no "Child Mode" button).
            if (currentTab != ParentTab.DASHBOARD && currentTab != ParentTab.HABITS) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                ParentTab.APPS -> "Apps"
                                ParentTab.HABITS -> "Habits"
                                ParentTab.SCHEDULES -> "Schedules"
                                ParentTab.PAIRING -> "Account & pairing"
                                ParentTab.AUDIT -> "Activity"
                                else -> ""
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onSelectTab(ParentTab.DASHBOARD) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (currentTab == ParentTab.SCHEDULES) {
                FloatingActionButton(
                    onClick = { showAddScheduleDialog = true },
                    modifier = Modifier.padding(bottom = 76.dp).testTag("add_schedule_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
          // Home scrolls under the floating bar; other tabs keep clear of it.
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = if (currentTab == ParentTab.DASHBOARD || fullScreenHabitForm) 0.dp else 84.dp)
          ) {
            when (currentTab) {
                ParentTab.DASHBOARD -> ParentHomeTab(
                    parentName = parentName,
                    parentEmail = parentAccountLabel,
                    parentPhotoUrl = parentPhotoUrl,
                    deviceInfo = deviceInfo,
                    childStatusLabel = childStatusLabel,
                    policy = policy,
                    todayUsage = todayUsage,
                    isDeviceOwner = isDeviceOwner,
                    isSupervised = isSupervised,
                    isCameraBlocked = isCameraBlocked,
                    isInstallBlocked = isInstallBlocked,
                    isMandatoryDnsEnforced = isMandatoryDnsEnforced,
                    enforcedDnsHost = enforcedDnsHost,
                    instantLockdown = instantLockdown,
                    qrBitmap = qrBitmap,
                    qrRemainingSeconds = qrRemainingSeconds,
                    onToggleSupervision = onToggleSupervision,
                    onToggleCameraRestriction = onToggleCameraRestriction,
                    onToggleInstallRestriction = onToggleInstallRestriction,
                    onSetMandatoryDns = onSetMandatoryDns,
                    onToggleInstantLockdown = onToggleInstantLockdown,
                    onPushSync = onPushSync,
                    onUnpairChild = onUnpairChild,
                    onRegenerateQr = onRegenerateQr,
                    onOpenApps = { onSelectTab(ParentTab.APPS) },
                    onOpenAccount = { onSelectTab(ParentTab.PAIRING) },
                    onChangePin = onChangePinRequested,
                    onSwitchRole = onSwitchRoleRequested,
                    onSignOut = onSignOut,
                    children = children,
                    selectedChildId = selectedChildId,
                    childStatusLabels = childStatusLabels,
                    childHeartbeats = childHeartbeats,
                    isSwitchingChild = isSwitchingChild,
                    pendingChildSwitch = pendingChildSwitch,
                    onSelectChild = onSelectChild,
                    onRemoveChild = onRemoveChild,
                    onRenameChild = onRenameChild,
                    onConfirmChildSwitch = onConfirmChildSwitch,
                    onCancelChildSwitch = onCancelChildSwitch,
                    observeChildMinutes = observeChildMinutes
                )
                ParentTab.HABITS -> HabitsTabContent(onFormVisibilityChange = { habitFormOpen = it })
                ParentTab.APPS -> AppsTabContent(
                    policy = policy,
                    todayUsage = todayUsage,
                    onUpdateAppRestriction = onUpdateAppRestriction,
                    onSetAppTimeWindowBlock = onSetAppTimeWindowBlock,
                    onRemoveAppTimeWindowBlock = onRemoveAppTimeWindowBlock
                )
                ParentTab.SCHEDULES -> SchedulesTabContent(
                    schedules = policy.schedules.values.toList(),
                    allApps = policy.apps.values.toList(),
                    onToggleSchedule = onToggleSchedule,
                    onDeleteSchedule = onDeleteSchedule
                )
                ParentTab.PAIRING -> PairingTabContent(
                    parentAccountLabel = parentAccountLabel,
                    parentPhotoUrl = parentPhotoUrl,
                    childStatusLabel = childStatusLabel,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    deviceInfo = deviceInfo,
                    qrBitmap = qrBitmap,
                    qrRemainingSeconds = qrRemainingSeconds,
                    onRegenerateQr = onRegenerateQr,
                    onPushSync = onPushSync,
                    onUnpairChild = onUnpairChild,
                    children = children,
                    selectedChildId = selectedChildId,
                    childStatusLabels = childStatusLabels,
                    onSelectChild = onSelectChild,
                    onRemoveChild = onRemoveChild
                )
                ParentTab.AUDIT -> AuditTabContent(
                    audits = audits,
                    onChangePinRequested = onChangePinRequested
                )
            }
          }
          if (!fullScreenHabitForm) {
              FloatingNavBar(
                  currentTab = currentTab,
                  onSelectTab = onSelectTab,
                  modifier = Modifier.align(Alignment.BottomCenter)
              )
          }
        }
    }

    if (showAddScheduleDialog) {
        AddScheduleDialog(
            allApps = policy.apps.values.toList(),
            onDismiss = { showAddScheduleDialog = false },
            onConfirm = { schedule ->
                onAddSchedule(schedule)
                showAddScheduleDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTabContent(
    deviceInfo: ChildDevice?,
    isDeviceOwner: Boolean,
    isSupervised: Boolean,
    isCameraBlocked: Boolean,
    isInstallBlocked: Boolean,
    isMandatoryDnsEnforced: Boolean,
    enforcedDnsHost: String,
    instantLockdown: Boolean,
    policy: Policy,
    todayUsage: Map<String, Int>,
    childStatusLabel: String = "",
    onPushSync: () -> Unit = {},
    onToggleSupervision: (Boolean) -> Unit,
    onToggleCameraRestriction: (Boolean) -> Unit,
    onToggleInstallRestriction: (Boolean) -> Unit,
    onSetMandatoryDns: (Boolean, String) -> Unit,
    onToggleInstantLockdown: (Boolean) -> Unit
) {
    val totalMinutes = todayUsage.values.sum()
    val blockedCount = policy.apps.values.count { it.mode == RestrictionMode.BLOCKED }
    val limitedCount = policy.apps.values.count { it.mode == RestrictionMode.LIMITED }
    val allowedCount = policy.apps.values.count { it.mode == RestrictionMode.ALLOWED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Child summary: who, online state, today's usage, send rules
        item {
            ChildSummaryCard(
                deviceInfo = deviceInfo,
                statusLabel = childStatusLabel,
                policy = policy,
                todayUsage = todayUsage,
                instantLockdown = instantLockdown,
                onToggleInstantLockdown = onToggleInstantLockdown,
                onPushSync = onPushSync
            )
        }

        // Supervision System Master Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSupervised) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (isSupervised) StatusAllowed else Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Supervision",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Parental Supervision System",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isSupervised) "Active • Supervised by Parent" else "Supervision Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSupervised) StatusAllowed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Switch(
                            checked = isSupervised,
                            onCheckedChange = onToggleSupervision,
                            modifier = Modifier.testTag("supervision_switch")
                        )
                    }

                    if (isSupervised) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Hardware & System Controls",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Camera Restriction Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Disable Camera Access",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Block hardware camera on child device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isCameraBlocked,
                                onCheckedChange = onToggleCameraRestriction
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // App Install Restriction Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Block App Installations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Prevent downloading/sideloading new APKs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isInstallBlocked,
                                onCheckedChange = onToggleInstallRestriction
                            )
                        }
                    }
                }
            }
        }

        // Mandatory Family DNS Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMandatoryDnsEnforced) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (isMandatoryDnsEnforced) StatusAllowed else Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "DNS Filtering",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Mandatory DNS Internet Filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isMandatoryDnsEnforced) "Active • Adult Websites Blocked" else "Internet Filtering Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isMandatoryDnsEnforced) StatusAllowed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Switch(
                            checked = isMandatoryDnsEnforced,
                            onCheckedChange = { onSetMandatoryDns(it, enforcedDnsHost) },
                            modifier = Modifier.testTag("mandatory_dns_switch")
                        )
                    }

                    if (isMandatoryDnsEnforced) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Active DNS Host: $enforcedDnsHost",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select Family-Safe DNS Filtering Provider:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DnsProvider.ALL_PROVIDERS.forEach { provider ->
                                val isSelected = enforcedDnsHost == provider.host
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetMandatoryDns(true, provider.host) },
                                    label = { Text(provider.name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        val currentProvider = DnsProvider.ALL_PROVIDERS.firstOrNull { it.host == enforcedDnsHost }
                        if (currentProvider != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = currentProvider.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = StatusAllowed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "OS Locked: Private DNS settings blocked for child.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusAllowed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = deviceInfo?.deviceName ?: "Tahmid's Tablet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Policy Version v${policy.version} • Status: ${deviceInfo?.enrollmentStatus?.name ?: "ENROLLED"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDeviceOwner) StatusAllowed.copy(alpha = 0.15f) else StatusLimited.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isDeviceOwner) "Device Owner Active" else "Standard Admin",
                                color = if (isDeviceOwner) StatusAllowed else StatusLimited,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Instant Bedtime / Lockdown Switch Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (instantLockdown) StatusBlocked.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (instantLockdown) StatusBlocked else MaterialTheme.colorScheme.primary
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (instantLockdown) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lockdown",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Instant Lockdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (instantLockdown) "All apps currently paused." else "Immediately pause all apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = instantLockdown,
                        onCheckedChange = onToggleInstantLockdown,
                        modifier = Modifier.testTag("instant_lockdown_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StatusBlocked,
                            checkedTrackColor = StatusBlocked.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Stats Grid
        item {
            Text(
                text = "Rules Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Blocked",
                    value = "$blockedCount",
                    color = StatusBlocked,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Time Limited",
                    value = "$limitedCount",
                    color = StatusLimited,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Allowed",
                    value = "$allowedCount",
                    color = StatusAllowed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Screen Time Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Total Usage",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${totalMinutes / 60}h ${totalMinutes % 60}m",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.HourglassBottom,
                        contentDescription = "Usage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildSummaryCard(
    deviceInfo: ChildDevice?,
    statusLabel: String,
    policy: Policy,
    todayUsage: Map<String, Int>,
    instantLockdown: Boolean,
    onToggleInstantLockdown: (Boolean) -> Unit,
    onPushSync: () -> Unit
) {
    val totalMinutes = todayUsage.values.sum()
    val limited = policy.apps.values.filter { it.mode == RestrictionMode.LIMITED && (it.dailyLimitMinutes ?: 0) > 0 }
    val budget = limited.sumOf { it.dailyLimitMinutes ?: 0 }
    val usedOfBudget = limited.sumOf { minOf(todayUsage[it.packageName] ?: 0, it.dailyLimitMinutes ?: 0) }
    val fraction = if (budget > 0) (usedOfBudget.toFloat() / budget).coerceIn(0f, 1f) else 0f

    val track = MaterialTheme.colorScheme.outlineVariant
    val ringColor = when {
        fraction >= 0.9f -> StatusBlocked
        fraction >= 0.7f -> StatusLimited
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = deviceInfo?.deviceName?.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceInfo?.deviceName ?: "কোনো সন্তানের ফোন যুক্ত নেই",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (deviceInfo != null) statusLabel else "জোড়া ট্যাবে গিয়ে QR তৈরি করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (deviceInfo != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(92.dp)) {
                            val stroke = 11.dp.toPx()
                            val topLeft = Offset(stroke / 2, stroke / 2)
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke)
                            )
                            drawArc(
                                color = ringColor, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "%d:%02d".format(totalMinutes / 60, totalMinutes % 60),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "আজকের স্ক্রিন টাইম",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                totalMinutes == 0 -> "ব্যবহারের তথ্য এখনো সিঙ্ক হয়নি"
                                budget > 0 -> "সীমাযুক্ত অ্যাপে ${(fraction * 100).toInt()}% ব্যবহার"
                                else -> "কোনো সময়সীমা সেট করা নেই"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onToggleInstantLockdown(!instantLockdown) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lockdown_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            if (instantLockdown) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (instantLockdown) "লক খুলুন" else "এখনই লক", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onPushSync,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("push_sync_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নিয়ম পাঠান", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppsTabContent(
    policy: Policy,
    todayUsage: Map<String, Int>,
    onUpdateAppRestriction: (String, RestrictionMode, Int?) -> Unit,
    onSetAppTimeWindowBlock: (String, Int, Int, Int, Int) -> Unit,
    onRemoveAppTimeWindowBlock: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = policy.apps.values.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_input")
            )
        }

        items(filteredApps, key = { it.packageName }) { app ->
            val used = todayUsage[app.packageName] ?: 0
            val appSchedule = policy.schedules.values.firstOrNull { it.blockedPackages.contains(app.packageName) }

            ParentAppControlCard(
                app = app,
                usedMinutes = used,
                activeTimeBlockSchedule = appSchedule,
                onUpdate = { newMode, limit ->
                    onUpdateAppRestriction(app.packageName, newMode, limit)
                },
                onSetTimeBlock = { sh, sm, eh, em ->
                    onSetAppTimeWindowBlock(app.packageName, sh, sm, eh, em)
                },
                onRemoveTimeBlock = {
                    onRemoveAppTimeWindowBlock(app.packageName)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ParentAppControlCard(
    app: AppPolicy,
    usedMinutes: Int,
    activeTimeBlockSchedule: Schedule? = null,
    onUpdate: (RestrictionMode, Int?) -> Unit,
    onSetTimeBlock: (startHour: Int, startMin: Int, endHour: Int, endMin: Int) -> Unit,
    onRemoveTimeBlock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var currentLimit by remember(app) { mutableStateOf((app.dailyLimitMinutes ?: 60).toFloat()) }
    var showTimeBlockDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.displayName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${usedMinutes}m used today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Done" else "Configure")
                }
            }

            // Scheduled Block Window Badge if present
            if (activeTimeBlockSchedule != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusBlocked.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = StatusBlocked,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Blocked from ${activeTimeBlockSchedule.startTime} to ${activeTimeBlockSchedule.endTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusBlocked,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            color = StatusBlocked,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { onRemoveTimeBlock() }
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = app.mode == RestrictionMode.ALLOWED,
                    onClick = { onUpdate(RestrictionMode.ALLOWED, null) },
                    label = { Text("Allow") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusAllowed.copy(alpha = 0.2f),
                        selectedLabelColor = StatusAllowed
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = app.mode == RestrictionMode.LIMITED,
                    onClick = { onUpdate(RestrictionMode.LIMITED, currentLimit.toInt()) },
                    label = { Text("Limit") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusLimited.copy(alpha = 0.2f),
                        selectedLabelColor = StatusLimited
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = app.mode == RestrictionMode.BLOCKED,
                    onClick = { onUpdate(RestrictionMode.BLOCKED, null) },
                    label = { Text("Block") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusBlocked.copy(alpha = 0.2f),
                        selectedLabelColor = StatusBlocked
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (expanded || app.mode == RestrictionMode.LIMITED) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Daily Time Limit",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${currentLimit.toInt()} mins / day",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentLimit,
                        onValueChange = { currentLimit = it },
                        onValueChangeFinished = {
                            if (app.mode == RestrictionMode.LIMITED) {
                                onUpdate(RestrictionMode.LIMITED, currentLimit.toInt())
                            }
                        },
                        valueRange = 15f..240f,
                        steps = 14
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button to add specific time window block (e.g. 14:00 to 18:00)
                    OutlinedButton(
                        onClick = { showTimeBlockDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeTimeBlockSchedule != null) "Change Scheduled Time Block" else "Block During Specific Hours (e.g. 14:00 - 17:00)",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showTimeBlockDialog) {
        AppTimeWindowDialog(
            appName = app.displayName,
            initialStartHour = activeTimeBlockSchedule?.startTime?.hour ?: 14,
            initialStartMin = activeTimeBlockSchedule?.startTime?.minute ?: 0,
            initialEndHour = activeTimeBlockSchedule?.endTime?.hour ?: 18,
            initialEndMin = activeTimeBlockSchedule?.endTime?.minute ?: 0,
            onDismiss = { showTimeBlockDialog = false },
            onConfirm = { sh, sm, eh, em ->
                onSetTimeBlock(sh, sm, eh, em)
                showTimeBlockDialog = false
            }
        )
    }
}

@Composable
fun AppTimeWindowDialog(
    appName: String,
    initialStartHour: Int,
    initialStartMin: Int,
    initialEndHour: Int,
    initialEndMin: Int,
    onDismiss: () -> Unit,
    onConfirm: (startHour: Int, startMin: Int, endHour: Int, endMin: Int) -> Unit
) {
    var startHour by remember { mutableStateOf(initialStartHour.toString()) }
    var startMin by remember { mutableStateOf("%02d".format(initialStartMin)) }
    var endHour by remember { mutableStateOf(initialEndHour.toString()) }
    var endMin by remember { mutableStateOf("%02d".format(initialEndMin)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Block $appName During Hours") },
        text = {
            Column {
                Text(
                    text = "Specify the exact time range when $appName will be blocked automatically on this device:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Start Blocking At (24-Hour format):", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = startMin,
                        onValueChange = { startMin = it },
                        label = { Text("Minute (0-59)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "End Blocking At:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endMin,
                        onValueChange = { endMin = it },
                        label = { Text("Minute (0-59)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = startHour.toIntOrNull()
                    val sm = startMin.toIntOrNull()
                    val eh = endHour.toIntOrNull()
                    val em = endMin.toIntOrNull()

                    if (sh == null || sh !in 0..23 || sm == null || sm !in 0..59) {
                        error = "Please enter valid start time (0-23 hours, 0-59 mins)"
                    } else if (eh == null || eh !in 0..23 || em == null || em !in 0..59) {
                        error = "Please enter valid end time (0-23 hours, 0-59 mins)"
                    } else {
                        onConfirm(sh, sm, eh, em)
                    }
                }
            ) {
                Text("Set Time Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SchedulesTabContent(
    schedules: List<Schedule>,
    allApps: List<AppPolicy>,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Automated Family Schedules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Devices lock automatically during these hours without consuming battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(schedules, key = { it.id }) { schedule ->
            val targetLabel = if (schedule.blockedPackages.isEmpty()) {
                "Applies to: All Apps"
            } else {
                val appNames = schedule.blockedPackages.mapNotNull { pkg ->
                    allApps.firstOrNull { it.packageName == pkg }?.displayName ?: pkg.substringAfterLast(".")
                }
                "Blocks: ${appNames.joinToString(", ")}"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = schedule.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${schedule.startTime} to ${schedule.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = targetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = schedule.activeDays.joinToString(", ") { it.name.take(3) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = schedule.enabled,
                            onCheckedChange = { onToggleSchedule(schedule.id, it) }
                        )
                        IconButton(onClick = { onDeleteSchedule(schedule.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Schedule",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PairingTabContent(
    parentAccountLabel: String?,
    parentPhotoUrl: String? = null,
    childStatusLabel: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    deviceInfo: ChildDevice?,
    qrBitmap: Bitmap?,
    qrRemainingSeconds: Int,
    onRegenerateQr: () -> Unit,
    onPushSync: () -> Unit,
    onUnpairChild: () -> Unit,
    children: List<ChildDevice> = emptyList(),
    selectedChildId: String? = null,
    childStatusLabels: Map<String, String> = emptyMap(),
    onSelectChild: (String) -> Unit = {},
    onRemoveChild: (String) -> Unit = {}
) {
    var unlinkTarget by remember { mutableStateOf<ChildDevice?>(null) }
    val minutes = qrRemainingSeconds / 60
    val seconds = qrRemainingSeconds % 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Child Device Pairing & Connection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "প্রথমে Google দিয়ে সাইন-ইন করুন, তারপর সন্তানের ফোন থেকে নিচের QR কোডটি স্ক্যান করুন।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Parent Google account card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (parentAccountLabel == null) {
                        Text(
                            text = "অভিভাবক অ্যাকাউন্ট (Parent Account)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "সন্তানের ফোন যুক্ত করতে Google অ্যাকাউন্টে সাইন-ইন করা প্রয়োজন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier.testTag("google_sign_in_button")
                        ) {
                            Text("Sign in with Google")
                        }
                    } else {
                        com.example.ui.components.ProfileAvatar(
                            photoUrl = parentPhotoUrl,
                            name = parentAccountLabel,
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Signed in as",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = parentAccountLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onSignOut) { Text("Sign out", fontSize = 12.sp) }
                    }
                }
            }
        }

        // Connected children (one row per linked child device)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (deviceInfo != null) StatusAllowed.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val shown = if (children.isNotEmpty()) children else listOfNotNull(deviceInfo)
                    Text(
                        text = if (shown.isEmpty()) "No Child Phone Connected"
                        else "Connected children (${shown.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    shown.forEach { child ->
                        val isSelected = child.deviceId == (selectedChildId ?: deviceInfo?.deviceId)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectChild(child.deviceId) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(StatusAllowed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = child.deviceName + if (isSelected && shown.size > 1) "  • managing" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isSelected) childStatusLabel
                                    else childStatusLabels[child.deviceId] ?: "Linked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { unlinkTarget = child },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Unlink", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (deviceInfo != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onPushSync,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync All Rules to Child (নিয়মাবলী আপডেট পাঠান)")
                        }
                    }
                }
            }
        }

        // QR Code Section
        item {
            Text(
                text = "অথবা QR কোড স্ক্যান করুন (Or Scan QR Code):",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = when {
                                parentAccountLabel == null -> "আগে Google দিয়ে সাইন-ইন করুন"
                                qrRemainingSeconds <= 0 -> "কোডের মেয়াদ শেষ বা ব্যবহৃত হয়েছে। নিচের বাটনে নতুন কোড তৈরি করুন।"
                                else -> "QR কোড তৈরি হচ্ছে..."
                            },
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "QR Expires in %02d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRegenerateQr,
                    modifier = Modifier.testTag("regenerate_qr_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Regenerate Code & QR")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android Enterprise Device Owner Setup (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To enable non-removable OS protection and app suspension via ADB:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "adb shell dpm set-device-owner com.aistudio.parentalcontrol.pxmz/com.example.child.admin.ChildDeviceAdminReceiver",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    unlinkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text("Unlink ${target.deviceName}?") },
            text = { Text("Rules will stop applying and this phone will be removed from your account. Other children are not affected.") },
            confirmButton = {
                TextButton(onClick = { unlinkTarget = null; onRemoveChild(target.deviceId) }) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AuditTabContent(
    audits: List<SyncAuditEntity>,
    onChangePinRequested: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Security & Audit Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onChangePinRequested,
                    modifier = Modifier.testTag("change_pin_button")
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change PIN", fontSize = 12.sp)
                }
            }
        }

        if (audits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No audit entries recorded yet.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(audits, key = { it.id }) { audit ->
                val timeStr = formatter.format(Instant.ofEpochMilli(audit.timestamp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = audit.event,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!audit.details.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = audit.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScheduleDialog(
    allApps: List<AppPolicy>,
    onDismiss: () -> Unit,
    onConfirm: (Schedule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("21") }
    var startMin by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("07") }
    var endMin by remember { mutableStateOf("00") }
    var applyToAll by remember { mutableStateOf(true) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Family Schedule") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Schedule Name (e.g. Study Time)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it },
                        label = { Text("Start Hr") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = startMin,
                        onValueChange = { startMin = it },
                        label = { Text("Start Min") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it },
                        label = { Text("End Hr") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endMin,
                        onValueChange = { endMin = it },
                        label = { Text("End Min") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Applies to:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = applyToAll,
                        onClick = { applyToAll = true },
                        label = { Text("All Apps") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !applyToAll,
                        onClick = { applyToAll = false },
                        label = { Text("Specific Apps") }
                    )
                }

                if (!applyToAll) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Select apps to block during this window:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        allApps.forEach { app ->
                            val isSelected = selectedApps.contains(app.packageName)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedApps = if (isSelected) selectedApps - app.packageName else selectedApps + app.packageName
                                },
                                label = { Text(app.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = startHour.toIntOrNull()
                    val sm = startMin.toIntOrNull()
                    val eh = endHour.toIntOrNull()
                    val em = endMin.toIntOrNull()

                    if (name.isBlank()) {
                        error = "Enter a schedule name"
                    } else if (sh == null || sh !in 0..23 || sm == null || sm !in 0..59) {
                        error = "Invalid start time"
                    } else if (eh == null || eh !in 0..23 || em == null || em !in 0..59) {
                        error = "Invalid end time"
                    } else if (!applyToAll && selectedApps.isEmpty()) {
                        error = "Select at least one app or choose 'All Apps'"
                    } else {
                        val schedule = Schedule(
                            id = UUID.randomUUID().toString().take(8),
                            name = name,
                            startTime = TimeOfDay(sh, sm),
                            endTime = TimeOfDay(eh, em),
                            activeDays = DayOfWeek.values().toSet(),
                            enabled = true,
                            blockedPackages = if (applyToAll) emptySet() else selectedApps
                        )
                        onConfirm(schedule)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
