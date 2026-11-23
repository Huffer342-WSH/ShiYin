package com.Huffer342.shiyin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGuardStatusTest {
    @Test
    fun readyRequiresEveryGuardPermission() {
        assertFalse(
            CompanionGuardStatus(
                installed = true,
                signatureMatches = true,
                canScheduleExactAlarms = true,
                notificationsEnabled = false
            ).ready
        )
        assertTrue(
            CompanionGuardStatus(
                installed = true,
                signatureMatches = true,
                canScheduleExactAlarms = true,
                notificationsEnabled = true
            ).ready
        )
    }
}
