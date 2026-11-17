package com.Huffer342.shiyin
import org.junit.Assert.*
import org.junit.Test
class AlarmTokenTest {
    @Test fun oldAndMissingActionsCannotStopNewAlarm() {
        val current = RingingAlarm("new", "test", 1000)
        assertTrue(isCurrentRingingToken("new", current))
        assertFalse(isCurrentRingingToken("old", current))
        assertFalse(isCurrentRingingToken(null, current))
        assertFalse(isCurrentRingingToken(null, null))
        assertFalse(isCurrentRingingToken("new", null))
    }
}
