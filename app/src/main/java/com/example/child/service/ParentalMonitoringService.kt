package com.example.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.child.enforcement.DefaultPolicyEnforcementManager
import com.example.child.enforcement.PolicyEnforcementManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ParentalMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null
    private lateinit var enforcementManager: PolicyEnforcementManager

    override fun onCreate() {
        super.onCreate()
        enforcementManager = DefaultPolicyEnforcementManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startPeriodicEnforcement()
        Log.i(TAG, "ParentalMonitoringService started as foreground service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startPeriodicEnforcement() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    enforcementManager.enforceCurrentPolicy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic enforcement cycle", e)
                }
                delay(60_000L) // every 1 minute
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parental Supervision & Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays persistent status that device supervision is actively enforcing safety rules."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Parental Control Active")
            .setContentText("Device supervision and screen time rules are enforced.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "ParentalMonitoringService destroyed.")
    }

    companion object {
        private const val TAG = "ParentalMonitorService"
        private const val CHANNEL_ID = "parental_protection_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
