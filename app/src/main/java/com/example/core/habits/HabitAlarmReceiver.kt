package com.example.core.habits

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.time.LocalDate

/** Fires at the habit's reminder time: re-arms the next alarm and raises a full-screen alert. */
class HabitAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(HabitAlarmScheduler.EXTRA_HABIT_ID) ?: return
        val habit = HabitStore(context).load().firstOrNull { it.id == id } ?: return

        // Keep repeating: arm the following occurrence first.
        HabitAlarmScheduler.schedule(context, habit)

        if (habit.isDone(LocalDate.now())) return
        showAlert(context, habit)
    }

    private fun showAlert(context: Context, habit: Habit) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val alertIntent = Intent(context, HabitAlarmActivity::class.java)
            .putExtra(HabitAlarmScheduler.EXTRA_HABIT_ID, habit.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            habit.id.hashCode(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(habit.title)
            .setContentText(habit.description.ifBlank { "Time for your habit" })
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        nm.notify(habit.id.hashCode(), notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm-style reminders for your habits"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "habit_reminders"
    }
}
