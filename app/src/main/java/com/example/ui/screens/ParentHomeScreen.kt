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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.ChildDevice
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
    onOpenAccount: () -> Unit,
    onChangePin: () -> Unit,
    onSwitchRole: () -> Unit,
    onSignOut: () -> Unit
) {
    var showAddChild by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val displayName = parentName?.takeIf { it.isNotBlank() }
        ?: parentEmail?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "Parent"
    val totalMinutes = todayUsage.values.sum()
    val blockedCount = policy.apps.values.count { it.mode == RestrictionMode.BLOCKED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
    ) {
        // ---------- Header ----------
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(HeaderGradient)
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.first().uppercase(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

                Spacer(modifier = Modifier.height(20.dp))

                // Summary glass card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(16.dp)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = when {
                            deviceInfo == null -> "No child added yet"
                            isSupervised -> "Family Safe"
                            else -> "Protection paused"
                        },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(if (deviceInfo != null) "1" else "0", "Children", Modifier.weight(1f))
                        StatTile(formatMinutes(totalMinutes), "Today", Modifier.weight(1f))
                        StatTile("$blockedCount", "Blocked apps", Modifier.weight(1f))
                    }
                }
            }
        }

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
                        status = if (isMandatoryDnsEnforced) "On" else "Off",
                        active = isMandatoryDnsEnforced,
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
                if (deviceInfo == null) {
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E9E5))
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
                                        deviceInfo.deviceName.firstOrNull()?.uppercase() ?: "?",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4B2A9A)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        deviceInfo.deviceName,
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
                                        onClick = { confirmRemove = true },
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
                Spacer(modifier = Modifier.height(32.dp))
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

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove child phone?") },
            text = { Text("Rules will stop applying and this phone will be unlinked from your account.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; expanded = false; onUnpairChild() }) {
                    Text("Remove", color = StatusBlocked)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
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
            .background(Color.White.copy(alpha = 0.12f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
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
