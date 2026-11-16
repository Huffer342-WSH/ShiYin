package com.Huffer342.shiyin

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

enum class RepeatRule(val label: String) {
    ONCE("仅一次"), WEEKLY("按星期"), WORKDAY("中国大陆工作日")
}

data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val uri: String,
    val hour: Int,
    val minute: Int,
    val rule: RepeatRule = RepeatRule.ONCE,
    val date: LocalDate = LocalDate.now(),
    val weekdays: Set<DayOfWeek> = emptySet(),
    val stopMinutes: Int = 30,
    val enabled: Boolean = true,
    val nextAt: Long = 0,
    val status: String = "",
    val fadeSeconds: Int = 0
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && isLocalAudioReference(uri))
        require(hour in 0..23 && minute in 0..59)
        require(stopMinutes in 1..1440)
        require(fadeSeconds in 0..300 && fadeSeconds < stopMinutes * 60) { "淡入须小于单次播放上限" }
        require(rule != RepeatRule.WEEKLY || weekdays.isNotEmpty())
    }
}

class MissingCalendarException(val year: Int) : IllegalStateException("缺少 ${year} 年工作日日历，请联网更新")

data class WorkCalendar(val year: Int, val daysOff: Map<LocalDate, Boolean>) {
    init { require(daysOff.keys.all { it.year == year }) }
    fun isWorkday(date: LocalDate): Boolean {
        require(date.year == year)
        return daysOff[date]?.not() ?: (date.dayOfWeek.value <= 5)
    }
}

fun nextOccurrence(
    schedule: Schedule,
    now: ZonedDateTime,
    calendars: Map<Int, WorkCalendar>
): ZonedDateTime? {
    if (!schedule.enabled) return null
    val time = LocalTime.of(schedule.hour, schedule.minute)
    if (schedule.rule == RepeatRule.ONCE) {
        return schedule.date.atTime(time).atZone(now.zone).takeIf { it.isAfter(now) }
    }
    for (offset in 0L..370L) {
        val date = now.toLocalDate().plusDays(offset)
        val candidate = date.atTime(time).atZone(now.zone)
        if (!candidate.isAfter(now)) continue
        val matches = when (schedule.rule) {
            RepeatRule.WEEKLY -> date.dayOfWeek in schedule.weekdays
            RepeatRule.WORKDAY -> (calendars[date.year] ?: throw MissingCalendarException(date.year)).isWorkday(date)
            RepeatRule.ONCE -> false
        }
        if (matches) return candidate
    }
    return null
}

fun shouldFire(schedule: Schedule, expectedAt: Long, now: Long): Boolean =
    schedule.enabled && expectedAt > 0 && schedule.nextAt == expectedAt &&
        now >= expectedAt && now - expectedAt <= 120_000L
