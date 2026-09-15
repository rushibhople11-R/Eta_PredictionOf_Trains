package org.example.project

import org.example.project.model.IndianStandardTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndianStandardTimeTest {

    @Test
    fun testKnownIstEpochConversion() {
        // 2026-09-12 12:33:46 UTC = 2026-09-12 18:03:46 IST
        // 1789216426000L is 2026-09-12 12:33:46 UTC
        val epochUtc = 1789216426000L
        val ist = IndianStandardTime.fromEpochMillis(epochUtc)

        assertEquals(2026, ist.year)
        assertEquals(9, ist.month)
        assertEquals("Sep", ist.monthName)
        assertEquals(12, ist.day)
        assertEquals(18, ist.hour)
        assertEquals(3, ist.minute)
        assertEquals(46, ist.second)
        assertEquals("18:03:46", ist.time24)
        assertEquals("06:03:46 PM", ist.time12)
        assertEquals("18:03:46 IST", ist.compactBadge)
        assertEquals("12 Sep 2026 • 06:03:46 PM IST", ist.fullFormatted)
    }

    @Test
    fun testCountdownCalculation() {
        val epochUtc = 1789216426000L // 18:03:46 IST
        val ist = IndianStandardTime.fromEpochMillis(epochUtc)

        // Target: 18:30 (26m 14s away)
        val countdown = ist.formatCountdownTo("18:30")
        assertEquals("26m 14s", countdown)

        // Target: 18:00 (3m 46s ago)
        val past = ist.formatCountdownTo("18:00")
        assertEquals("Departed 3m ago", past)
    }
}
