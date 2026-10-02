package com.example.child.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.security.AndroidPinSecurityManager
import com.example.core.security.PinVerificationResult
import com.example.ui.components.PinDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PinDialogState
import kotlinx.coroutines.launch

class AppBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Blocked App"
        val blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "RESTRICTED"
        val blockMessage = intent.getStringExtra(EXTRA_BLOCK_MESSAGE) ?: "This application is restricted by your parent."

        val pinSecurityManager = AndroidPinSecurityManager(applicationContext)

        setContent {
            MyApplicationTheme {
                var pinState by remember { mutableStateOf<PinDialogState>(PinDialogState.Hidden) }
                val coroutineScope = rememberCoroutineScope()

                // Pressing back takes child safely to Home Screen
                BackHandler {
                    returnToHomeScreen()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppBlockContent(
                            packageName = packageName,
                            blockReason = blockReason,
                            blockMessage = blockMessage,
                            onReturnHome = { returnToHomeScreen() },
                            onUnlockWithPin = {
                                pinState = PinDialogState.EnterPin("Parent PIN to bypass restriction")
                            }
                        )

                        PinDialog(
                            state = pinState,
                            onDismiss = { pinState = PinDialogState.Hidden },
                            onSubmitPin = { pin ->
                                coroutineScope.launch {
                                    val result = pinSecurityManager.verifyPin(pin)
                                    when (result) {
                                        is PinVerificationResult.Success -> {
                                            pinState = PinDialogState.Hidden
                                            // Allow opening the app and finish block activity
                                            finish()
                                        }
                                        is PinVerificationResult.Incorrect -> {
                                            pinState = PinDialogState.EnterPin(
                                                "Incorrect PIN. Attempts left: ${result.attemptsRemainingBeforeLockout}",
                                                result.attemptsRemainingBeforeLockout
                                            )
                                        }
                                        is PinVerificationResult.LockedOut -> {
                                            pinState = PinDialogState.LockedOut(result.secondsRemaining)
                                        }
                                        is PinVerificationResult.NotConfigured -> {
                                            pinState = PinDialogState.Hidden
                                            finish()
                                        }
                                    }
                                }
                            },
                            onSetupPin = {},
                            onChangePin = { _, _ -> }
                        )
                    }
                }
            }
        }
    }

    private fun returnToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
        const val EXTRA_BLOCK_MESSAGE = "extra_block_message"
    }
}

@Composable
private fun AppBlockContent(
    packageName: String,
    blockReason: String,
    blockMessage: String,
    onReturnHome: () -> Unit,
    onUnlockWithPin: () -> Unit
) {
    val appDisplayName = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }

    val reasonTitleBn = when (blockReason) {
        "DEVICE_LOCKDOWN" -> "ডিভাইস সম্পূর্ণ লকডাউন"
        "BEDTIME_SCHEDULE" -> "বেডটাইম শিডিউল কার্যকর"
        "LIMIT_EXCEEDED" -> "দৈনিক ব্যবহারের সময়সীমা শেষ"
        else -> "অভিভাবক কর্তৃক ব্লকড"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Blocked",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "অ্যাপটি সাময়িকভাবে বন্ধ আছে",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    text = reasonTitleBn,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = appDisplayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = blockMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onReturnHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("return_home_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("হোম স্ক্রিনে ফিরে যান (Return Home)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onUnlockWithPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("parent_unlock_pin_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("প্যারেন্ট পিন দিয়ে আনলক করুন", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Protected by Parental Control",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
