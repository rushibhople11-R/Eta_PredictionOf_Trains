package org.example.project.engine

import org.example.project.model.PredictionResult
import org.example.project.model.TrainJourneyFeatureVector
import kotlin.math.roundToInt

/**
 * Explainability & Feature Attribution Engine.
 *
 * Translates raw numerical feature weights and prediction coefficients
 * into transparent, plain-English operational delay narratives.
 */
object ExplainabilityEngine {

    /**
     * Synthesizes human-readable explanations from model feature weights and journey state.
     */
    fun synthesizeExplanation(
        prediction: PredictionResult,
        features: TrainJourneyFeatureVector
    ): List<String> {
        val explanations = mutableListOf<String>()
        val weights = prediction.featureImportances

        // 1. Thal Ghat & Gradient Analysis
        val gradientWeight = weights["gradient_penalty"] ?: weights["gradient_resistance"] ?: 0f
        if (gradientWeight > 2f || features.gradientFactor > 1.2) {
            explanations.add("Thal Ghat 1:37 gradient operations: banking engine detachment & mandatory brake safety test (+${gradientWeight.roundToInt().coerceAtLeast(6)}m)")
        }

        // 2. Speed Deficit vs MPS
        val speedWeight = weights["speed_deficit"] ?: 0f
        if (speedWeight > 2f || features.speedDeltaVsMps < -25.0) {
            val deficit = (-features.speedDeltaVsMps).roundToInt()
            explanations.add("Permanent/temporary track speed restriction (running ${deficit} km/h below section MPS)")
        }

        // 3. Headway / Preceding Train Compression
        val headwayWeight = weights["headway_compression"] ?: 0f
        if (headwayWeight > 2f || features.precedingHeadwayMinutes < 8) {
            explanations.add("Close headway: yellow/double-yellow signal braking behind preceding service (+${headwayWeight.roundToInt().coerceAtLeast(3)}m)")
        }

        // 4. Single Track Bottleneck
        val singleTrackWeight = weights["single_track_penalty"] ?: 0f
        if (singleTrackWeight > 2f || features.isSingleTrackAhead) {
            explanations.add("Single-track section: scheduled loop line crossing halt")
        }

        // 5. Junction Clearance Queue
        val junctionWeight = weights["junction_congestion"] ?: 0f
        if (junctionWeight > 2f) {
            explanations.add("Congested junction approach signal queue (+${junctionWeight.roundToInt()}m)")
        }

        // 6. Slack Absorption / Recovery
        val slackWeight = weights["slack_recovery"] ?: weights["slack_absorption"] ?: 0f
        if (slackWeight < -0.5f) {
            val rec = (-slackWeight).roundToInt()
            explanations.add("Making up time: recovering ${rec}m via built-in section slack buffer")
        }

        // 7. Fallback if on schedule
        if (explanations.isEmpty()) {
            if (prediction.predictedDelayMinutes == 0) {
                explanations.add("Punctual run: section timings within scheduled tolerances")
            } else {
                explanations.add("Maintaining stable delay offset across corridor")
            }
        }

        return explanations
    }

    /**
     * Produces a concise single-line marquee summary.
     */
    fun summarizeForMarquee(
        prediction: PredictionResult,
        features: TrainJourneyFeatureVector
    ): String {
        val points = synthesizeExplanation(prediction, features)
        return points.joinToString(" • ")
    }
}
