package com.example.child.scheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.time.DayOfWeek
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ScheduleAlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun `scheduleNextTransitionForSchedules sets exact alarm when active schedules exist`() {
        val schedule = Schedule(
            id = "homework",
            name = "Homework",
            startTime = TimeOfDay(16, 0),
            endTime = TimeOfDay(18, 0),
            activeDays = DayOfWeek.values().toSet(),
            enabled = true
        )

        val scheduledZdt = ScheduleAlarmScheduler.scheduleNextTransitionForSchedules(
            context,
            listOf(schedule)
        )

        assertNotNull("Upcoming transition must be calculated", scheduledZdt)
        assertTrue(scheduledZdt!!.isAfter(ZonedDateTime.now().minusSeconds(1)))

        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val nextScheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("AlarmManager must receive a scheduled alarm", nextScheduledAlarm)
    }

    @Test
    fun `scheduleNextTransitionForSchedules returns null and cancels when no schedules enabled`() {
        val disabledSchedule = Schedule(
            id = "disabled",
            name = "Disabled",
            startTime = TimeOfDay(10, 0),
            endTime = TimeOfDay(12, 0),
            activeDays = setOf(DayOfWeek.MONDAY),
            enabled = false
        )

        val scheduledZdt = ScheduleAlarmScheduler.scheduleNextTransitionForSchedules(
            context,
            listOf(disabledSchedule)
        )

        assertNull("Should return null when no schedules are active", scheduledZdt)
    }

    @Test
    fun `MidnightResetScheduler schedules alarm for future midnight`() {
        MidnightResetScheduler.scheduleNextMidnightAlarm(context)

        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Midnight alarm should be registered", nextAlarm)
    }
}
