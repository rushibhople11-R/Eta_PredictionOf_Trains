package org.example.project

import org.example.project.db.*
import kotlin.test.*

class RailNexusDatabaseTest {

    private lateinit var db: RailNexusDatabase

    @BeforeTest
    fun setup() {
        db = RailNexusDatabase()
    }

    @Test
    fun testInitialStationsSeed() {
        val stations = db.getAllStations()
        assertTrue(stations.isNotEmpty(), "Stations should be pre-seeded")
        
        val nashikRoad = db.getStation("NK")
        assertNotNull(nashikRoad, "Nashik Road (NK) station must exist")
        assertEquals("Nashik Road", nashikRoad.stationName)
        assertTrue(nashikRoad.isJunction, "Nashik Road should be marked as junction")

        val igatpuri = db.getStation("IGP")
        assertNotNull(igatpuri, "Igatpuri (IGP) station must exist")
        assertEquals(19.6967, igatpuri.latitude, 0.001)
    }

    @Test
    fun testInitialTrainsSeed() {
        val trains = db.getAllTrains()
        assertTrue(trains.size >= 3, "At least 3 corridor trains should be pre-seeded")

        val panchavati = db.getTrain("12109")
        assertNotNull(panchavati, "Train 12109 Panchavati Express must exist")
        assertEquals("CSMT", panchavati.sourceStationCode)
        assertEquals("MMR", panchavati.destinationStationCode)

        val vandeBharat = db.getTrain("22223")
        assertNotNull(vandeBharat, "Train 22223 Vande Bharat must exist")
        assertEquals(10, vandeBharat.priorityScore, "Vande Bharat must have highest priority")
    }

    @Test
    fun testScheduleStopSequence() {
        val schedule = db.getScheduleForTrain("12109")
        assertTrue(schedule.isNotEmpty(), "Schedule for 12109 must not be empty")
        
        // Validate stop sequence monotonicity
        val sequences = schedule.map { it.stopSequence }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), sequences, "Stops must strictly increase in sequence")
        
        // Validate origin and destination in schedule
        assertEquals("CSMT", schedule.first().stationCode)
        assertEquals("MMR", schedule.last().stationCode)
        
        // Validate Nashik Road sequence
        val nkStop = schedule.find { it.stationCode == "NK" }
        assertNotNull(nkStop)
        assertEquals(6, nkStop.stopSequence)
        assertEquals(188, nkStop.distanceFromSourceKm)
    }

    @Test
    fun testLivePositionAndMovement() {
        val latestPosition = db.getLatestPosition("run-12109-today")
        assertNotNull(latestPosition, "Latest position for active run should exist")
        assertEquals("DVL-NK Block Section 1", latestPosition.currentSegmentBlock)
        assertEquals(104.0, latestPosition.speedKmph)

        val movements = db.getMovementsForRun("run-12109-today")
        assertTrue(movements.isNotEmpty(), "Actual movements should be recorded")
        val dvlMovement = movements.find { it.stationCode == "DVL" }
        assertNotNull(dvlMovement)
        assertEquals(18, dvlMovement.observedDelayMinutes)
    }

    @Test
    fun testEtaPredictionsAndConfidence() {
        val predictions = db.getLatestPredictions("run-12109-today")
        assertTrue(predictions.isNotEmpty(), "Predictions should exist for active run")

        val nkPrediction = predictions.find { it.targetStationCode == "NK" }
        assertNotNull(nkPrediction)
        assertEquals("09:51", nkPrediction.predictedEta)
        assertEquals(0.87, nkPrediction.confidenceScore)
        assertTrue(nkPrediction.primaryExplanation.contains("downstream congestion"))
    }

    @Test
    fun testDelayEventsRootCauseAttribution() {
        val delayEvents = db.getDelayEvents("run-12109-today")
        assertEquals(2, delayEvents.size, "Should have 2 pre-seeded delay events")
        
        val ghatDelay = delayEvents.find { it.primaryReasonCategory == "THAL_GHAT_BANKING" }
        assertNotNull(ghatDelay)
        assertEquals(11, ghatDelay.delayDurationMinutes)

        val headwayDelay = delayEvents.find { it.primaryReasonCategory == "HEADWAY_SEPARATION" }
        assertNotNull(headwayDelay)
        assertEquals(7, headwayDelay.delayDurationMinutes)
    }

    @Test
    fun testAlertsManagement() {
        val activeAlerts = db.getActiveAlerts()
        assertTrue(activeAlerts.isNotEmpty(), "Active alerts must exist")

        val odhaAlert = activeAlerts.find { it.stationCode == "ODH" }
        assertNotNull(odhaAlert)
        assertEquals("CAUTION_ORDER", odhaAlert.alertType)

        // Resolve alert
        db.resolveAlert(odhaAlert.alertId)
        val afterResolve = db.getActiveAlerts()
        assertNull(afterResolve.find { it.alertId == odhaAlert.alertId }, "Resolved alert should not be in active list")
    }

    @Test
    fun testModelPerformanceEvaluation() {
        // Evaluate accuracy of prediction vs ground truth arrival
        val predictedEtaEpoch = 1789300260000L // 09:51:00
        val actualArrivalEpoch = 1789300380000L // 09:53:00 (2 minutes off)
        
        val evaluation = db.evaluatePredictionAccuracy(
            predictionId = "pred-nk-101",
            actualMovementId = 501L,
            predictedEtaEpoch = predictedEtaEpoch,
            actualArrivalEpoch = actualArrivalEpoch,
            modelVersion = "v2.4.1-lgbm-quantile"
        )

        assertEquals(2.0, evaluation.absoluteErrorMinutes, 0.01)
        assertEquals(4.0, evaluation.squaredError, 0.01)

        val (mae, count) = db.getModelPerformanceSummary("v2.4.1-lgbm-quantile")
        assertEquals(1, count)
        assertEquals(2.0, mae, 0.01)
    }

    @Test
    fun testWhatIfScenarioIntegration() {
        val scenarios = db.getWhatIfScenariosForRun("run-12109-today")
        assertEquals(1, scenarios.size)
        
        val scenario = scenarios.first()
        assertEquals("DVL", scenario.regulationStationCode)
        assertEquals(7, scenario.hypotheticalDelayMinutes)
        assertEquals(19, scenario.corridorMinutesSaved)
        assertEquals("SAFE_TO_EXECUTE", scenario.safetyAuditStatus)
        assertEquals("APPROVED", scenario.controllerAction)
    }
}
