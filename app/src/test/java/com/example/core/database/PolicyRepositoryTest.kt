package com.example.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class PolicyRepositoryTest {

    private lateinit var database: ChildDatabase
    private lateinit var repository: PolicyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomPolicyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `applyNewPolicyAtomic stores apps and schedules successfully`() = runTest {
        val app = AppPolicy(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 30
        )
        val schedule = Schedule(
            id = "bedtime",
            name = "Bedtime",
            startTime = TimeOfDay(22, 0),
            endTime = TimeOfDay(7, 0),
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        )

        val policy = Policy(
            version = 1,
            updatedAtEpochMs = 1727870000000L,
            apps = mapOf(app.packageName to app),
            schedules = mapOf(schedule.id to schedule)
        )

        val applied = repository.applyNewPolicyAtomic(policy, "test_device_1")
        assertTrue("New policy version 1 should be accepted", applied)

        val retrieved = repository.getCurrentPolicy()
        assertEquals(1, retrieved.version)
        assertEquals(1, retrieved.apps.size)
        assertEquals(RestrictionMode.LIMITED, retrieved.apps["com.google.android.youtube"]?.mode)
        assertEquals(30, retrieved.apps["com.google.android.youtube"]?.dailyLimitMinutes)
        assertEquals(1, retrieved.schedules.size)
        assertEquals("Bedtime", retrieved.schedules["bedtime"]?.name)
    }

    @Test
    fun `applyNewPolicyAtomic rejects stale policy version`() = runTest {
        val policyV5 = Policy(
            version = 5,
            updatedAtEpochMs = 1727870000000L,
            apps = emptyMap()
        )
        assertTrue(repository.applyNewPolicyAtomic(policyV5, "test_device_1"))

        // Attempt to apply older version 4
        val policyV4 = Policy(
            version = 4,
            updatedAtEpochMs = 1727871000000L,
            apps = emptyMap()
        )
        val rejectedOld = repository.applyNewPolicyAtomic(policyV4, "test_device_1")
        assertFalse("Stale version 4 must be rejected", rejectedOld)

        // Attempt to apply same version 5
        val rejectedSame = repository.applyNewPolicyAtomic(policyV5, "test_device_1")
        assertFalse("Duplicate version 5 must be rejected", rejectedSame)

        // Version 6 must succeed
        val policyV6 = Policy(
            version = 6,
            updatedAtEpochMs = 1727872000000L,
            apps = emptyMap()
        )
        assertTrue("Monotonically increasing version 6 must be accepted", repository.applyNewPolicyAtomic(policyV6, "test_device_1"))
    }

    @Test
    fun `recordTodayUsage saves and retrieves daily usage accurately`() = runTest {
        repository.recordTodayUsage("com.game.app", "2026-10-02", 45)
        repository.recordTodayUsage("com.youtube", "2026-10-02", 20)

        val usageMap = repository.getTodayUsageMap("2026-10-02")
        assertEquals(45, usageMap["com.game.app"])
        assertEquals(20, usageMap["com.youtube"])
    }
}
