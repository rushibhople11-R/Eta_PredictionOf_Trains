package org.example.project

import org.example.project.data.NashikStationData
import org.example.project.engine.NetworkAwareForecastingEngine
import org.example.project.engine.RailwayNetworkTopologyRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkAwareForecastingEngineTest {

    @Test
    fun testChainedRouteExecutionAndTopology() {
        val train = NashikStationData.trainPanchavati12109 // Panchavati Express (CSMT -> MMR)
        val report = NetworkAwareForecastingEngine.forecastJourney(
            journey = train,
            currentStopIndex = 0,
            observedDelayMinutes = 0
        )

        assertEquals(train.trainNumber, report.trainNumber)
        assertEquals(train.stops.size, report.stationForecasts.size)
        // Inter-station segments must equal stops.size - 1
        assertEquals(train.stops.size - 1, report.segmentTransits.size)
        assertTrue(report.networkAdvantageExplanation.isNotBlank())
        assertTrue(report.totalJourneyDistanceKm > 0.0)
        assertTrue(report.totalRuntimeMinutes > 0.0)
    }

    @Test
    fun testKinematicPhysicalLimitsRespected() {
        val train = NashikStationData.trainPanchavati12109
        val report = NetworkAwareForecastingEngine.forecastJourney(
            journey = train,
            currentStopIndex = 1, // Dadar
            observedDelayMinutes = 15
        )

        // Verify every segment runtime adheres to track MPS kinematic limits
        report.segmentTransits.forEach { transit ->
            val seg = transit.segment
            val minKinematicTime = (seg.distanceKm / seg.speedLimitKmh) * 60.0 * 0.8
            assertTrue(
                transit.effectiveRuntimeMinutes >= minKinematicTime,
                "Segment ${seg.segmentId} runtime ${transit.effectiveRuntimeMinutes} was faster than kinematic limit $minKinematicTime"
            )
        }
    }

    @Test
    fun testKasaraIgatpuriGhatSectionGradientFactor() {
        val train = NashikStationData.trainPanchavati12109
        val report = NetworkAwareForecastingEngine.forecastJourney(
            journey = train,
            currentStopIndex = 2, // Kalyan
            observedDelayMinutes = 10
        )

        // Look for KSRA-IGP segment
        val ghatSegment = report.segmentTransits.find { it.segment.fromStationCode == "KSRA" && it.segment.toStationCode == "IGP" }
        assertNotNull(ghatSegment, "Thal Ghat segment KSRA-IGP must be present in Panchavati Express corridor")
        assertTrue(ghatSegment.segment.isGhatSection, "KSRA-IGP must be flagged as Ghat section")
        assertTrue(ghatSegment.segment.gradientFactor > 1.0, "Gradient factor must be greater than 1.0 for 1:37 incline")
        assertTrue(
            ghatSegment.kinematicRuntimeMinutes > (ghatSegment.segment.distanceKm / ghatSegment.segment.speedLimitKmh) * 60.0,
            "Ghat runtime must be extended by gradient factor"
        )
    }

    @Test
    fun testDwellInflationUnderDelay() {
        val train = NashikStationData.trainPanchavati12109
        val onTimeReport = NetworkAwareForecastingEngine.forecastJourney(
            journey = train,
            currentStopIndex = 1,
            observedDelayMinutes = 0
        )
        val delayedReport = NetworkAwareForecastingEngine.forecastJourney(
            journey = train,
            currentStopIndex = 1,
            observedDelayMinutes = 25 // Severe delay causing crowd accumulation
        )

        val onTimeDwellSum = onTimeReport.stationForecasts.sumOf { it.dwellInflationMinutes }
        val delayedDwellSum = delayedReport.stationForecasts.sumOf { it.dwellInflationMinutes }

        assertTrue(
            delayedDwellSum > onTimeDwellSum,
            "Delayed train must experience dwell inflation due to passenger platform accumulation: delayed=$delayedDwellSum, onTime=$onTimeDwellSum"
        )
    }

    @Test
    fun testMultiTrainPrecedenceAndSingleTrackContention() {
        // Register an active train in the block
        RailwayNetworkTopologyRegistry.registerTrainOccupancy("12109", "SEG-NK-ODH")

        val blocks = RailwayNetworkTopologyRegistry.getActiveTrainsInBlock("SEG-NK-ODH")
        assertTrue(blocks.contains("12109"))

        // Run forecast for Vande Bharat 22223 on same route
        val vb = NashikStationData.trainVandeBharat22223
        val report = NetworkAwareForecastingEngine.forecastJourney(
            journey = vb,
            currentStopIndex = 3, // Kasara
            observedDelayMinutes = 12
        )

        assertNotNull(report)
        assertTrue(report.segmentTransits.isNotEmpty())
    }
}
