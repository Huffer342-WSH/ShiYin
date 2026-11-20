package com.Huffer342.shiyin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionGuardTest {
    private fun schedule(id: String, nextAt: Long, enabled: Boolean = true) = Schedule(
        id = id,
        title = "测试",
        uri = "content://media/external/audio/media/1",
        hour = 7,
        minute = 0,
        enabled = enabled,
        nextAt = nextAt
    )

    @Test
    fun syncJsonIncludesOnlyEnabledFutureAlarms() {
        val raw = encodeGuardAlarmSyncJson(
            listOf(
                schedule("future-1", 200_000L, enabled = true),
                schedule("future-2", 300_000L, enabled = true),
                schedule("disabled", 400_000L, enabled = false),
                schedule("past", 100_000L, enabled = true),
                schedule("unregistered", 0L, enabled = true)
            ),
            now = 100_000L
        )
        val json = JSONObject(raw)
        assertEquals(200_000L, json.getLong("future-1"))
        assertEquals(300_000L, json.getLong("future-2"))
        assertFalse(json.has("disabled"))
        assertFalse(json.has("past"))
        assertFalse(json.has("unregistered"))
    }
}
