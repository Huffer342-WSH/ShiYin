package com.Huffer342.shiyin.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardAlarmStoreTest {
    @Test
    fun alarmMapRoundTripsAndDropsInvalidEntries() {
        val encoded = encodeAlarmMap(mapOf("a" to 10_000L, "b" to 0L, "" to 20_000L, "c" to 30_000L))
        assertEquals(mapOf("a" to 10_000L, "c" to 30_000L), decodeAlarmMap(encoded))
        assertEquals(emptyMap<String, Long>(), decodeAlarmMap("not-json"))
    }

    @Test
    fun fireWindowMatchesExactStoredAlarmWithinGracePeriod() {
        assertTrue(isValidFireWindow(10_000L, 10_000L, 10_000L))
        assertTrue(isValidFireWindow(10_000L, 10_000L, 130_000L))
        assertFalse(isValidFireWindow(10_000L, 10_000L, 9_999L))
        assertFalse(isValidFireWindow(10_000L, 10_000L, 130_001L))
        assertFalse(isValidFireWindow(10_000L, 20_000L, 10_000L))
        assertFalse(isValidFireWindow(10_000L, null, 10_000L))
        assertFalse(isValidFireWindow(0L, 0L, 0L))
    }
}
