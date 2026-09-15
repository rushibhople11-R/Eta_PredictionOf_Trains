package org.example.project.engine

import org.example.project.model.ConfidenceLevel
import org.example.project.model.ConfidenceScore
import org.example.project.model.PredictionResult
import kotlin.math.max

/**
 * Quantile Spread & Telemetry Freshness Confidence Engine.
 *
 * Converts prediction uncertainty into an intuitive percentage and categorization.
 */
object ConfidenceCalculationEngine {

    /**
     * Calculates confidence metrics based on prediction quantile intervals
     * and current data freshness.
     */
    fun computeConfidence(
        prediction: PredictionResult,
        telemetryTimestampEpochMillis: Long,
        currentEpochMillis: Long
    ): ConfidenceScore {
        val spread = max(0, prediction.quantileUpperMinutes - prediction.quantileLowerMinutes)
        val dataAgeSeconds = max(0L, (currentEpochMillis - telemetryTimestampEpochMillis) / 1000)

        // Baseline score from quantile tightness
        // Narrow spread (<= 4 mins) -> high score; wide spread (>= 25 mins) -> low score
        var score = (100 - (spread * 3.5)).toInt().coerceIn(15, 98)

        // Penalize stale telemetry (if GPS update > 120s old)
        if (dataAgeSeconds > 120) {
            val stalenessPenalty = ((dataAgeSeconds - 120) / 30).toInt().coerceAtMost(35)
            score = (score - stalenessPenalty).coerceAtLeast(10)
        }

        val level = when {
            score >= 85 -> ConfidenceLevel.HIGH
            score >= 65 -> ConfidenceLevel.MEDIUM
            score >= 45 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.VOLATILE
        }

        val explanation = when (level) {
            ConfidenceLevel.HIGH -> "High telemetry freshness with narrow ${spread}m variance window"
            ConfidenceLevel.MEDIUM -> "Stable corridor run; projected within ±${spread / 2}m interval"
            ConfidenceLevel.LOW -> "Moderate uncertainty due to bottleneck section friction"
            ConfidenceLevel.VOLATILE -> if (dataAgeSeconds > 180) {
                "Telemetry stale by ${dataAgeSeconds}s; wide arrival variance (${spread}m)"
            } else {
                "High variance (${spread}m) due to complex single-track/gradient factors"
            }
        }

        return ConfidenceScore(
            scorePercent = score,
            level = level,
            spreadMinutes = spread,
            dataFreshnessSeconds = dataAgeSeconds,
            explanation = explanation
        )
    }
}
