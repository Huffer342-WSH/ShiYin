package com.Huffer342.shiyin.alarmprobe

import org.junit.Assert.*
import org.junit.Test

class WaitingWindowTest {
    @Test fun onlyCurrentFutureExperimentCanKeepServiceRunning() {
        assertTrue(validWaitingRun("a", "a", 280_000, 100_000))
        assertFalse(validWaitingRun("old", "a", 280_000, 100_000))
        assertFalse(validWaitingRun("a", "a", 100_000, 100_000))
        assertFalse(validWaitingRun("a", "a", 99_999, 100_000))
        assertFalse(validWaitingRun("a", "a", 340_001, 100_000))
        assertFalse(validWaitingRun(null, null, 280_000, 100_000))
    }
}
