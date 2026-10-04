package com.example.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.child.protection.DeviceProtectionManager
import com.example.child.protection.PermissionStatus
import com.example.ui.theme.StatusAllowed
import com.example.ui.theme.StatusBlocked

@Composable
fun DeviceProtectionSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permissionsList by remember { mutableStateOf(DeviceProtectionManager.getAllPermissionsStatus(context)) }

    fun refreshPermissions() {
        permissionsList = DeviceProtectionManager.getAllPermissionsStatus(context)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    // The user grants permissions on a different screen (system Settings). Re-read them every time this
    // app comes back to the foreground, otherwise the list stays stale and a granted permission looks broken.
    DisposableEffect(context) {
        var owner: android.content.Context? = context
        while (owner is android.content.ContextWrapper && owner !is androidx.lifecycle.LifecycleOwner) {
            owner = owner.baseContext
        }
        val lifecycle = (owner as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val grantedCount = permissionsList.count { it.isGranted }
    val totalCount = permissionsList.size
    val isAllCrucial = permissionsList.filter { it.isCrucial }.all { it.isGranted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAllCrucial) StatusAllowed.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = if (isAllCrucial) StatusAllowed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ডিভাইস নিয়ন্ত্রণ পারমিশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Full Device Protection Setup ($grantedCount/$totalCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAllCrucial) StatusAllowed else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                Text(
                    text = "সন্তানের ফোন সম্পূর্ণ নিয়ন্ত্রণ, সময়সীমা কার্যকর এবং অ্যাপ আনইনস্টল বন্ধ করতে নিচের পারমিশনগুলো প্রদান করুন:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(permissionsList, key = { it.id }) { permission ->
                        PermissionItemCard(
                            permission = permission,
                            onGrantClick = {
                                launchPermissionSetting(context, permission.id)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { refreshPermissions() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("স্ট্যাটাস রিফ্রেশ", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            DeviceProtectionManager.startProtectionService(context)
                            refreshPermissions()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সুরক্ষা সার্ভিস চালু", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_permissions_dialog")
            ) {
                Text("সম্পন্ন (Done)")
            }
        }
    )
}

@Composable
private fun PermissionItemCard(
    permission: PermissionStatus,
    onGrantClick: () -> Unit
) {
    val icon = when (permission.id) {
        "admin" -> Icons.Default.Security
        "usage" -> Icons.Default.QueryStats
        "overlay" -> Icons.Default.Layers
        "accessibility" -> Icons.Default.Accessibility
        "battery" -> Icons.Default.BatteryAlert
        else -> Icons.Default.Notifications
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.isGranted) StatusAllowed.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (permission.isGranted) StatusAllowed.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (permission.isGranted) StatusAllowed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = permission.titleBn,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = permission.descriptionBn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (permission.isGranted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusAllowed.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusAllowed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "সচল", color = StatusAllowed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("grant_permission_${permission.id}")
                ) {
                    Text(text = "পারমিশন দিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun launchPermissionSetting(context: Context, permissionId: String) {
    val primary = when (permissionId) {
        "admin" -> DeviceProtectionManager.createDeviceAdminIntent(context)
        "usage" -> DeviceProtectionManager.createUsageAccessIntent(context)
        "overlay" -> DeviceProtectionManager.createOverlayIntent(context)
        "accessibility" -> DeviceProtectionManager.createAccessibilityIntent()
        "battery" -> DeviceProtectionManager.createBatteryOptimizationIntent(context)
        "notification" -> DeviceProtectionManager.createNotificationIntent(context)
        else -> null
    }
    // Many phone makers (ColorOS, MIUI, ...) reject the precise intent, so fall back step by step.
    val fallbacks = buildList {
        when (permissionId) {
            "usage" -> add(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "overlay" -> add(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            "battery" -> add(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        add(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            )
        )
    }

    for (intent in listOfNotNull(primary) + fallbacks) {
        try {
            // ACTION_ADD_DEVICE_ADMIN must NOT use NEW_TASK: Settings' DeviceAdminAdd screen finishes
            // immediately ("Cannot start ADD_DEVICE_ADMIN as a new task"), which looked like a white flash.
            if (context !is android.app.Activity) {
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            if (permissionId == "accessibility") {
                android.widget.Toast.makeText(
                    context,
                    "সেটিং ধূসর বা বন্ধ থাকলে: Settings → Apps → এই অ্যাপ → ⋮ মেনু → \"Allow restricted settings\" চাপুন, তারপর আবার চেষ্টা করুন।",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return
        } catch (e: Exception) {
            android.util.Log.w("DeviceProtectionSetup", "Could not open $permissionId settings with ${intent.action}", e)
        }
    }
    android.widget.Toast.makeText(
        context,
        "সেটিংস খোলা যায়নি। ফোনের Settings → Apps → এই অ্যাপ খুলে হাতে পারমিশন দিন।",
        android.widget.Toast.LENGTH_LONG
    ).show()
}
