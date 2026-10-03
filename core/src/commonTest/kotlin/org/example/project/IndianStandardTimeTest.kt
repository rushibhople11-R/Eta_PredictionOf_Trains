package org.example.project

import org.example.project.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
        assertEquals("6:03:46 PM", ist.time12)
        assertEquals("6:03 PM", ist.time12Standard)
        assertEquals("6:03 PM IST", ist.compactBadge)
        assertEquals("12 Sep 2026 • 6:03:46 PM IST", ist.fullFormatted)
    }

    @Test
    fun testCountdownCalculation() {
        val epochUtc = 1789216426000L // 18:03:46 IST
        val ist = IndianStandardTime.fromEpochMillis(epochUtc)

        // Target: 18:30 in 24-hr (26m 14s away)
        val countdown24 = ist.formatCountdownTo("18:30")
        assertEquals("26m 14s", countdown24)

        // Target: 6:30 PM in 12-hr standard format (26m 14s away)
        val countdown12 = ist.formatCountdownTo("6:30 PM")
        assertEquals("26m 14s", countdown12)

        // Target: 18:00 (3m 46s ago)
        val past = ist.formatCountdownTo("18:00")
        assertEquals("Departed 3m ago", past)
    }

    @Test
    fun testToStandard12Hour() {
        assertEquals("9:30 AM", "09:30".toStandard12Hour())
        assertEquals("9:30 AM", "9:30".toStandard12Hour())
        assertEquals("9:30 AM", "09:30 AM".toStandard12Hour())
        assertEquals("6:42 PM", "18:42".toStandard12Hour())
        assertEquals("12:15 AM", "00:15".toStandard12Hour())
        assertEquals("12:00 PM", "12:00".toStandard12Hour())
        assertEquals("12:00 AM", "00:00".toStandard12Hour())
    }

    @Test
    fun testCalculateExactDynamicEta() {
        assertEquals("9:48 AM", calculateExactDynamicEta("09:30", 18))
        assertEquals("9:30 AM", calculateExactDynamicEta("9:30 AM", 0))
        assertEquals("12:15 PM", calculateExactDynamicEta("11:50 AM", 25))
        assertEquals("12:05 AM", calculateExactDynamicEta("23:45", 20))
        assertEquals("11:32 AM", calculateExactDynamicEta("11:30", 2))
    }

    @Test
    fun testTrainScheduleActiveStatusAt11Am() {
        // Mock 11:00 AM IST
        val ist11Am = IstTime(
            year = 2026, month = 9, monthName = "Sep", day = 15,
            hour = 11, minute = 0, second = 0, epochMillis = 0L
        )

        // 1. Vande Bharat 22223 (06:20 to 11:40) -> ACTIVE NOW
        val vbStatus = evaluateTrainScheduleStatus("06:20", "11:40", 0, ist11Am, "09:30")
        assertEquals(OfficialRunStatus.ACTIVE_NOW, vbStatus.status)
        assertTrue(vbStatus.isCurrentlyActive)
        assertEquals("9:30 AM", vbStatus.exactDynamicArrivalAtNashik)

        // 2. Panchavati 12109 (18:15 to 23:05) -> UPCOMING TODAY
        val panchavatiStatus = evaluateTrainScheduleStatus("18:15", "23:05", 18, ist11Am, "21:38")
        assertEquals(OfficialRunStatus.UPCOMING_TODAY, panchavatiStatus.status)
        assertFalse(panchavatiStatus.isCurrentlyActive)
        assertEquals("9:56 PM", panchavatiStatus.exactDynamicArrivalAtNashik)

        // 3. Punjab Mail 12137 (19:35 to 02:30 overnight) -> UPCOMING TODAY at 11 AM
        val punjabMailStatus = evaluateTrainScheduleStatus("19:35", "02:30", 20, ist11Am, "22:47")
        assertEquals(OfficialRunStatus.UPCOMING_TODAY, punjabMailStatus.status)
        assertFalse(punjabMailStatus.isCurrentlyActive)
    }

    @Test
    fun testTrainScheduleActiveStatusAtNight() {
        // Mock 8:30 PM IST (20:30)
        val ist830Pm = IstTime(
            year = 2026, month = 9, monthName = "Sep", day = 15,
            hour = 20, minute = 30, second = 0, epochMillis = 0L
        )

        // Panchavati 12109 (18:15 to 23:05) -> ACTIVE NOW
        val panchavatiStatus = evaluateTrainScheduleStatus("18:15", "23:05", 18, ist830Pm, "21:38")
        assertEquals(OfficialRunStatus.ACTIVE_NOW, panchavatiStatus.status)
        assertTrue(panchavatiStatus.isCurrentlyActive)

        // Vande Bharat 22223 (06:20 to 11:40) -> COMPLETED TODAY
        val vbStatus = evaluateTrainScheduleStatus("06:20", "11:40", 0, ist830Pm, "09:30")
        assertEquals(OfficialRunStatus.COMPLETED_TODAY, vbStatus.status)
        assertFalse(vbStatus.isCurrentlyActive)
    }
}
