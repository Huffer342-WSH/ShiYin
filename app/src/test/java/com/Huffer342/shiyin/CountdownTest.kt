package com.Huffer342.shiyin
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
class CountdownTest {
    @Test fun explicitRestartPreservesDurationAndReplacesExpiredDeadline() {
        val old = Schedule(title = "test", uri = "content://media/external/audio/media/1",
            hour = 10, minute = 1, countdownAt = 1000, countdownSeconds = 20, enabled = false)
        val loaded = ScheduleCodec.decode(ScheduleCodec.encode(listOf(old))).single()
        val restarted = restartCountdown(loaded, 100_000)
        assertEquals(old.id, restarted.id)
        assertEquals(20L, restarted.countdownSeconds)
        assertEquals(120_000L, restarted.countdownAt)
        assertTrue(restarted.enabled)
        assertEquals(0L, restarted.nextAt)
    }
    @Test(expected = IllegalArgumentException::class)
    fun legacyDurationMustNotBeGuessed() {
        restartCountdown(Schedule(title = "test", uri = "content://media/external/audio/media/1",
            hour = 10, minute = 0, countdownAt = 1000), 2000)
    }

    @Test fun countdownKeepsSecondsAcrossStorageAndDoesNotRestartAfterExpiry() {
        val now = ZonedDateTime.parse("2026-11-17T10:00:45+08:00")
        val end = now.toInstant().toEpochMilli() + 20_000
        val s = Schedule(title = "test", uri = "content://media/external/audio/media/1",
            hour = 10, minute = 1, countdownAt = end)
        val loaded = ScheduleCodec.decode(ScheduleCodec.encode(listOf(s))).single()
        assertEquals(end, nextOccurrence(loaded, now, emptyMap())!!.toInstant().toEpochMilli())
        assertNull(nextOccurrence(loaded, now.plusSeconds(21), emptyMap()))
        assertNull(nextOccurrence(loaded.copy(enabled = false), now, emptyMap()))
    }
}
