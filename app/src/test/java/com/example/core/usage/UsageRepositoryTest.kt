package com.example.core.usage

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class UsageRepositoryTest {

    private lateinit var database: ChildDatabase
    private lateinit var policyRepository: PolicyRepository
    private lateinit var usageRepository: AndroidUsageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        policyRepository = RoomPolicyRepository(database)
        usageRepository = AndroidUsageRepository(context, policyRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `start of day calculation handles local timezone correctly`() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS)

        assertEquals(0, startOfDay.hour)
        assertEquals(0, startOfDay.minute)
        assertEquals(0, startOfDay.second)
        assertEquals(now.toLocalDate(), startOfDay.toLocalDate())
    }

    @Test
    fun `refreshTodayUsage returns cached Room data when permission not granted in test`() = runTest {
        val todayStr = LocalDate.now().toString()
        policyRepository.recordTodayUsage("com.test.app", todayStr, 40)

        val usage = usageRepository.refreshTodayUsage()
        assertEquals(40, usage["com.test.app"])
    }

    @Test
    fun `getTodayUsageMinutes returns 0 for untracked package`() = runTest {
        val minutes = usageRepository.getTodayUsageMinutes("com.unknown.app")
        assertEquals(0, minutes)
    }
}
