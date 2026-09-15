package org.example.project

import org.example.project.data.RealTrainRecordRegistry
import org.example.project.engine.HistoricalPunctualityEngine
import org.example.project.engine.RtisTelemetryEngine
import org.example.project.engine.SignalAspect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealTrainTrackingTest {

    @Test
    fun testRealTrainRegistryMasterList() {
        val allTrains = RealTrainRecordRegistry.getAllRealTrains()
        assertTrue(allTrains.isNotEmpty(), "Master real train list should not be empty")

        val panchavati = RealTrainRecordRegistry.getTrainByNumber("12109")
        assertNotNull(panchavati, "Train 12109 Panchavati must be present")
        assertEquals("Panchavati Superfast Express", panchavati.trainName)
        assertEquals("CSMT", panchavati.source.code)
        assertEquals("MMR", panchavati.destination.code)
        assertTrue(panchavati.stops.size >= 8, "Stops list must be complete")

        val vandeBharat = RealTrainRecordRegistry.getTrainByNumber("22223")
        assertNotNull(vandeBharat, "Train 22223 Vande Bharat must be present")
        assertTrue(vandeBharat.onTimePunctuality30Days > 90.0, "Vande Bharat punctuality should be > 90%")
    }

    @Test
    fun testSearchRealTrains() {
        val resultsNumber = RealTrainRecordRegistry.searchTrains("12951")
        assertEquals(1, resultsNumber.size)
        assertEquals("12951", resultsNumber.first().trainNumber)

        val resultsName = RealTrainRecordRegistry.searchTrains("Punjab")
        assertTrue(resultsName.isNotEmpty())
        assertEquals("12137", resultsName.first().trainNumber)
    }

    @Test
    fun test30DayHistoricalRunHistory() {
        val history = HistoricalPunctualityEngine.get30DayRunHistory("12109")
        assertEquals(30, history.size, "History must contain 30 daily run records")
        assertTrue(history.all { it.trainNumber == "12109" })
        assertTrue(history.any { it.status == "ON_TIME" })
        assertTrue(history.any { it.primaryDelayReason.isNotBlank() })
    }

    @Test
    fun testRtisSatelliteTelemetrySimulation() {
        val telemetry = RtisTelemetryEngine.generateLiveTelemetry("12109", 0)
        assertEquals("12109", telemetry.trainNumber)
        assertTrue(telemetry.currentSpeedKmh > 0.0)
        assertTrue(telemetry.latitude in 18.0..22.0)
        assertTrue(telemetry.longitude in 72.0..75.0)
        assertTrue(telemetry.kilometerPost.startsWith("KM"))
        assertTrue(telemetry.signalAspect in SignalAspect.values())
        assertTrue(telemetry.satelliteNavIcStatus.contains("NAVIC"))
    }
}
