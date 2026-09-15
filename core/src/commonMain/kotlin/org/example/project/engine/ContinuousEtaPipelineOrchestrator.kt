package org.example.project.engine

import org.example.project.model.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Continuous ETA Prediction Pipeline Orchestrator.
 *
 * Implements the continuous update lifecycle:
 * new data -> validate -> generate features -> predict ETA -> calculate confidence -> compare with previous prediction -> explain ETA change -> update dashboard
 */
class ContinuousEtaPipelineOrchestrator(
    var activePredictor: IEtaPredictor = QuantileEtaPredictor()
) {
    // In-memory cache of latest predictions: Key = "$trainNumber:$targetStationCode"
    private val previousPredictions = mutableMapOf<String, PredictionResult>()

    // In-memory cache of previous telemetry for kinematic jump validation
    private val previousTelemetryMap = mutableMapOf<String, TrainTelemetrySnapshot>()

    /**
     * Step 1: Validate Telemetry
     */
    fun validateTelemetry(
        snapshot: TrainTelemetrySnapshot,
        currentEpochMillis: Long
    ): TelemetryValidationResult {
        if (snapshot.trainNumber.isBlank()) {
            return TelemetryValidationResult.Rejected("Empty train number", snapshot)
        }

        // Kinematic Speed Check (0..180 km/h)
        if (snapshot.speedKmH < 0.0 || snapshot.speedKmH > 180.0) {
            return TelemetryValidationResult.Rejected(
                "Speed ${snapshot.speedKmH} km/h violates kinematic speed limits (0 - 180 km/h)",
                snapshot
            )
        }

        // Indian Railway Territorial Coordinates Check (6°N to 38°N, 68°E to 98°E)
        val validCoords = (snapshot.latitude in 6.0..38.0) && (snapshot.longitude in 68.0..98.0)
        if (!validCoords) {
            return TelemetryValidationResult.Rejected(
                "GPS coordinates (${snapshot.latitude}, ${snapshot.longitude}) out of Indian Railway bounds",
                snapshot
            )
        }

        // Timestamp Sanity (not > 5 mins future, not > 24 hours past)
        val maxFuture = currentEpochMillis + 300_000L
        val maxPast = currentEpochMillis - (24 * 3_600_000L)
        if (snapshot.timestampEpochMillis > maxFuture) {
            return TelemetryValidationResult.Rejected("Telemetry timestamp is in future", snapshot)
        }
        if (snapshot.timestampEpochMillis < maxPast) {
            return TelemetryValidationResult.Rejected("Telemetry timestamp is stale (> 24 hours)", snapshot)
        }

        // Teleportation / Kinematic Jump Check
        val prev = previousTelemetryMap[snapshot.trainNumber]
        if (prev != null && snapshot.timestampEpochMillis > prev.timestampEpochMillis) {
            val deltaSec = (snapshot.timestampEpochMillis - prev.timestampEpochMillis) / 1000.0
            if (deltaSec > 0.0) {
                val dLat = (snapshot.latitude - prev.latitude) * 111.0
                val dLon = (snapshot.longitude - prev.longitude) * 111.0
                val distKm = sqrt(dLat * dLat + dLon * dLon)
                val impliedSpeed = (distKm / deltaSec) * 3600.0
                if (impliedSpeed > 220.0) {
                    return TelemetryValidationResult.Rejected(
                        "GPS jump: implied velocity ${impliedSpeed.toInt()} km/h exceeds 220 km/h physical threshold",
                        snapshot
                    )
                }
            }
        }

        return TelemetryValidationResult.Accepted(snapshot)
    }

    /**
     * Executes the full pipeline for incoming live telemetry.
     */
    fun processLiveTelemetry(
        snapshot: TrainTelemetrySnapshot,
        journey: TrainJourney,
        currentEpochMillis: Long = System.currentTimeMillis(),
        weatherImpairmentIndex: Float = 0.0f,
        precedingHeadwayMinutes: Int = 15
    ): EtaContinuousUpdateResult? {
        // Step 1: Validate
        val validation = validateTelemetry(snapshot, currentEpochMillis)
        if (validation is TelemetryValidationResult.Rejected) {
            return null
        }
        previousTelemetryMap[snapshot.trainNumber] = snapshot

        val safeCurrentIdx = snapshot.currentStopIndex.coerceIn(0, journey.stops.lastIndex)
        val safeTargetIdx = (safeCurrentIdx + 1).coerceAtMost(journey.stops.lastIndex)
        val targetStop = journey.stops[safeTargetIdx]

        // Step 2: Generate Features
        val featureVector = FeatureEngineeringEngine.extractFeatures(
            journey = journey,
            currentStopIndex = safeCurrentIdx,
            targetStopIndex = safeTargetIdx,
            telemetry = snapshot,
            precedingHeadwayMinutes = precedingHeadwayMinutes,
            weatherImpairmentIndex = weatherImpairmentIndex
        )

        // Step 3: Predict ETA
        val scheduledArrivalEpoch = featureVector.scheduledArrivalEpochMillis
        val currentPrediction = activePredictor.predictStationArrival(
            features = featureVector,
            scheduledArrivalEpochMillis = scheduledArrivalEpoch
        )

        // Step 4: Calculate Confidence
        val confidence = ConfidenceCalculationEngine.computeConfidence(
            prediction = currentPrediction,
            telemetryTimestampEpochMillis = snapshot.timestampEpochMillis,
            currentEpochMillis = currentEpochMillis
        )

        // Step 5: Compare with Previous Prediction
        val cacheKey = "${snapshot.trainNumber}:${targetStop.station.code}"
        val previousPrediction = previousPredictions[cacheKey]
        val comparison = if (previousPrediction != null) {
            val delta = currentPrediction.predictedDelayMinutes - previousPrediction.predictedDelayMinutes
            val explanation = synthesizeDeltaExplanation(delta, featureVector, previousPrediction, currentPrediction)
            PredictionComparison(
                trainNumber = snapshot.trainNumber,
                stationCode = targetStop.station.code,
                previousDelayMinutes = previousPrediction.predictedDelayMinutes,
                currentDelayMinutes = currentPrediction.predictedDelayMinutes,
                deltaMinutes = delta,
                changeExplanation = explanation,
                isSignificantChange = abs(delta) >= 2
            )
        } else {
            PredictionComparison(
                trainNumber = snapshot.trainNumber,
                stationCode = targetStop.station.code,
                previousDelayMinutes = currentPrediction.predictedDelayMinutes,
                currentDelayMinutes = currentPrediction.predictedDelayMinutes,
                deltaMinutes = 0,
                changeExplanation = "Initial baseline ETA established based on real-time track telemetry.",
                isSignificantChange = false
            )
        }
        previousPredictions[cacheKey] = currentPrediction

        // Step 6: Synthesize General Explainability & Causes
        val explanations = ExplainabilityEngine.synthesizeExplanation(currentPrediction, featureVector)

        // Step 7: Evaluate Operational Alerts
        val alerts = mutableListOf<RailAlert>()
        AlertEngine.evaluateGhatAlert(
            trainNumber = snapshot.trainNumber,
            currentStationCode = journey.stops[safeCurrentIdx].station.code,
            currentEpochMillis = currentEpochMillis
        )?.let { alerts.add(it) }

        AlertEngine.evaluateWakeupAlert(
            trainNumber = snapshot.trainNumber,
            destinationStationCode = journey.destination.code,
            pointEtaEpochMillis = currentPrediction.pointEtaEpochMillis,
            currentEpochMillis = currentEpochMillis
        )?.let { alerts.add(it) }

        // Headway alert
        if (featureVector.precedingHeadwayMinutes < 10) {
            alerts.add(
                RailAlert(
                    alertId = "ALERT-HDW-${snapshot.trainNumber}",
                    trainNumber = snapshot.trainNumber,
                    type = AlertType.DELAY_SPIKE,
                    severity = AlertSeverity.WARNING,
                    title = "Close Headway Following Traffic",
                    message = "Following preceding train at ${featureVector.precedingHeadwayMinutes} min spacing; cautionary double-yellow signaling active.",
                    timestampEpochMillis = currentEpochMillis,
                    triggerStopCode = targetStop.station.code
                )
            )
        }

        return EtaContinuousUpdateResult(
            validatedSnapshot = snapshot,
            featureVector = featureVector,
            currentPrediction = currentPrediction,
            confidence = confidence,
            comparison = comparison,
            explanations = explanations,
            alerts = alerts,
            processedAtEpochMillis = currentEpochMillis
        )
    }

    private fun synthesizeDeltaExplanation(
        deltaMinutes: Int,
        features: TrainJourneyFeatureVector,
        prev: PredictionResult,
        curr: PredictionResult
    ): String {
        return when {
            deltaMinutes > 0 -> {
                val reasons = mutableListOf<String>()
                if (features.precedingHeadwayMinutes < 12) reasons.add("close headway behind preceding train (${features.precedingHeadwayMinutes}m)")
                if (features.speedDeltaVsMps < -15.0) reasons.add("train running below MPS (${features.currentSpeedKmH.toInt()} km/h vs ${features.sectionMpsKmH.toInt()} km/h limit)")
                if (features.isSingleTrackAhead) reasons.add("downstream single-track block clearance wait")
                if (features.weatherImpairmentIndex > 0.2f) reasons.add("weather/visibility speed restriction")
                if (reasons.isEmpty()) reasons.add("operational section deceleration")
                "ETA delayed by +${deltaMinutes} min due to ${reasons.joinToString(" and ")}."
            }
            deltaMinutes < 0 -> {
                val recoveryMins = abs(deltaMinutes)
                val reasons = mutableListOf<String>()
                if (features.scheduledSlackMinutes > 0) reasons.add("absorbing ${features.scheduledSlackMinutes} min scheduled engineering slack")
                if (features.currentSpeedKmH >= features.sectionMpsKmH * 0.9) reasons.add("sustained maximum permissible speed (${features.currentSpeedKmH.toInt()} km/h)")
                if (reasons.isEmpty()) reasons.add("unobstructed track block clearance")
                "ETA improved by -${recoveryMins} min due to ${reasons.joinToString(" and ")}."
            }
            else -> "ETA stable: Train maintaining scheduled corridor progression without variance."
        }
    }

    fun clearCache() {
        previousPredictions.clear()
        previousTelemetryMap.clear()
    }
}

sealed class TelemetryValidationResult {
    data class Accepted(val snapshot: TrainTelemetrySnapshot) : TelemetryValidationResult()
    data class Rejected(val reason: String, val rejectedSnapshot: TrainTelemetrySnapshot) : TelemetryValidationResult()
}
