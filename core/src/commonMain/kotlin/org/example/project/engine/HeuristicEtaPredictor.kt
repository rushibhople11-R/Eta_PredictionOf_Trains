package org.example.project.engine

import org.example.project.model.IndianStandardTime
import org.example.project.model.ModelArchitecture
import org.example.project.model.PredictionResult
import org.example.project.model.TrainJourneyFeatureVector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic Kinematic & Slack Recovery Predictor.
 *
 * Serves as the high-availability fallback and baseline predictor.
 */
class HeuristicEtaPredictor(
    override val modelVersion: String = "v1.2.0-deterministic"
) : IEtaPredictor {

    override val modelArchitecture: ModelArchitecture = ModelArchitecture.HEURISTIC_BASELINE

    override fun predictStationArrival(
        features: TrainJourneyFeatureVector,
        scheduledArrivalEpochMillis: Long
    ): PredictionResult {
        var runningDelay = max(0, features.currentDelayMinutes)

        // 1. Recover time via built-in engineering slack
        val availableSlack = features.scheduledSlackMinutes
        val priorityFactor = features.trainType.priorityScore / 10.0
        val recovered = if (runningDelay > 0 && availableSlack > 0) {
            val potentialRecovery = (availableSlack * priorityFactor).roundToInt()
            min(runningDelay, max(1, potentialRecovery))
        } else {
            0
        }
        runningDelay = max(0, runningDelay - recovered)

        // 2. Speed deficit penalty
        var speedPenalty = 0
        if (features.currentSpeedKmH > 0 && features.speedDeltaVsMps < -20.0) {
            // Running significantly slower than MPS (e.g. speed restriction)
            val deficitRatio = (-features.speedDeltaVsMps / features.sectionMpsKmH)
            speedPenalty = ((features.distanceRemainingKm / 100.0) * deficitRatio * 8.0).roundToInt()
            runningDelay += speedPenalty
        }

        // 3. Gradient resistance & banking engine detachment penalty (e.g. Thal Ghat 1:37)
        var gradientPenalty = 0
        if (features.gradientFactor > 1.2) {
            gradientPenalty = if (features.trainType.priorityScore >= 9) 6 else 14
            runningDelay += gradientPenalty
        }

        // 4. Single-track bottleneck penalty
        var singleTrackPenalty = 0
        if (features.isSingleTrackAhead && runningDelay > 10) {
            singleTrackPenalty = if (features.trainType.priorityScore >= 8) 5 else 12
            runningDelay += singleTrackPenalty
        }

        // 5. Junction signal clearance queue penalty
        var junctionPenalty = 0
        if (features.isJunctionAhead && features.junctionCongestionFactor > 1.1 && runningDelay > 12) {
            junctionPenalty = ((features.junctionCongestionFactor - 1.0) * 10).roundToInt()
            runningDelay += junctionPenalty
        }

        // 6. Quantile estimates (p10 best case, p90 worst case)
        val uncertainty = max(2, (features.distanceRemainingKm * 0.02).roundToInt() + if (features.isSingleTrackAhead) 3 else 1)
        val p10Delay = max(0, runningDelay - uncertainty - (recovered / 2))
        val p90Delay = runningDelay + uncertainty + (gradientPenalty / 2)

        // Final arrival timestamp in epoch millis
        val finalArrivalEpochMillis = scheduledArrivalEpochMillis + (runningDelay * 60_000L)
        val istTime = IndianStandardTime.fromEpochMillis(finalArrivalEpochMillis)

        val featureImportances = mapOf(
            "slack_recovery" to -recovered.toFloat(),
            "speed_deficit" to speedPenalty.toFloat(),
            "gradient_penalty" to gradientPenalty.toFloat(),
            "single_track_penalty" to singleTrackPenalty.toFloat(),
            "junction_congestion" to junctionPenalty.toFloat()
        )

        return PredictionResult(
            pointEtaEpochMillis = finalArrivalEpochMillis,
            predictedEtaHHmm = istTime.time24Short,
            predictedDelayMinutes = runningDelay,
            quantileLowerMinutes = p10Delay,
            quantileUpperMinutes = p90Delay,
            featureImportances = featureImportances,
            modelArchitecture = modelArchitecture,
            modelVersion = modelVersion
        )
    }
}
