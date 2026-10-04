package com.example.core.habits

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen habit reminder: wakes the screen, rings, and lets the parent mark the habit done. */
class HabitAlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getStringExtra(HabitAlarmScheduler.EXTRA_HABIT_ID)
        val store = HabitStore(this)
        val habit = store.load().firstOrNull { it.id == id }
        if (habit == null) {
            finish()
            return
        }

        startRinging()

        val timeText = LocalTime.of(habit.hour, habit.minute)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B3954), Color(0xFF0E5F78), Color(0xFF117A6A))
                        )
                    )
                    .systemBarsPadding()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(timeText, color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    habit.title,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (habit.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        habit.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = {
                        store.setDone(habit.id, LocalDate.now(), true)
                        finishAlarm(habit.id)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0B3954)
                    )
                ) { Text("Mark done", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { finishAlarm(habit.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Dismiss", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } catch (_: Exception) {
        }
    }

    private fun finishAlarm(habitId: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(habitId.hashCode())
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        super.onDestroy()
    }
}
