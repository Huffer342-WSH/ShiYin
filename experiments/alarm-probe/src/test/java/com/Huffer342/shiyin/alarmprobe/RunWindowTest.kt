package com.Huffer342.shiyin.alarmprobe

import org.junit.Assert.*
import org.junit.Test

class RunWindowTest {
    @Test fun staleAndEarlyAlarmsCannotStartPlayback() {
        assertTrue(validRun("new", "new", 100_000, 100_000))
        assertTrue(validRun("new", "new", 100_000, 130_000))
        assertFalse(validRun("old", "new", 100_000, 100_001))
        assertFalse(validRun("new", "new", 100_000, 99_999))
        assertFalse(validRun("new", "new", 100_000, 130_001))
        assertFalse(validRun(null, null, 100_000, 100_000))
        assertFalse(validRun("", "", 100_000, 100_000))
        assertFalse(validRun("new", "new", 0, 0))
    }
}
