package com.example.core.policy

import com.example.core.model.Schedule
import com.example.core.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val zone = ZoneId.of("Asia/Dhaka")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private val bedtime = Schedule(
        id = "bed",
        name = "Bedtime",
        startTime = TimeOfDay(22, 0),
        endTime = TimeOfDay(7, 0),
        activeDays = DayOfWeek.values().toSet()
    )

    // 2026-10-05 is a Monday.
    @Test
    fun `overnight schedule - early morning finds same-day end, not tonight's start`() {
        val next = ScheduleCalculator.findNextTransition(listOf(bedtime), at(2026, 10, 5, 1, 0))
        assertEquals(at(2026, 10, 5, 7, 0), next)
    }

    @Test
    fun `overnight schedule - midday finds tonight's start`() {
        val next = ScheduleCalculator.findNextTransition(listOf(bedtime), at(2026, 10, 5, 12, 0))
        assertEquals(at(2026, 10, 5, 22, 0), next)
    }

    @Test
    fun `overnight schedule - only Sunday active, Monday 01 00 finds Monday 07 00`() {
        val sundayOnly = bedtime.copy(activeDays = setOf(DayOfWeek.SUNDAY))
        val next = ScheduleCalculator.findNextTransition(listOf(sundayOnly), at(2026, 10, 5, 1, 0))
        assertEquals(at(2026, 10, 5, 7, 0), next)
    }

    @Test
    fun `overnight schedule - only Monday active, Monday 01 00 skips to Monday 22 00`() {
        val mondayOnly = bedtime.copy(activeDays = setOf(DayOfWeek.MONDAY))
        val next = ScheduleCalculator.findNextTransition(listOf(mondayOnly), at(2026, 10, 5, 1, 0))
        assertEquals(at(2026, 10, 5, 22, 0), next)
    }

    @Test
    fun `same-day schedule unchanged`() {
        val study = Schedule("s", "Study", TimeOfDay(16, 0), TimeOfDay(18, 0), DayOfWeek.values().toSet())
        assertEquals(at(2026, 10, 5, 16, 0), ScheduleCalculator.findNextTransition(listOf(study), at(2026, 10, 5, 9, 0)))
        assertEquals(at(2026, 10, 5, 18, 0), ScheduleCalculator.findNextTransition(listOf(study), at(2026, 10, 5, 16, 30)))
    }

    @Test
    fun `no enabled schedules returns null`() {
        assertNull(ScheduleCalculator.findNextTransition(listOf(bedtime.copy(enabled = false)), at(2026, 10, 5, 1, 0)))
    }
}
