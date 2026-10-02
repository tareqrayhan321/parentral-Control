package com.example.core.policy

import com.example.core.model.AppPolicy
import com.example.core.model.BlockReason
import com.example.core.model.Policy
import com.example.core.model.RestrictionDecision
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class PolicyEngineTest {

    private lateinit var engine: PolicyEngine

    @Before
    fun setUp() {
        engine = DefaultPolicyEngine()
    }

    @Test
    fun `unmanaged app returns Allowed`() {
        val policy = Policy.empty()
        val decision = engine.evaluate(
            packageName = "com.unmanaged.app",
            currentTime = TimeOfDay(12, 0),
            currentDay = DayOfWeek.MONDAY,
            usageTodayMinutes = 100,
            policy = policy
        )
        assertEquals(RestrictionDecision.Allowed, decision)
    }

    @Test
    fun `disabled app policy returns Allowed even if mode is BLOCKED`() {
        val app = AppPolicy(
            packageName = "com.test.app",
            displayName = "Test App",
            mode = RestrictionMode.BLOCKED,
            enabled = false
        )
        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app)
        )
        val decision = engine.evaluate(
            packageName = "com.test.app",
            currentTime = TimeOfDay(14, 0),
            currentDay = DayOfWeek.TUESDAY,
            usageTodayMinutes = 0,
            policy = policy
        )
        assertEquals(RestrictionDecision.Allowed, decision)
    }

    @Test
    fun `manually blocked app returns Blocked with MANUALLY_BLOCKED`() {
        val app = AppPolicy(
            packageName = "com.facebook.katana",
            displayName = "Facebook",
            mode = RestrictionMode.BLOCKED,
            enabled = true
        )
        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app)
        )
        val decision = engine.evaluate(
            packageName = "com.facebook.katana",
            currentTime = TimeOfDay(10, 0),
            currentDay = DayOfWeek.WEDNESDAY,
            usageTodayMinutes = 5,
            policy = policy
        )
        assertTrue(decision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.MANUALLY_BLOCKED, (decision as RestrictionDecision.Blocked).reason)
    }

    @Test
    fun `limited app returns Allowed when usage is strictly below limit`() {
        val app = AppPolicy(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 30,
            enabled = true
        )
        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app)
        )
        val decision = engine.evaluate(
            packageName = "com.google.android.youtube",
            currentTime = TimeOfDay(15, 0),
            currentDay = DayOfWeek.FRIDAY,
            usageTodayMinutes = 29,
            policy = policy
        )
        assertEquals(RestrictionDecision.Allowed, decision)
    }

    @Test
    fun `limited app returns Blocked when usage reaches or exceeds daily limit`() {
        val app = AppPolicy(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 30,
            enabled = true
        )
        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app)
        )

        val atLimitDecision = engine.evaluate(
            packageName = "com.google.android.youtube",
            currentTime = TimeOfDay(15, 0),
            currentDay = DayOfWeek.FRIDAY,
            usageTodayMinutes = 30,
            policy = policy
        )
        assertTrue(atLimitDecision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, (atLimitDecision as RestrictionDecision.Blocked).reason)

        val overLimitDecision = engine.evaluate(
            packageName = "com.google.android.youtube",
            currentTime = TimeOfDay(15, 0),
            currentDay = DayOfWeek.FRIDAY,
            usageTodayMinutes = 35,
            policy = policy
        )
        assertTrue(overLimitDecision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.DAILY_LIMIT_REACHED, (overLimitDecision as RestrictionDecision.Blocked).reason)
    }

    @Test
    fun `schedule crossing midnight (22 00 to 07 00) blocks evening and morning correctly`() {
        val nightSchedule = Schedule(
            id = "bedtime",
            name = "Bedtime",
            startTime = TimeOfDay(22, 0),
            endTime = TimeOfDay(7, 0),
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            enabled = true
        )

        val app = AppPolicy(
            packageName = "com.zhiliaoapp.musically",
            displayName = "TikTok",
            mode = RestrictionMode.ALLOWED,
            scheduleIds = listOf("bedtime"),
            enabled = true
        )

        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app),
            schedules = mapOf(nightSchedule.id to nightSchedule)
        )

        // Monday 21:59 -> NOT blocked yet
        val monBeforeBed = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(21, 59), DayOfWeek.MONDAY, 0, policy)
        assertEquals(RestrictionDecision.Allowed, monBeforeBed)

        // Monday 22:00 -> BLOCKED (evening portion)
        val monBedtime = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(22, 0), DayOfWeek.MONDAY, 0, policy)
        assertTrue(monBedtime is RestrictionDecision.Blocked)
        assertEquals(BlockReason.SCHEDULE_ACTIVE, (monBedtime as RestrictionDecision.Blocked).reason)

        // Monday 23:30 -> BLOCKED (evening portion)
        val monLate = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(23, 30), DayOfWeek.MONDAY, 0, policy)
        assertTrue(monLate is RestrictionDecision.Blocked)

        // Tuesday 03:00 -> BLOCKED (morning portion of Monday's schedule)
        val tueEarly = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(3, 0), DayOfWeek.TUESDAY, 0, policy)
        assertTrue(tueEarly is RestrictionDecision.Blocked)

        // Tuesday 06:59 -> BLOCKED (morning portion of Monday's schedule)
        val tueWakeupSoon = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(6, 59), DayOfWeek.TUESDAY, 0, policy)
        assertTrue(tueWakeupSoon is RestrictionDecision.Blocked)

        // Tuesday 07:00 -> ALLOWED (bedtime ended)
        val tueMorning = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(7, 0), DayOfWeek.TUESDAY, 0, policy)
        assertEquals(RestrictionDecision.Allowed, tueMorning)

        // Sunday evening (Sunday is NOT in activeDays) 22:30 -> ALLOWED
        val sunEvening = engine.evaluate("com.zhiliaoapp.musically", TimeOfDay(22, 30), DayOfWeek.SUNDAY, 0, policy)
        assertEquals(RestrictionDecision.Allowed, sunEvening)
    }

    @Test
    fun `policy precedence verifies MANUALLY_BLOCKED precedes SCHEDULE_ACTIVE and DAILY_LIMIT`() {
        val schedule = Schedule(
            id = "sched1",
            name = "School",
            startTime = TimeOfDay(9, 0),
            endTime = TimeOfDay(15, 0),
            activeDays = setOf(DayOfWeek.WEDNESDAY),
            enabled = true
        )

        val app = AppPolicy(
            packageName = "com.supercell.clashroyale",
            displayName = "Clash Royale",
            mode = RestrictionMode.BLOCKED, // Manually blocked
            dailyLimitMinutes = 30, // Exceeded daily limit
            scheduleIds = listOf("sched1"), // Inside active schedule
            enabled = true
        )

        val policy = Policy(
            version = 5,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app),
            schedules = mapOf(schedule.id to schedule)
        )

        val decision = engine.evaluate(
            packageName = "com.supercell.clashroyale",
            currentTime = TimeOfDay(10, 0), // during schedule
            currentDay = DayOfWeek.WEDNESDAY,
            usageTodayMinutes = 45, // exceeds 30
            policy = policy
        )

        // Priority 1 wins: MANUALLY_BLOCKED
        assertTrue(decision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.MANUALLY_BLOCKED, (decision as RestrictionDecision.Blocked).reason)
    }

    @Test
    fun `policy precedence verifies SCHEDULE_ACTIVE precedes DAILY_LIMIT_REACHED`() {
        val schedule = Schedule(
            id = "sched1",
            name = "Study",
            startTime = TimeOfDay(18, 0),
            endTime = TimeOfDay(20, 0),
            activeDays = setOf(DayOfWeek.THURSDAY),
            enabled = true
        )

        val app = AppPolicy(
            packageName = "com.game.app",
            displayName = "Game",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 20,
            scheduleIds = listOf("sched1"),
            enabled = true
        )

        val policy = Policy(
            version = 2,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(app.packageName to app),
            schedules = mapOf(schedule.id to schedule)
        )

        val decision = engine.evaluate(
            packageName = "com.game.app",
            currentTime = TimeOfDay(19, 0),
            currentDay = DayOfWeek.THURSDAY,
            usageTodayMinutes = 30, // limit reached too
            policy = policy
        )

        // Schedule block takes precedence over daily limit so child knows it's study time
        assertTrue(decision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.SCHEDULE_ACTIVE, (decision as RestrictionDecision.Blocked).reason)
    }

    @Test
    fun `calculateRemainingMinutes behaves accurately`() {
        val limitedApp = AppPolicy(
            packageName = "com.youtube",
            displayName = "YouTube",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 60,
            enabled = true
        )

        assertEquals(60, engine.calculateRemainingMinutes(limitedApp, 0))
        assertEquals(15, engine.calculateRemainingMinutes(limitedApp, 45))
        assertEquals(0, engine.calculateRemainingMinutes(limitedApp, 60))
        assertEquals(0, engine.calculateRemainingMinutes(limitedApp, 90))

        val allowedApp = AppPolicy(
            packageName = "com.notes",
            displayName = "Notes",
            mode = RestrictionMode.ALLOWED
        )
        assertNull(engine.calculateRemainingMinutes(allowedApp, 100))
    }

    @Test
    fun `ScheduleCalculator finds next transition correctly`() {
        val zone = ZoneId.of("UTC")
        val fixedNow = ZonedDateTime.of(2026, 10, 2, 21, 0, 0, 0, zone) // Friday 21:00

        val schedule = Schedule(
            id = "night",
            name = "Night",
            startTime = TimeOfDay(22, 0),
            endTime = TimeOfDay(7, 0),
            activeDays = setOf(DayOfWeek.FRIDAY),
            enabled = true
        )

        val nextTransition = ScheduleCalculator.findNextTransition(listOf(schedule), fixedNow)
        // Next transition should be today at 22:00
        assertEquals(
            ZonedDateTime.of(2026, 10, 2, 22, 0, 0, 0, zone),
            nextTransition
        )
    }

    @Test
    fun `schedule with specific blockedPackages blocks only target apps and allows others`() {
        val specificSchedule = Schedule(
            id = "study_block",
            name = "Study Window",
            startTime = TimeOfDay(14, 0), // 2:00 PM
            endTime = TimeOfDay(17, 0),   // 5:00 PM
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            enabled = true,
            blockedPackages = setOf("com.google.android.youtube") // Only YouTube
        )

        val youtube = AppPolicy("com.google.android.youtube", "YouTube", RestrictionMode.ALLOWED)
        val duolingo = AppPolicy("com.duolingo", "Duolingo", RestrictionMode.ALLOWED)

        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(
                youtube.packageName to youtube,
                duolingo.packageName to duolingo
            ),
            schedules = mapOf(specificSchedule.id to specificSchedule)
        )

        // At 15:30 on Monday:
        // YouTube MUST be BLOCKED
        val ytDecision = engine.evaluate("com.google.android.youtube", TimeOfDay(15, 30), DayOfWeek.MONDAY, 0, policy)
        assertTrue(ytDecision is RestrictionDecision.Blocked)
        assertEquals(BlockReason.SCHEDULE_ACTIVE, (ytDecision as RestrictionDecision.Blocked).reason)

        // Duolingo MUST be ALLOWED
        val duoDecision = engine.evaluate("com.duolingo", TimeOfDay(15, 30), DayOfWeek.MONDAY, 0, policy)
        assertEquals(RestrictionDecision.Allowed, duoDecision)

        // At 17:30 (outside window), YouTube is ALLOWED again
        val ytOutside = engine.evaluate("com.google.android.youtube", TimeOfDay(17, 30), DayOfWeek.MONDAY, 0, policy)
        assertEquals(RestrictionDecision.Allowed, ytOutside)
    }
}
