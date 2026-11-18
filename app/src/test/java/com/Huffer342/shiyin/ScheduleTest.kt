package com.Huffer342.shiyin

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String) = ZonedDateTime.parse(value + "+08:00[Asia/Shanghai]")
    private fun schedule(rule: RepeatRule = RepeatRule.WEEKLY) = Schedule(
        title = "测试曲目", uri = "content://media/external/audio/media/1", hour = 7, minute = 30,
        rule = rule, date = LocalDate.of(2026, 1, 5), weekdays = setOf(DayOfWeek.MONDAY)
    )

    @Test fun weeklyRollsToNextWeekAtExactBoundary() {
        assertEquals(time("2026-01-12T07:30:00"),
            nextOccurrence(schedule(), time("2026-01-05T07:30:00"), emptyMap()))
    }

    @Test fun weeklyCanRunLaterToday() {
        assertEquals(time("2026-01-05T07:30:00"),
            nextOccurrence(schedule(), time("2026-01-05T07:29:00"), emptyMap()))
    }

    @Test fun expiredOneShotDoesNotCatchUp() {
        assertNull(nextOccurrence(schedule(RepeatRule.ONCE), time("2026-01-06T00:00:00"), emptyMap()))
    }

    @Test fun disabledTaskHasNoOccurrence() {
        assertNull(nextOccurrence(schedule().copy(enabled = false), time("2026-01-01T00:00:00"), emptyMap()))
    }

    @Test fun makeUpSundayOverridesWeekendAndHolidayOverridesWeekday() {
        val calendar = WorkCalendar(2026, mapOf(
            LocalDate.of(2026, 1, 2) to true,
            LocalDate.of(2026, 1, 4) to false
        ))
        assertFalse(calendar.isWorkday(LocalDate.of(2026, 1, 2)))
        assertTrue(calendar.isWorkday(LocalDate.of(2026, 1, 4)))
        assertEquals(time("2026-01-04T07:30:00"),
            nextOccurrence(schedule(RepeatRule.WORKDAY), time("2026-01-02T00:00:00"), mapOf(2026 to calendar)))
    }

    @Test(expected = MissingCalendarException::class)
    fun workdayNeverGuessesAcrossMissingYear() {
        nextOccurrence(schedule(RepeatRule.WORKDAY), time("2026-12-31T23:59:00"),
            mapOf(2026 to WorkCalendar(2026, emptyMap())))
    }

    @Test fun staleDuplicateDisabledAndLateTriggersAreRejected() {
        val task = schedule().copy(nextAt = 10_000)
        assertTrue(shouldFire(task, 10_000, 10_010))
        assertFalse(shouldFire(task, 9_000, 10_010))
        assertFalse(shouldFire(task, 10_000, 9_999))
        assertFalse(shouldFire(task, 10_000, 130_001))
        assertFalse(shouldFire(task.copy(enabled = false), 10_000, 10_010))
        assertFalse(shouldFire(task.copy(nextAt = 20_000), 10_000, 10_010))
    }

    @Test fun timeUsesChosenZoneAndHandlesDstGap() {
        val task = schedule().copy(hour = 2, minute = 30, weekdays = setOf(DayOfWeek.SUNDAY))
        val now = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ZoneId.of("America/New_York"))
        val next = nextOccurrence(task, now, emptyMap())!!
        assertEquals(3, next.hour)
        assertEquals(now.zone, next.zone)
        assertNotEquals(zone, next.zone)
    }

    @Test fun schedulesRoundTripWithoutLosingState() {
        val values = listOf(schedule().copy(nextAt = 123456L, status = "待授权"),
            schedule(RepeatRule.ONCE).copy(enabled = false))
        assertEquals(values, ScheduleCodec.decode(ScheduleCodec.encode(values)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWeeklyTaskIsRejected() {
        schedule().copy(weekdays = emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationIsRejected() {
        schedule().copy(stopMinutes = 0)
    }

    private fun calendarJson(year: Int = 2026, days: String = """{"date":"2026-01-04","isOffDay":false}""") =
        """{"year":$year,"papers":["https://www.gov.cn/example"],"days":[$days]}"""

    @Test fun calendarParserKeepsMakeUpWorkdays() {
        assertTrue(ScheduleCodec.calendar(calendarJson(), 2026).isWorkday(LocalDate.of(2026, 1, 4)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun calendarRejectsWrongYear() {
        ScheduleCodec.calendar(calendarJson(), 2027)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calendarRejectsStringBoolean() {
        ScheduleCodec.calendar(calendarJson(days = """{"date":"2026-01-04","isOffDay":"false"}"""), 2026)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calendarRejectsDuplicateDates() {
        val day = """{"date":"2026-01-04","isOffDay":false}"""
        ScheduleCodec.calendar(calendarJson(days = "$day,$day"), 2026)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calendarRejectsEmptyYearRatherThanGuessing() {
        ScheduleCodec.calendar(calendarJson(days = ""), 2026)
    }
}
