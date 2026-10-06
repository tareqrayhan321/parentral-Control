package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.child.protection.DeviceProtectionManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.TimeOfDay
import com.example.ui.theme.StatusAllowed
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusLimited
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(
    policy: Policy,
    todayUsage: Map<String, Int>,
    isDeviceOwner: Boolean,
    isSupervised: Boolean,
    isCameraBlocked: Boolean,
    isMandatoryDnsEnforced: Boolean = true,
    enforcedDnsHost: String = "medium.kahfguard.com",
    deviceName: String?,
    isChildConnectedToParent: Boolean = false,
    connectedParentName: String = "Parent's Phone",
    onOpenConnectDialog: () -> Unit = {},
    onUnpairDevice: () -> Unit = {},
    onSwitchRoleRequested: () -> Unit = {},
    onOpenProtectionSetup: () -> Unit = {},
    onOpenParentLogin: () -> Unit,
    onRefreshUsage: () -> Unit,
    syncStatus: String = ""
) {
    val totalMinutesUsed = todayUsage.values.sum()
    val totalHours = totalMinutesUsed / 60
    val remainingMins = totalMinutesUsed % 60
    val currentTime = TimeOfDay.now()
    val currentDay = LocalDate.now().dayOfWeek

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSupervised) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = if (isSupervised) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = deviceName ?: "Child Device",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isChildConnectedToParent) "Linked with Parent • Protected" else "Not Linked to Parent Phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isChildConnectedToParent) StatusAllowed else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenProtectionSetup,
                        modifier = Modifier.testTag("protection_setup_button")
                    ) {
                        Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = "Protection Permissions")
                    }
                    IconButton(
                        onClick = onRefreshUsage,
                        modifier = Modifier.testTag("refresh_usage_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Usage")
                    }
                    FilledTonalButton(
                        onClick = onOpenParentLogin,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("parent_login_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Parent Login",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Parent", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Parent Phone Connection Card
            item {
                Spacer(modifier = Modifier.height(2.dp))
                if (!isChildConnectedToParent) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "অভিভাবকের ফোনের সাথে যুক্ত নয়",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Not Connected to Parent Phone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "অভিভাবকের ফোন থেকে নিয়ন্ত্রণ, অ্যাপ ব্লক ও স্ক্রিন টাইম মনিটর করার জন্য নিচের বাটনে চাপ দিয়ে অভিভাবকের ফোনের সাথে কানেক্ট করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenConnectDialog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_to_parent_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cable,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect to Parent (অভিভাবকের সাথে কানেক্ট করুন)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusAllowed.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                    imageVector = Icons.Default.Cable,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Linked to $connectedParentName",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusAllowed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = StatusAllowed.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "LINKED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusAllowed,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "অভিভাবকের সাথে যুক্ত",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (syncStatus.isNotBlank()) {
                                    Text(
                                        text = syncStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Device Control & Permissions Status Card
            item {
                val context = LocalContext.current
                val isAllCrucial = DeviceProtectionManager.isAllCrucialGranted(context)
                val permissions = DeviceProtectionManager.getAllPermissionsStatus(context)
                val grantedCount = permissions.count { it.isGranted }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAllCrucial) StatusAllowed.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAllCrucial) StatusAllowed
                                            else MaterialTheme.colorScheme.tertiary
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isAllCrucial) "ডিভাইস সুরক্ষা সম্পূর্ণ সচল" else "ডিভাইস নিয়ন্ত্রণ পারমিশন বাকি",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "পারমিশন অবস্থা: $grantedCount/${permissions.size} টি সক্রিয়",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAllCrucial) StatusAllowed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenProtectionSetup,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("open_protection_setup_btn")
                            ) {
                                Text(if (isAllCrucial) "সেটিংস" else "সেটআপ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Supervision & Safety Banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSupervised) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSupervised) MaterialTheme.colorScheme.primary else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Safety Status",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSupervised) "Parental Supervision Active" else "Supervision Paused",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSupervised) {
                                    "App limits, bedtime schedules, and tamper protections are active."
                                } else {
                                    "Device restrictions temporarily lifted."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCameraBlocked) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NoPhotography,
                                        contentDescription = null,
                                        tint = StatusBlocked,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Camera access currently restricted by parent.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusBlocked,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (isMandatoryDnsEnforced) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = StatusAllowed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Safe Web Filter Active ($enforcedDnsHost)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusAllowed,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Safe Web & Mandatory DNS Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMandatoryDnsEnforced) StatusAllowed.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isMandatoryDnsEnforced) StatusAllowed else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Safe Internet",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMandatoryDnsEnforced) "Mandatory DNS Internet Protection Active" else "Safe Web Filtering Paused",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMandatoryDnsEnforced) StatusAllowed else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isMandatoryDnsEnforced) {
                                    "Adult websites and malicious domains are blocked automatically across all browsers and apps."
                                } else if (!isDeviceOwner) {
                                    "DNS is NOT on: this phone is not set up as Device Owner, so DNS cannot be forced or locked."
                                } else {
                                    "DNS is NOT on yet (waiting for the parent's setting or a working internet connection)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Total Screen Time Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Today's Screen Time",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (totalHours > 0) "${totalHours}h ${remainingMins}m" else "${remainingMins}m",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "used today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            }

            // Active Schedules Notice
            val activeSchedules = policy.schedules.values.filter { it.enabled && it.isActiveAt(currentTime, currentDay) }
            if (activeSchedules.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusBlocked.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(StatusBlocked),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Schedule",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Scheduled Block Active Now",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusBlocked
                                )
                                Text(
                                    text = activeSchedules.joinToString("; ") { schedule ->
                                        val target = if (schedule.blockedPackages.isNotEmpty()) "specific apps" else "all apps"
                                        "${schedule.name} (${schedule.startTime} - ${schedule.endTime}) for $target"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Monitored Apps Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps & Limits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${policy.apps.size} monitored",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Apps List
            items(policy.apps.values.toList(), key = { it.packageName }) { app ->
                val used = todayUsage[app.packageName] ?: 0

                // Check if this app has an active scheduled time window right now
                val activeAppSchedule = policy.schedules.values.firstOrNull { schedule ->
                    schedule.enabled &&
                            (schedule.blockedPackages.contains(app.packageName) || schedule.id in app.scheduleIds || (schedule.blockedPackages.isEmpty() && app.scheduleIds.isEmpty())) &&
                            schedule.isActiveAt(currentTime, currentDay)
                }

                // Check if this app has a dedicated block schedule configured (even if not currently active)
                val configuredSchedule = policy.schedules.values.firstOrNull { schedule ->
                    schedule.enabled && schedule.blockedPackages.contains(app.packageName)
                }

                ChildAppUsageCard(
                    app = app,
                    usedMinutes = used,
                    activeScheduleName = activeAppSchedule?.name,
                    activeScheduleWindow = if (activeAppSchedule != null) "${activeAppSchedule.startTime} - ${activeAppSchedule.endTime}" else null,
                    scheduledBlockWindow = if (configuredSchedule != null && activeAppSchedule == null) "${configuredSchedule.startTime} - ${configuredSchedule.endTime}" else null
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ChildAppUsageCard(
    app: AppPolicy,
    usedMinutes: Int,
    activeScheduleName: String? = null,
    activeScheduleWindow: String? = null,
    scheduledBlockWindow: String? = null
) {
    val limit = app.dailyLimitMinutes
    val isOverLimit = limit != null && usedMinutes >= limit
    val progress = if (limit != null && limit > 0) {
        (usedMinutes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val statusColor: Color
    val statusText: String

    if (app.mode == RestrictionMode.BLOCKED) {
        statusColor = StatusBlocked
        statusText = "Blocked by Parent"
    } else if (activeScheduleName != null && activeScheduleWindow != null) {
        statusColor = StatusBlocked
        statusText = "Blocked by Schedule ($activeScheduleWindow)"
    } else {
        when (app.mode) {
            RestrictionMode.BLOCKED -> {
                statusColor = StatusBlocked
                statusText = "Blocked by Parent"
            }
            RestrictionMode.LIMITED -> {
                if (isOverLimit) {
                    statusColor = StatusBlocked
                    statusText = "Daily limit reached ($usedMinutes/$limit m)"
                } else {
                    statusColor = StatusLimited
                    statusText = "${limit!! - usedMinutes}m left today"
                }
            }
            RestrictionMode.ALLOWED -> {
                statusColor = StatusAllowed
                statusText = if (scheduledBlockWindow != null) "Allowed now (Blocks at $scheduledBlockWindow)" else "Always Allowed (${usedMinutes}m used)"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.displayName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (app.mode == RestrictionMode.BLOCKED) "BLOCKED"
                        else if (activeScheduleWindow != null) "IN BLOCK WINDOW"
                        else when (app.mode) {
                            RestrictionMode.BLOCKED -> "BLOCKED"
                            RestrictionMode.LIMITED -> if (isOverLimit) "LIMIT REACHED" else "${usedMinutes}m / ${limit}m"
                            RestrictionMode.ALLOWED -> if (scheduledBlockWindow != null) "BLOCKED $scheduledBlockWindow" else "UNLIMITED"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (app.mode == RestrictionMode.LIMITED && limit != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isOverLimit || activeScheduleName != null) StatusBlocked else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
