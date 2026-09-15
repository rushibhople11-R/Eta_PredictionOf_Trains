package org.example.project

import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.engine.*
import org.example.project.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RailNexusCoreEngineTest {

    @Test
    fun testPluggablePredictorStrategySwap() {
        val train = NashikStationData.trainVandeBharat22223
        val telemetry = TrainTelemetrySnapshot(
            trainNumber = train.trainNumber,
            latitude = 19.345,
            longitude = 73.123,
            speedKmH = 110.0,
            currentStopIndex = 1, // Kalyan (KYN)
            currentDelayMinutes = 15,
            timestampEpochMillis = 1773300000000L
        )

        // 1. Feature Engineering
        val features = FeatureEngineeringEngine.extractFeatures(
            journey = train,
            currentStopIndex = 2, // Kalyan (KYN)
            targetStopIndex = 3, // Igatpuri (IGP)
            telemetry = telemetry
        )

        assertEquals("22223", features.trainNumber)
        assertEquals("KYN", features.currentStationCode)
        assertEquals("IGP", features.targetStationCode)
        assertTrue(features.distanceRemainingKm > 0)

        // 2. Baseline Heuristic Predictor
        val heuristicPredictor: IEtaPredictor = HeuristicEtaPredictor()
        val heuristicResult = heuristicPredictor.predictStationArrival(
            features = features,
            scheduledArrivalEpochMillis = 1773303600000L // 1h in future
        )
        assertEquals(ModelArchitecture.HEURISTIC_BASELINE, heuristicResult.modelArchitecture)
        assertTrue(heuristicResult.predictedDelayMinutes >= 0)

        // 3. Hot-Swap with Quantile ML Predictor (Same Interface Contract!)
        val quantilePredictor: IEtaPredictor = QuantileEtaPredictor()
        val quantileResult = quantilePredictor.predictStationArrival(
            features = features,
            scheduledArrivalEpochMillis = 1773303600000L
        )
        assertEquals(ModelArchitecture.QUANTILE_GRADIENT_BOOST, quantileResult.modelArchitecture)
        // Quantile bounds must be logically ordered: p10 <= predicted <= p90
        assertTrue(quantileResult.quantileLowerMinutes <= quantileResult.predictedDelayMinutes)
        assertTrue(quantileResult.predictedDelayMinutes <= quantileResult.quantileUpperMinutes)
    }

    @Test
    fun testThalGhatGradientDetection() {
        val train = NashikStationData.trainVandeBharat22223

        // Index 2 is Kasara (KSRA), Index 3 is Igatpuri (IGP)
        val telemetry = TrainTelemetrySnapshot(
            trainNumber = train.trainNumber,
            latitude = 19.65,
            longitude = 73.50,
            speedKmH = 55.0,
            currentStopIndex = 2, // Kasara
            currentDelayMinutes = 10,
            timestampEpochMillis = 1773300000000L
        )

        val featuresGhat = FeatureEngineeringEngine.extractFeatures(
            journey = train,
            currentStopIndex = 2,
            targetStopIndex = 3,
            telemetry = telemetry
        )

        assertEquals(1.37, featuresGhat.gradientFactor, "Kasara-Igatpuri must have 1.37 Thal Ghat gradient factor")

        // Explainability engine must cite Thal Ghat operations
        val predictor = HeuristicEtaPredictor()
        val result = predictor.predictStationArrival(featuresGhat, 1773303600000L)
        val explanations = ExplainabilityEngine.synthesizeExplanation(result, featuresGhat)
        assertTrue(explanations.any { it.contains("Thal Ghat") || it.contains("gradient") })
    }

    @Test
    fun testNetworkAwareCrossingPrecedence() {
        val vandeBharat = NashikStationData.trainVandeBharat22223 // Priority 10
        val freightTrain = TrainJourney(
            trainNumber = "BOXN-9821",
            trainName = "Container Freight Express",
            trainType = TrainType.PASSENGER, // Priority 2
            source = Station("CSMT", "Mumbai CSMT"),
            destination = Station("BSL", "Bhusawal"),
            stops = vandeBharat.stops
        )

        val conflictBlock = TrackBlockOccupancy(
            blockId = "BLK-KSRA-IGP-UP",
            blockName = "Kasara - Igatpuri Single Track Ghat Section",
            isSingleTrack = true,
            lengthKm = 14.0
        )

        val resolution = NetworkAwareAnalysisEngine.resolveCrossingPrecedence(
            trainA = freightTrain,
            trainB = vandeBharat,
            conflictBlock = conflictBlock,
            crossingStationCode = "KSRA"
        )

        assertEquals("22223", resolution.higherPriorityTrainNumber)
        assertEquals("BOXN-9821", resolution.lowerPriorityTrainNumber)
        assertEquals("KSRA", resolution.crossingStationCode)
        assertTrue(resolution.lowerPriorityDelayPenaltyMinutes >= 10)
        assertTrue(resolution.reason.contains("Vande Bharat"))
    }

    @Test
    fun testConfidenceCalculationAndStalenessPenalty() {
        val prediction = PredictionResult(
            pointEtaEpochMillis = 1773303600000L,
            predictedEtaHHmm = "18:45",
            predictedDelayMinutes = 12,
            quantileLowerMinutes = 10,
            quantileUpperMinutes = 14, // Narrow spread of 4 mins
            modelArchitecture = ModelArchitecture.HEURISTIC_BASELINE,
            modelVersion = "v1"
        )

        val currentEpoch = 1773300000000L

        // 1. Fresh telemetry (10s old)
        val freshConfidence = ConfidenceCalculationEngine.computeConfidence(
            prediction = prediction,
            telemetryTimestampEpochMillis = currentEpoch - 10_000L,
            currentEpochMillis = currentEpoch
        )
        assertEquals(ConfidenceLevel.HIGH, freshConfidence.level)
        assertTrue(freshConfidence.scorePercent >= 85)

        // 2. Stale telemetry (300s old)
        val staleConfidence = ConfidenceCalculationEngine.computeConfidence(
            prediction = prediction,
            telemetryTimestampEpochMillis = currentEpoch - 300_000L,
            currentEpochMillis = currentEpoch
        )
        assertTrue(staleConfidence.scorePercent < freshConfidence.scorePercent)
    }

    @Test
    fun testAlertEngineTriggers() {
        val currentEpoch = 1773300000000L

        // 1. Wakeup alert within 10 minutes
        val wakeupAlert = AlertEngine.evaluateWakeupAlert(
            trainNumber = "22223",
            destinationStationCode = "SNSI",
            pointEtaEpochMillis = currentEpoch + (10 * 60_000L),
            currentEpochMillis = currentEpoch
        )
        assertNotNull(wakeupAlert)
        assertEquals(AlertType.WAKEUP, wakeupAlert.type)

        // 2. Connection risk alert when feasibility drops
        val connectionAlert = AlertEngine.evaluateConnectionRisk(
            trainNumber = "22223",
            connectingTrainNumber = "12859",
            interchangeStationCode = "NK",
            feasibilityScore = 35,
            cushionMinutes = 3,
            currentEpochMillis = currentEpoch
        )
        assertNotNull(connectionAlert)
        assertEquals(AlertType.CONNECTION_RISK, connectionAlert.type)
        assertEquals(AlertSeverity.CRITICAL, connectionAlert.severity)

        // 3. Delay spike alert
        val spikeAlert = AlertEngine.evaluateDelaySpike(
            trainNumber = "22223",
            currentStationCode = "IGP",
            previousDelayMinutes = 5,
            currentDelayMinutes = 20,
            currentEpochMillis = currentEpoch
        )
        assertNotNull(spikeAlert)
        assertEquals(AlertType.DELAY_SPIKE, spikeAlert.type)
        assertTrue(spikeAlert.message.contains("15m delay surge"))
    }
}
