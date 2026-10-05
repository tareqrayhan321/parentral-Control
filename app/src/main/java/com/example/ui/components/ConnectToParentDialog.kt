package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Child-side pairing dialog. The primary path is scanning the parent's QR with the Google
 * code scanner (system UI, no camera permission). Pasting the QR text is kept as a fallback.
 */
@Composable
fun ConnectToParentDialog(
    onDismiss: () -> Unit,
    onConnectWithQr: (qrContent: String) -> Unit
) {
    val context = LocalContext.current
    var qrJsonInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }

    // In-app scanner (CameraX + ZXing): it does not depend on a Google Play services module, which is
    // missing or outdated on many tablets and made the old scanner open and close at once.
    fun startScan() { showScanner = true }

    if (showScanner) {
        QrScannerDialog(
            onResult = { raw ->
                showScanner = false
                if (raw.isBlank()) errorMessage = "QR কোড পড়া যায়নি। আবার চেষ্টা করুন।"
                else onConnectWithQr(raw)
            },
            onDismiss = { showScanner = false }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScan()
        else errorMessage = "ক্যামেরা পারমিশন দেওয়া হয়নি। Settings → Apps → এই অ্যাপ → Permissions → Camera চালু করুন।"
    }

    fun scanWithPermission() {
        errorMessage = null
        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE)
            as? android.app.admin.DevicePolicyManager
        if (dpm?.getCameraDisabled(null) == true) {
            errorMessage = "এই ডিভাইসে ক্যামেরা অভিভাবকের নিয়মে বন্ধ করা আছে, তাই স্ক্যান করা যাচ্ছে না। QR টেক্সট পেস্ট করুন।"
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startScan() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Connect to Parent Phone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "অভিভাবকের ফোনের সাথে যুক্ত করুন",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "অভিভাবকের ফোনের পেয়ারিং ট্যাবে দেখানো QR কোডটি স্ক্যান করুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        scanWithPermission()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR (কিউআর স্ক্যান করুন)")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "অথবা QR-এর টেক্সট পেস্ট করুন:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = qrJsonInput,
                    onValueChange = {
                        qrJsonInput = it
                        errorMessage = null
                    },
                    label = { Text("QR Code Payload") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    if (qrJsonInput.isBlank()) {
                        errorMessage = "QR কোডের টেক্সট পেস্ট করুন বা স্ক্যান করুন।"
                    } else {
                        onConnectWithQr(qrJsonInput)
                    }
                },
                modifier = Modifier.testTag("submit_connect_button")
            ) {
                Text("Connect (কানেক্ট করুন)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
