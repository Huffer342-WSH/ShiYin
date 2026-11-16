package com.Huffer342.shiyin

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

object ScheduleCodec {
    fun encode(schedules: List<Schedule>): String = JSONArray().apply {
        schedules.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("uri", s.uri)
                put("hour", s.hour); put("minute", s.minute); put("rule", s.rule.name)
                put("date", s.date.toString())
                put("weekdays", JSONArray(s.weekdays.map { it.value }.sorted()))
                put("stopMinutes", s.stopMinutes); put("enabled", s.enabled)
                put("nextAt", s.nextAt); put("status", s.status); put("fadeSeconds", s.fadeSeconds)
            })
        }
    }.toString()

    fun decode(text: String): List<Schedule> {
        val array = JSONArray(text)
        val result = (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            val days = o.getJSONArray("weekdays")
            Schedule(
                id = o.getString("id"), title = o.getString("title"), uri = o.getString("uri"),
                hour = o.getInt("hour"), minute = o.getInt("minute"),
                rule = RepeatRule.valueOf(o.getString("rule")), date = LocalDate.parse(o.getString("date")),
                weekdays = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
                stopMinutes = o.getInt("stopMinutes"), enabled = o.getBoolean("enabled"),
                nextAt = o.optLong("nextAt", 0), status = o.optString("status", ""),
                fadeSeconds = o.optInt("fadeSeconds", 0)
            )
        }
        require(result.map { it.id }.distinct().size == result.size) { "任务编号重复" }
        return result
    }

    fun calendar(text: String, expectedYear: Int): WorkCalendar {
        val o = JSONObject(text)
        require(o.getInt("year") == expectedYear) { "日历年份不匹配" }
        require(o.getJSONArray("papers").length() > 0) { "日历缺少公告来源" }
        val days = o.getJSONArray("days")
        require(days.length() in 1..366) { "日历数据不完整" }
        val result = mutableMapOf<LocalDate, Boolean>()
        repeat(days.length()) { index ->
            val day = days.getJSONObject(index)
            val date = LocalDate.parse(day.getString("date"))
            require(date.year == expectedYear && date !in result) { "日历日期无效或重复" }
            val off = day.get("isOffDay")
            require(off is Boolean) { "日历休息日标记无效" }
            result[date] = off
        }
        return WorkCalendar(expectedYear, result)
    }
}
