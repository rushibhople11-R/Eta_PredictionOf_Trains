package org.example.project

import kotlin.test.*
import org.example.project.engine.CorridorSimulationEngine
import org.example.project.engine.HistoricalPunctualityEngine
import org.example.project.engine.HistoricalTrainRunRecord
import org.example.project.model.ContingencyEvent

class CorridorSimulationTest {

    @Test
    fun testCorridorSnapshotGeneration() {
        val engine = CorridorSimulationEngine()
        val snapshot = engine.computeCorridorSnapshot()

        assertNotNull(snapshot)
        assertTrue(snapshot.activeTrains.isNotEmpty(), "Should track multiple concurrent trains")
        assertEquals(7, snapshot.activeTrains.size)
        assertEquals(7, snapshot.corridorBlocks.size)
        assertTrue(snapshot.platformOccupancy.containsKey("Pf 1"))
        assertTrue(snapshot.platformOccupancy.containsKey("Pf 2"))
        assertTrue(snapshot.platformOccupancy.containsKey("Pf 3"))
        assertTrue(snapshot.platformOccupancy.containsKey("Pf 4"))
    }

    @Test
    fun testWhatIfContingencyInjectionAndDelayRipple() {
        val engine = CorridorSimulationEngine()
        val initialSnapshot = engine.computeCorridorSnapshot()
        val panchavatiInitial = initialSnapshot.activeTrains.find { it.trainNumber == "12109" }!!

        // Inject Thal Ghat banking engine detachment contingency
        val ghatIncident = ContingencyEvent(
            id = "THAL_GHAT_INCIDENT",
            title = "Thal Ghat Banking Detach Queue",
            description = "Congestion at Igatpuri banking siding",
            affectedBlockId = "BLOCK_KSRA_IGP",
            affectedTrainNumber = "12109",
            delayPenaltyMinutes = 20,
            isTrackBlocked = false,
            speedLimitKmph = 30
        )
        engine.injectContingency(ghatIncident)

        val updatedSnapshot = engine.computeCorridorSnapshot()
        val panchavatiUpdated = updatedSnapshot.activeTrains.find { it.trainNumber == "12109" }!!

        assertTrue(
            panchavatiUpdated.predictedDelayMinutes >= panchavatiInitial.predictedDelayMinutes + 20,
            "Delay should increase by at least 20 minutes"
        )
        assertEquals(30.0, panchavatiUpdated.currentSpeedKmH, "Speed should drop to speed restriction")
        assertNotNull(updatedSnapshot.systemAlert)

        // Clear contingency
        engine.clearAllContingencies()
        val clearedSnapshot = engine.computeCorridorSnapshot()
        val panchavatiCleared = clearedSnapshot.activeTrains.find { it.trainNumber == "12109" }!!
        assertEquals(panchavatiInitial.predictedDelayMinutes, panchavatiCleared.predictedDelayMinutes)
    }

    @Test
    fun testHistoricalPunctualityReport() {
        val report = HistoricalPunctualityEngine.generatePunctualityReport("Bhusawal (BSL)")

        assertTrue(report.totalRunsRecorded > 0)
        assertTrue(report.onTimePercentage in 0.0..100.0)
        assertTrue(report.averageDelayMinutes >= 0.0)
        assertTrue(report.thalGhatAverageDelayMinutes >= 0.0)
        assertTrue(report.sectionSlackAbsorptionAvgMinutes >= 0.0)
        assertEquals("Bhusawal (BSL)", report.division)
    }
}
