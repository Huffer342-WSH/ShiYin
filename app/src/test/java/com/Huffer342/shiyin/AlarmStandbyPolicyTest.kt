package com.Huffer342.shiyin

import org.junit.Assert.*
import org.junit.Test

class AlarmStandbyPolicyTest {
    private fun alarm(at: Long, enabled: Boolean = true) = Schedule(
        title = "test", uri = "content://media/external/audio/media/1",
        hour = 7, minute = 0, enabled = enabled, nextAt = at
    )

    @Test fun bootRestoresFutureTasksWithoutRequiringManualBackgroundSwipe() {
        assertTrue(shouldRestoreStandby(listOf(alarm(200_000)), 100_000))
        assertFalse(shouldRestoreStandby(listOf(alarm(200_000, false), alarm(0), alarm(99_000)), 100_000))
        assertFalse(shouldRestoreStandby(emptyList(), 100_000))
    }

    @Test fun canceledAndUnregisteredAlarmsDoNotKeepServiceAlive() {
        assertFalse(needsAlarmStandby(listOf(alarm(200_000, false), alarm(0)), 100_000, false))
        assertFalse(needsAlarmStandby(emptyList(), 100_000, false))
    }

    @Test fun consumingLastAlarmKeepsWaitingServiceUntilPlaybackStarts() {
        assertTrue(needsAlarmStandby(emptyList(), 100_000, true))
        assertFalse(needsAlarmStandby(emptyList(), 100_000, false))
    }

    @Test fun picksEarliestEnabledAlarmAndRetainsDeliveryGrace() {
        assertEquals(300_000L, standbyNextAt(listOf(alarm(400_000), alarm(300_000), alarm(250_000, false)), 200_000))
        assertTrue(needsAlarmStandby(listOf(alarm(100_000)), 220_000, false))
        assertFalse(needsAlarmStandby(listOf(alarm(100_000)), 220_001, false))
    }
}
