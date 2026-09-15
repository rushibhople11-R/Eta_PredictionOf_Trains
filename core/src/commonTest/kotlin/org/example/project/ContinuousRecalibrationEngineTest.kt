package org.example.project

import org.example.project.data.NashikStationData
import org.example.project.engine.ContinuousRecalibrationEngine
import org.example.project.model.LiveOperationalUpdate
import org.example.project.model.RecalibrationState
import kotlin.test.*

class ContinuousRecalibrationEngineTest {

    @BeforeTest
    fun setup() {
        ContinuousRecalibrationEngine.resetState()
    }

    @Test
    fun testCompleteTenStepPipelineExecution() {
        val train = NashikStationData.trainPanchavati12109

        // Seed initial state
        ContinuousRecalibrationEngine.seedInitialState(
            RecalibrationState(
                trainNumber = train.trainNumber,
                lastStationCode = "DR",
                lastPredictedEtaHHmm = "18:39",
                lastObservedDelayMinutes = 10,
                lastPredictedDelayMinutes = 12,
                lastConfidenceScore = 88,
                lastUpdateEpoch = System.currentTimeMillis() - 300_000L // 5 mins ago
            )
        )

        // Simulate incoming live telemetry update where delay increased by +3 mins
        val update = LiveOperationalUpdate(
            updateId = "UPD-001",
            trainNumber = train.trainNumber,
            stationCode = "DR",
            speedKmph = 78.0,
            observedDelayMinutes = 13, // increased from 10
            timestampEpoch = System.currentTimeMillis()
        )

        val report = ContinuousRecalibrationEngine.processLiveUpdate(update, train)

        assertEquals(train.trainNumber, report.trainNumber)
        assertEquals("18:39", report.previousEtaHHmm)
        assertNotNull(report.currentEtaHHmm)
        assertNotNull(report.lastUpdatedHHmm)
        assertEquals(10, report.stepAuditLogs.size) // All 10 steps executed
        assertEquals("LIVE UPDATE", report.stepAuditLogs[0].stepName)
        assertEquals("DATA VALIDATION", report.stepAuditLogs[1].stepName)
        assertEquals("UPDATE DASHBOARD", report.stepAuditLogs[9].stepName)
        assertTrue(report.changeReason.isNotBlank())
        assertTrue(report.confidencePercent in 70..96)
    }

    @Test
    fun testSegmentTravelTimeIncreasedReason() {
        val train = NashikStationData.trainPanchavati12109

        ContinuousRecalibrationEngine.seedInitialState(
            RecalibrationState(
                trainNumber = train.trainNumber,
                lastStationCode = "DR",
                lastPredictedEtaHHmm = "18:39",
                lastObservedDelayMinutes = 8,
                lastPredictedDelayMinutes = 10,
                lastConfidenceScore = 90,
                lastUpdateEpoch = System.currentTimeMillis() - 180_000L
            )
        )

        // Incoming update with delay delta > 0
        val update = LiveOperationalUpdate(
            updateId = "UPD-002",
            trainNumber = train.trainNumber,
            stationCode = "DR",
            speedKmph = 55.0,
            observedDelayMinutes = 12, // +4 min jump
            timestampEpoch = System.currentTimeMillis()
        )

        val report = ContinuousRecalibrationEngine.processLiveUpdate(update, train)

        assertTrue(report.changeMinutes > 0, "ETA change must be positive when delay increases")
        assertTrue(
            report.changeReason.contains("segment travel time", ignoreCase = true) ||
                    report.changeReason.contains("increased", ignoreCase = true),
            "Reason must explain segment travel time increase: ${report.changeReason}"
        )
    }

    @Test
    fun testValidationRejectsImpossibleSpeed() {
        val train = NashikStationData.trainPanchavati12109

        val invalidUpdate = LiveOperationalUpdate(
            updateId = "UPD-ERR",
            trainNumber = train.trainNumber,
            stationCode = "DR",
            speedKmph = 240.0, // Impossible speed for Indian broad gauge train
            observedDelayMinutes = 5
        )

        val report = ContinuousRecalibrationEngine.processLiveUpdate(invalidUpdate, train)

        // Pipeline should capture validation rejection in step 2
        val step2 = report.stepAuditLogs.find { it.stepNumber == 2 }
        assertNotNull(step2)
        assertEquals("REJECTED", step2.status)
        assertTrue(report.triggeredAlerts.any { it.title.contains("Validation") })
    }

    @Test
    fun testHighDelayTriggersCriticalAlert() {
        val train = NashikStationData.trainPanchavati12109

        val severeDelayUpdate = LiveOperationalUpdate(
            updateId = "UPD-SEV",
            trainNumber = train.trainNumber,
            stationCode = "KYN",
            speedKmph = 50.0,
            observedDelayMinutes = 26, // Severe delay >= 20m
            timestampEpoch = System.currentTimeMillis()
        )

        val report = ContinuousRecalibrationEngine.processLiveUpdate(severeDelayUpdate, train)

        assertTrue(report.triggeredAlerts.isNotEmpty(), "Severe delay must trigger alert")
        assertTrue(report.triggeredAlerts.any { it.severity == org.example.project.model.AlertSeverity.CRITICAL })
    }
}
