package org.example.project

import org.example.project.data.SampleRailwayData
import org.example.project.engine.DelayPropagationEngine
import org.example.project.model.CascadeRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DelayPropagationEngineTest {

    @Test
    fun testOnTimeTrainMaintainsSchedule() {
        val train = SampleRailwayData.train12424
        val predictions = DelayPropagationEngine.predictJourney(
            journey = train,
            currentStopIndex = 1, // At Kanpur (CNB)
            currentDelayMinutes = 0
        )

        assertEquals(train.stops.size, predictions.size)
        val cnb = predictions[1]
        assertTrue(cnb.isCurrentStation)
        assertEquals(0, cnb.delayMinutes)
        assertEquals(cnb.scheduledTime, cnb.predictedEta)

        val destination = predictions.last()
        assertEquals(0, destination.delayMinutes)
        assertEquals(destination.scheduledTime, destination.predictedEta)
    }

    @Test
    fun testDelaySlackRecovery() {
        val train = SampleRailwayData.train12424
        val initialDelay = 45

        val predictions = DelayPropagationEngine.predictJourney(
            journey = train,
            currentStopIndex = 1, // At Kanpur (CNB) with 45m delay
            currentDelayMinutes = initialDelay
        )

        val currentStop = predictions[1]
        assertEquals(initialDelay, currentStop.delayMinutes)

        // Across DDU and Patna, high slack buffers (20m + 25m) should recover time
        val finalStop = predictions.last()
        assertTrue(
            finalStop.delayMinutes < initialDelay,
            "Expected final delay (${finalStop.delayMinutes}) to be less than initial delay ($initialDelay) due to slack buffers"
        )
    }

    @Test
    fun testTimeOffsetFormatting() {
        assertEquals("17:20", DelayPropagationEngine.formatTimeOffset("16:20", 60))
        assertEquals("00:15", DelayPropagationEngine.formatTimeOffset("23:45", 30))
        assertEquals("01:00", DelayPropagationEngine.formatTimeOffset("00:30", 30))
    }

    @Test
    fun testAnalyzePropagationDistinguishesObservedFromPredicted() {
        val journey = org.example.project.data.NashikStationData.trainVandeBharat22223
        val currentIdx = 2 // Kalyan (KYN)
        val initialDelay = 12

        val report = DelayPropagationEngine.analyzePropagation(
            journey = journey,
            currentStopIndex = currentIdx,
            observedDelayMinutes = initialDelay
        )

        assertEquals("22223", report.trainNumber)
        assertEquals("KYN", report.currentStationCode)
        assertEquals(initialDelay, report.observedDelayMinutes)
        assertTrue(report.stationProgressions.isNotEmpty())

        // Past stations: OBSERVED_ACTUAL
        val past = report.stationProgressions.filter { it.sequence - 1 < currentIdx }
        past.forEach {
            assertEquals(org.example.project.model.DelayObservationType.OBSERVED_ACTUAL, it.observationType)
        }

        // Current station: OBSERVED_ACTUAL with initial delay
        val current = report.stationProgressions[currentIdx]
        assertEquals(org.example.project.model.DelayObservationType.OBSERVED_ACTUAL, current.observationType)
        assertEquals(initialDelay, current.delayMinutes)

        // Upcoming stations: PREDICTED_DOWNSTREAM
        val upcoming = report.stationProgressions.filter { it.sequence - 1 > currentIdx }
        assertTrue(upcoming.isNotEmpty())
        upcoming.forEach {
            assertEquals(org.example.project.model.DelayObservationType.PREDICTED_DOWNSTREAM, it.observationType)
        }

        // Check severity and confidence
        assertTrue(report.confidencePercent in 68..95)
        assertTrue(report.expectedAffectedStationsCount > 0)
        assertTrue(report.progressionSummary.contains("KYN (+12m OBSERVED)"))
    }

    @Test
    fun testCascadingDelayProgressionUnderCongestion() {
        val journey = org.example.project.data.NashikStationData.trainPanchavati12109
        val currentIdx = 3 // Kasara (KSRA) before Thal Ghat
        val initialDelay = 10

        val report = DelayPropagationEngine.analyzePropagation(
            journey = journey,
            currentStopIndex = currentIdx,
            observedDelayMinutes = initialDelay,
            precedingHeadwayMinutes = 6, // Close headway causes cautionary braking
            weatherImpairmentIndex = 0.3f // Visibility restriction
        )

        // Across upcoming stations (IGP, DVL, NK), banking detachment and headway cause compounding delays
        val upcoming = report.stationProgressions.filter { it.sequence - 1 > currentIdx }
        val igp = upcoming.first() // Igatpuri (Ghat top)
        
        // Downstream delay should compound (greater than initial 10m)
        assertTrue(
            igp.delayMinutes > initialDelay,
            "Delay should cascade from +${initialDelay}m at KSRA to higher delay at IGP (actual: ${igp.delayMinutes}m)"
        )
        assertTrue(report.maxProjectedDelayMinutes >= igp.delayMinutes)
        assertTrue(report.severity == org.example.project.model.DelaySeverity.HIGH || report.severity == org.example.project.model.DelaySeverity.CRITICAL || report.severity == org.example.project.model.DelaySeverity.MODERATE)
    }
}
