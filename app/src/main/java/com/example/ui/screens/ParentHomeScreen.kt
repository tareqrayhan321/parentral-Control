package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.ChildDevice
import com.example.core.sync.RemoteHeartbeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.core.model.DnsProvider
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.ui.theme.StatusAllowed
import com.example.ui.theme.StatusBlocked
import java.time.LocalTime

private val HeaderGradient = Brush.linearGradient(
    listOf(Color(0xFF0B3954), Color(0xFF0E5F78), Color(0xFF117A6A))
)
private val HomeBg = Color(0xFFF6F8FA)
private val InkDark = Color(0xFF10231F)
private val InkSoft = Color(0xFF5B6F69)

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun formatMinutes(total: Int): String =
    if (total >= 60) "%dh %02dm".format(total / 60, total % 60) else "${total}m"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeTab(
    parentName: String?,
    parentEmail: String?,
    parentPhotoUrl: String? = null,
    deviceInfo: ChildDevice?,
    childStatusLabel: String,
    policy: Policy,
    todayUsage: Map<String, Int>,
    isDeviceOwner: Boolean,
    isSupervised: Boolean,
    isCameraBlocked: Boolean,
    isInstallBlocked: Boolean,
    isMandatoryDnsEnforced: Boolean,
    enforcedDnsHost: String,
    /** Real state reported by the child phone: true = Private DNS is on, false = off, null = no report yet. */
    childDnsActive: Boolean? = null,
    instantLockdown: Boolean,
    qrBitmap: Bitmap?,
    qrRemainingSeconds: Int,
    onToggleSupervision: (Boolean) -> Unit,
    onToggleCameraRestriction: (Boolean) -> Unit,
    onToggleInstallRestriction: (Boolean) -> Unit,
    onSetMandatoryDns: (Boolean, String) -> Unit,
    onToggleInstantLockdown: (Boolean) -> Unit,
    onPushSync: () -> Unit,
    onUnpairChild: () -> Unit,
    onRegenerateQr: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenAccount: () -> Unit,
    onChangePin: () -> Unit,
    onSwitchRole: () -> Unit,
    onSignOut: () -> Unit,
    // ---- multi-child ----
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
    observeChildMinutes: (String) -> Flow<Int?> = { emptyFlow() }
) {
    var showAddChild by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ChildDevice?>(null) }
    var profileTarget by remember { mutableStateOf<ChildDevice?>(null) }
    var renameTarget by remember { mutableStateOf<ChildDevice?>(null) }
    val childCount = if (children.isNotEmpty()) children.size else if (deviceInfo != null) 1 else 0

    LaunchedEffect(deviceInfo?.deviceId) { expanded = false }

    // Close the "Add Child" dialog as soon as a new child has linked.
    var lastChildCount by remember { mutableStateOf(childCount) }
    LaunchedEffect(childCount) {
        if (childCount > lastChildCount) showAddChild = false
        lastChildCount = childCount
    }

    val displayName = parentName?.takeIf { it.isNotBlank() }
        ?: parentEmail?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "Parent"
    val totalMinutes = todayUsage.values.sum()
    val blockedCount = policy.apps.values.count { it.mode == RestrictionMode.BLOCKED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
    ) {
        // ---------- Fixed header (does not scroll) ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(HeaderGradient)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 100.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.ui.components.ProfileAvatar(
                photoUrl = parentPhotoUrl,
                name = displayName,
                size = 52.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting(), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                Text(
                    displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("home_menu_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Account & pairing") },
                        onClick = { menuOpen = false; onOpenAccount() }
                    )
                    DropdownMenuItem(
                        text = { Text("Change PIN") },
                        onClick = { menuOpen = false; onChangePin() }
                    )
                    DropdownMenuItem(
                        text = { Text("Switch role") },
                        onClick = { menuOpen = false; onSwitchRole() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        onClick = { menuOpen = false; onSignOut() }
                    )
                }
            }
        }

        // ---------- Family Safe card: half over the header, half over the screen ----------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val overlap = placeable.height / 2
                    layout(placeable.width, placeable.height - overlap) {
                        placeable.place(0, -overlap)
                    }
                },
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E9E5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        deviceInfo == null -> "No child added yet"
                        isSupervised -> "Family Safe"
                        else -> "Protection paused"
                    },
                    color = InkDark,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$childCount", "Children", Modifier.weight(1f))
                    StatTile(formatMinutes(totalMinutes), "Today", Modifier.weight(1f))
                    StatTile("$blockedCount", "Blocked apps", Modifier.weight(1f))
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ---------- Quick actions ----------
        item {
            SectionTitle("Quick actions")
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(
                        icon = if (instantLockdown) Icons.Default.LockOpen else Icons.Default.Lock,
                        label = if (instantLockdown) "Unlock" else "Lock Now",
                        status = if (instantLockdown) "Locked" else "Off",
                        active = instantLockdown,
                        tone = StatusBlocked,
                        tag = "quick_lock",
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleInstantLockdown(!instantLockdown) }
                    )
                    QuickAction(
                        icon = Icons.Default.Public,
                        label = "Safe Internet",
                        status = when {
                            !isMandatoryDnsEnforced -> "Off"
                            childDnsActive == true -> "On"
                            else -> "Pending"
                        },
                        active = isMandatoryDnsEnforced && childDnsActive == true,
                        tone = StatusAllowed,
                        tag = "quick_dns",
                        modifier = Modifier.weight(1f),
                        onClick = { onSetMandatoryDns(!isMandatoryDnsEnforced, enforcedDnsHost) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(
                        icon = Icons.Default.Block,
                        label = "Block Installs",
                        status = if (isInstallBlocked) "On" else "Off",
                        active = isInstallBlocked,
                        tone = Color(0xFF2F7FB8),
                        tag = "quick_installs",
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleInstallRestriction(!isInstallBlocked) }
                    )
                    QuickAction(
                        icon = if (isCameraBlocked) Icons.Default.NoPhotography else Icons.Default.CameraAlt,
                        label = "Block Camera",
                        status = if (isCameraBlocked) "On" else "Off",
                        active = isCameraBlocked,
                        tone = Color(0xFF8A5CD0),
                        tag = "quick_camera",
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleCameraRestriction(!isCameraBlocked) }
                    )
                }
            }
        }

        // ---------- Family members ----------
        item { SectionTitle("Family members") }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (children.isEmpty() && deviceInfo == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E9E5))
                    ) {
                        Text(
                            text = "No child phone is linked yet. Tap “Add Child” to link one.",
                            color = InkSoft,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                } else {
                    val shown = if (children.isNotEmpty()) children else listOfNotNull(deviceInfo)
                    shown.forEachIndexed { index, child ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        if (child.deviceId == deviceInfo?.deviceId) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(
                                if (childCount > 1) 1.5.dp else 1.dp,
                                if (childCount > 1) Color(0xFF0B3954) else Color(0xFFE1E9E5)
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD9C7FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            child.deviceName.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4B2A9A)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            child.deviceName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InkDark
                                        )
                                        Text(childStatusLabel, fontSize = 13.sp, color = InkSoft)
                                        Text(
                                            "${formatMinutes(totalMinutes)} today",
                                            fontSize = 13.sp,
                                            color = InkSoft
                                        )
                                    }
                                    ChildOverflowMenu(
                                        onProfile = { profileTarget = child },
                                        onRename = { renameTarget = child },
                                        onRemove = { removeTarget = child }
                                    )
                                    IconButton(
                                        onClick = { expanded = !expanded },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF3F6))
                                            .testTag("child_expand_button")
                                    ) {
                                        Icon(
                                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Details"
                                        )
                                    }
                                }

                                // The child's apps (usage, limits, blocks) live under the child.
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFF1F5F8))
                                        .clickable(onClick = onOpenApps)
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("child_apps_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF0B3954))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Apps", fontWeight = FontWeight.Bold, color = InkDark)
                                        Text(
                                            "${policy.apps.size} apps • $blockedCount blocked",
                                            fontSize = 12.sp,
                                            color = InkSoft
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = InkSoft)
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Parental supervision", fontWeight = FontWeight.SemiBold, color = InkDark)
                                            Text(
                                                if (isSupervised) "Active" else "Paused",
                                                fontSize = 12.sp,
                                                color = if (isSupervised) StatusAllowed else InkSoft
                                            )
                                        }
                                        Switch(
                                            checked = isSupervised,
                                            onCheckedChange = onToggleSupervision,
                                            modifier = Modifier.testTag("supervision_switch")
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isDeviceOwner) "Full device control: ON" else "Full device control: not set up on child phone",
                                        fontSize = 12.sp,
                                        color = if (isDeviceOwner) StatusAllowed else InkSoft
                                    )

                                    if (isMandatoryDnsEnforced) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = when {
                                                childDnsActive == true -> "Safe Internet: ON and locked on child phone"
                                                !isDeviceOwner -> "Safe Internet: NOT on. Child phone has no Device Owner, so DNS cannot be forced."
                                                childDnsActive == false -> "Safe Internet: requested, NOT on yet. Retrying every minute."
                                                else -> "Safe Internet: requested, waiting for the child phone to report."
                                            },
                                            fontSize = 12.sp,
                                            color = if (childDnsActive == true) StatusAllowed else InkSoft
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Safe Internet provider", fontWeight = FontWeight.SemiBold, color = InkDark)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DnsProvider.ALL_PROVIDERS.forEach { provider ->
                                                FilterChip(
                                                    selected = enforcedDnsHost == provider.host,
                                                    onClick = { onSetMandatoryDns(true, provider.host) },
                                                    label = { Text(provider.name, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = onPushSync,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("push_sync_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Send rules", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { removeTarget = child },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = StatusBlocked,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Remove", color = StatusBlocked, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        } else {
                            ChildSummaryCard(
                                child = child,
                                statusLabel = childStatusLabels[child.deviceId]
                                    ?: "Linked • waiting for first report from child",
                                loading = isSwitchingChild,
                                onSelect = { onSelectChild(child.deviceId) },
                                onProfile = { profileTarget = child },
                                onRename = { renameTarget = child },
                                onRemove = { removeTarget = child }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Add Child
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFE4ECF3))
                        .clickable { showAddChild = true }
                        .padding(vertical = 18.dp)
                        .testTag("add_child_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0B3954))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Add Child", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B3954))
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
        }
    }

    if (showAddChild) {
        AddChildDialog(
            qrBitmap = qrBitmap,
            qrRemainingSeconds = qrRemainingSeconds,
            onRegenerateQr = onRegenerateQr,
            onDismiss = { showAddChild = false }
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${target.deviceName}?") },
            text = { Text("Rules will stop applying and this phone will be unlinked from your account. Your other children are not affected.") },
            confirmButton = {
                TextButton(onClick = { removeTarget = null; expanded = false; onRemoveChild(target.deviceId) }) {
                    Text("Remove", color = StatusBlocked)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.deviceId) { mutableStateOf(target.deviceName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename child") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onRenameChild(target.deviceId, name); renameTarget = null }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    profileTarget?.let { target ->
        ChildProfileDialog(
            child = children.firstOrNull { it.deviceId == target.deviceId } ?: target,
            heartbeat = childHeartbeats[target.deviceId],
            statusLabel = childStatusLabels[target.deviceId]
                ?: if (target.deviceId == deviceInfo?.deviceId) childStatusLabel else "Linked",
            isSelected = target.deviceId == deviceInfo?.deviceId,
            observeMinutes = observeChildMinutes,
            onDismiss = { profileTarget = null },
            onRename = { renameTarget = target; profileTarget = null },
            onRemove = { removeTarget = target; profileTarget = null },
            onManage = { onSelectChild(target.deviceId); profileTarget = null }
        )
    }

    if (pendingChildSwitch != null) {
        val fromName = deviceInfo?.deviceName ?: "this child"
        AlertDialog(
            onDismissRequest = onCancelChildSwitch,
            title = { Text("Unsent changes") },
            text = { Text("You changed the rules for $fromName but have not sent them yet. Send them before switching?") },
            confirmButton = {
                TextButton(onClick = { onConfirmChildSwitch(true) }) { Text("Send & switch") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onConfirmChildSwitch(false) }) { Text("Discard", color = StatusBlocked) }
                    TextButton(onClick = onCancelChildSwitch) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = InkDark,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFEEF3F6))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = InkDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = InkSoft, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    status: String,
    active: Boolean,
    tone: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) tone.copy(alpha = 0.10f) else Color.White),
        border = BorderStroke(1.dp, if (active) tone.copy(alpha = 0.5f) else Color(0xFFE1E9E5))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) tone else Color(0xFFE9EEF2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (active) Color.White else Color(0xFF3D4F5C))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = InkDark, maxLines = 1)
                Text(status, fontSize = 12.sp, color = if (active) tone else InkSoft)
            }
        }
    }
}

/** "Add Child": shows a fresh pairing QR with the steps to follow on the child's phone. */
@Composable
fun AddChildDialog(
    qrBitmap: Bitmap?,
    qrRemainingSeconds: Int,
    onRegenerateQr: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (qrBitmap == null || qrRemainingSeconds <= 0) onRegenerateQr()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Add Child", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkDark)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "1. Install this app on your child's phone\n" +
                        "2. Open it, sign in and choose “Kid”\n" +
                        "3. Tap “Scan QR” and scan the code below",
                    fontSize = 14.sp,
                    color = InkSoft,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF6F8FA)),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null && qrRemainingSeconds > 0) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Pairing QR",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        Text("QR expired", color = InkSoft)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (qrRemainingSeconds > 0)
                        "Expires in %d:%02d".format(qrRemainingSeconds / 60, qrRemainingSeconds % 60)
                    else "Create a new code",
                    fontSize = 13.sp,
                    color = InkSoft
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onRegenerateQr, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New QR code")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ChildOverflowMenu(onProfile: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("child_menu_button")) {
            Icon(Icons.Default.MoreVert, contentDescription = "Child options", tint = InkSoft)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Profile") }, onClick = { open = false; onProfile() })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            DropdownMenuItem(
                text = { Text("Remove", color = StatusBlocked) },
                onClick = { open = false; onRemove() }
            )
        }
    }
}

/** Compact card for a child that is not the one currently being managed. Tap to manage it. */
@Composable
private fun ChildSummaryCard(
    child: ChildDevice,
    statusLabel: String,
    loading: Boolean,
    onSelect: () -> Unit,
    onProfile: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onSelect)
            .testTag("child_card_${child.deviceId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE1E9E5))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD9C7FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    child.deviceName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4B2A9A)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(child.deviceName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkDark, maxLines = 1)
                Text(statusLabel, fontSize = 13.sp, color = InkSoft)
                Text(
                    if (loading) "Loading rules…" else "Tap to manage",
                    fontSize = 12.sp,
                    color = Color(0xFF0B3954)
                )
            }
            ChildOverflowMenu(onProfile = onProfile, onRename = onRename, onRemove = onRemove)
        }
    }
}

@Composable
private fun ChildProfileDialog(
    child: ChildDevice,
    heartbeat: RemoteHeartbeat?,
    statusLabel: String,
    isSelected: Boolean,
    observeMinutes: (String) -> Flow<Int?>,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onManage: () -> Unit
) {
    val minutesFlow = remember(child.deviceId) { observeMinutes(child.deviceId) }
    val minutes by minutesFlow.collectAsState(initial = null)
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD9C7FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            child.deviceName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B2A9A)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(child.deviceName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkDark)
                        Text(if (isSelected) "Currently managing" else "Linked child", fontSize = 12.sp, color = InkSoft)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ProfileRow("Status", statusLabel)
                ProfileRow("Device", "${child.platform} • ${child.deviceName}")
                ProfileRow("App version", heartbeat?.appVersion ?: child.appVersion)
                if (child.lastSeenEpochMs > 0L) {
                    ProfileRow(
                        "Linked on",
                        Instant.ofEpochMilli(child.lastSeenEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                    )
                }
                ProfileRow("Used today", minutes?.let { formatMinutes(it) } ?: "No report yet")
                ProfileRow("Rules applied", heartbeat?.let { "v${it.ackedPolicyVersion}" } ?: "—")
                ProfileRow("Full device control", if (heartbeat?.isDeviceOwner == true) "On" else "Not set up")
                ProfileRow("Accessibility guard", if (heartbeat?.accessibilityEnabled == true) "On" else "Off")
                Spacer(modifier = Modifier.height(16.dp))
                if (!isSelected) {
                    Button(
                        onClick = onManage,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Manage this child", fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRename,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Rename") }
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Remove", color = StatusBlocked) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 13.sp, color = InkSoft, modifier = Modifier.weight(0.42f))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkDark,
            modifier = Modifier.weight(0.58f)
        )
    }
}
