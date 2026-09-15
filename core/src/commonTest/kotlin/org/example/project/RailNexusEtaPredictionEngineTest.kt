package org.example.project

import org.example.project.data.NashikStationData
import org.example.project.engine.*
import org.example.project.model.*
import kotlin.test.*

class RailNexusEtaPredictionEngineTest {

    private val journey = NashikStationData.trainVandeBharat22223
    private val nowEpoch = 1718000000000L // arbitrary fixed test epoch

    @Test
    fun testFeatureEngineeringExtractsAll15Features() {
        val telemetry = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.6467,
            longitude = 73.4839, // Kasara
            speedKmH = 95.0,
            currentStopIndex = 2, // Kasara
            currentDelayMinutes = 12,
            timestampEpochMillis = nowEpoch
        )

        val features = FeatureEngineeringEngine.extractFeatures(
            journey = journey,
            currentStopIndex = 2,
            targetStopIndex = 3, // Igatpuri
            telemetry = telemetry,
            precedingHeadwayMinutes = 8,
            weatherImpairmentIndex = 0.3f
        )

        // 1. Scheduled arrival time
        assertTrue(features.scheduledArrivalEpochMillis > 0L)
        assertEquals("08:43", features.scheduledArrivalHHmm)

        // 2. Scheduled departure time
        assertTrue(features.scheduledDepartureEpochMillis > 0L)
        assertEquals("07:15", features.scheduledDepartureHHmm)

        // 3. Current train position
        assertEquals(19.6467, features.currentLatitude)
        assertEquals(73.4839, features.currentLongitude)

        // 4. Current speed
        assertEquals(95.0, features.currentSpeedKmH)

        // 5. Current delay
        assertEquals(12, features.currentDelayMinutes)

        // 6. Previous station delay
        assertEquals(12, features.previousStationDelayMinutes)

        // 7. Route information
        assertEquals("CR-CSMT-SNSI-01", features.routeId)
        assertEquals("CSMT", features.originCode)
        assertEquals("IGP", features.targetStationCode)

        // 8. Station sequence
        assertEquals(3, features.currentStationSequence)
        assertEquals(4, features.targetStationSequence)
        assertEquals(journey.stops.size, features.totalStationsInRoute)

        // 9. Historical travel time
        assertTrue(features.historicalTravelTimeMinutes > 0.0)

        // 10. Historical delay patterns
        assertTrue(features.historicalMeanDelayMinutes in 1.0..25.0)
        assertTrue(features.historicalDelayStdDev in 0.5..10.0)

        // 11. Time of day
        assertTrue(features.timeOfDayMinutes in 0..1439)
        assertTrue(features.timeOfDayBucket.isNotBlank())

        // 12. Day of week
        assertTrue(features.dayOfWeek in 1..7)
        assertTrue(features.dayOfWeekName.isNotBlank())

        // 13. Downstream operational conditions
        assertTrue(features.downstreamCongestionFactor >= 1.0)
        assertEquals(8, features.precedingHeadwayMinutes)
        assertEquals(0.3f, features.weatherImpairmentIndex)
        // Kasara to Igatpuri has Thal Ghat incline
        assertEquals(1.37, features.gradientFactor)

        // 14. Accumulated delay
        assertEquals(12, features.accumulatedDelayMinutes)
        assertTrue(features.scheduledSlackMinutes >= 0)

        // 15. Segment travel time
        assertTrue(features.scheduledSegmentTravelTimeMinutes > 0)
        assertTrue(features.kinematicSegmentTravelTimeMinutes > 0.0)
    }

    @Test
    fun testBaselineLinearRegressionPredictor() {
        val predictor = LinearRegressionEtaPredictor()
        assertEquals(ModelArchitecture.LINEAR_REGRESSION, predictor.modelArchitecture)
        assertEquals("v1.3.0-ridge-baseline", predictor.modelVersion)

        val telemetry = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.2437,
            longitude = 73.1355,
            speedKmH = 110.0,
            currentStopIndex = 1,
            currentDelayMinutes = 10,
            timestampEpochMillis = nowEpoch
        )

        val features = FeatureEngineeringEngine.extractFeatures(
            journey = journey,
            currentStopIndex = 1,
            targetStopIndex = 2,
            telemetry = telemetry
        )

        val result = predictor.predictStationArrival(features, nowEpoch + (30 * 60_000L))

        assertTrue(result.predictedDelayMinutes >= 0)
        assertTrue(result.quantileLowerMinutes <= result.predictedDelayMinutes)
        assertTrue(result.predictedDelayMinutes <= result.quantileUpperMinutes)
        assertTrue(result.confidenceScore in 0.60..0.98)
        assertTrue(result.predictedArrivalEpochMillis > 0L)
        assertTrue(result.featureImportances.containsKey("current_delay"))
    }

    @Test
    fun testChampionQuantileGradientBoostingPredictor() {
        val predictor = QuantileEtaPredictor()
        assertEquals(ModelArchitecture.QUANTILE_GRADIENT_BOOST, predictor.modelArchitecture)

        val telemetry = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.6467,
            longitude = 73.4839,
            speedKmH = 60.0,
            currentStopIndex = 2, // Kasara (approaching Thal Ghat)
            currentDelayMinutes = 14,
            timestampEpochMillis = nowEpoch
        )

        val features = FeatureEngineeringEngine.extractFeatures(
            journey = journey,
            currentStopIndex = 2,
            targetStopIndex = 3, // Igatpuri
            telemetry = telemetry,
            precedingHeadwayMinutes = 6, // Close headway compression
            weatherImpairmentIndex = 0.4f // Fog/monsoon
        )

        val result = predictor.predictStationArrival(features, nowEpoch + (35 * 60_000L))

        // Non-linear effects should increase predicted delay
        assertTrue(result.predictedDelayMinutes > 14)
        // Asymmetric quantiles: p10 <= p50 <= p90
        assertTrue(result.quantileLowerMinutes <= result.predictedDelayMinutes)
        assertTrue(result.predictedDelayMinutes <= result.quantileUpperMinutes)
        // Check feature attribution breakdown
        assertTrue(result.featureImportances.containsKey("headway_compression"))
        assertTrue(result.featureImportances.containsKey("weather_impairment"))
        assertTrue(result.featureImportances.containsKey("gradient_resistance"))
    }

    @Test
    fun testContinuousUpdatePipelineFlow() {
        val orchestrator = ContinuousEtaPipelineOrchestrator(QuantileEtaPredictor())

        // 1. Initial Telemetry at Kalyan
        val t1 = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.2437,
            longitude = 73.1355,
            speedKmH = 100.0,
            currentStopIndex = 1,
            currentDelayMinutes = 5,
            timestampEpochMillis = nowEpoch
        )

        val res1 = orchestrator.processLiveTelemetry(t1, journey, nowEpoch)
        assertNotNull(res1)
        assertEquals("Initial baseline ETA established based on real-time track telemetry.", res1.comparison?.changeExplanation)

        // 2. Continuous Update: Delay spike due to headway congestion (120s later, ~2 km movement)
        val t2 = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.2580,
            longitude = 73.1480,
            speedKmH = 45.0, // Slowing down
            currentStopIndex = 1,
            currentDelayMinutes = 10,
            timestampEpochMillis = nowEpoch + 120_000L
        )

        val res2 = orchestrator.processLiveTelemetry(t2, journey, nowEpoch + 120_000L, precedingHeadwayMinutes = 5)
        assertNotNull(res2)
        val comp2 = res2.comparison
        assertNotNull(comp2)
        assertTrue(comp2.deltaMinutes > 0)
        assertTrue(comp2.changeExplanation.contains("ETA delayed by"))

        // 3. Continuous Update: Speed recovery & slack absorption (180s later, ~3 km movement)
        val t3 = TrainTelemetrySnapshot(
            trainNumber = "22223",
            latitude = 19.2800,
            longitude = 73.1650,
            speedKmH = 115.0, // Running at full MPS
            currentStopIndex = 1,
            currentDelayMinutes = 4,
            timestampEpochMillis = nowEpoch + 300_000L
        )

        val res3 = orchestrator.processLiveTelemetry(t3, journey, nowEpoch + 300_000L, precedingHeadwayMinutes = 18)
        assertNotNull(res3)
        val comp3 = res3.comparison
        assertNotNull(comp3)
        assertTrue(comp3.deltaMinutes < 0)
        assertTrue(comp3.changeExplanation.contains("ETA improved by"))
    }

    @Test
    fun testKinematicValidationRejectsAnomalies() {
        val orchestrator = ContinuousEtaPipelineOrchestrator()

        // Unrealistic speed (> 180 km/h)
        val tSpeed = TrainTelemetrySnapshot("22223", 19.2437, 73.1355, 240.0, 0.0, 1, 0, nowEpoch)
        val vSpeed = orchestrator.validateTelemetry(tSpeed, nowEpoch)
        assertTrue(vSpeed is TelemetryValidationResult.Rejected)

        // Outside India coordinates
        val tCoords = TrainTelemetrySnapshot("22223", 51.5074, -0.1278, 90.0, 0.0, 1, 0, nowEpoch)
        val vCoords = orchestrator.validateTelemetry(tCoords, nowEpoch)
        assertTrue(vCoords is TelemetryValidationResult.Rejected)

        // Normal point accepted
        val tOk = TrainTelemetrySnapshot("22223", 19.2437, 73.1355, 105.0, 0.0, 1, 0, nowEpoch)
        val vOk = orchestrator.validateTelemetry(tOk, nowEpoch)
        assertTrue(vOk is TelemetryValidationResult.Accepted)
    }

    @Test
    fun testModelEvaluationEngineMetrics() {
        val report = ModelEvaluationEngine.generateCorridorBenchmarkEvaluation(
            modelVersion = "v2.4.1-lgbm-quantile",
            architecture = ModelArchitecture.QUANTILE_GRADIENT_BOOST
        )

        // Realistic railway bounds (1.0 to 3.5 min MAE)
        assertTrue(report.meanAbsoluteErrorMinutes in 0.8..3.5, "MAE must be realistic, actual: ${report.meanAbsoluteErrorMinutes}")
        assertTrue(report.rootMeanSquaredErrorMinutes >= report.meanAbsoluteErrorMinutes, "RMSE must be >= MAE mathematically")
        assertTrue(report.totalPredictions > 0)
        assertFalse(report.predictedVsActualArrivals.isEmpty())

        // Validate error distribution histogram
        val hist = report.errorDistribution
        val totalPct = hist.under1MinPercent + hist.oneTo3MinsPercent + hist.threeTo5MinsPercent + hist.over5MinsPercent
        assertTrue(totalPct in 99.0..101.0, "Histogram percentages should sum to ~100%")
    }
}
