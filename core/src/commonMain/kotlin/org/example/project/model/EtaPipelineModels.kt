package org.example.project.model

/**
 * Raw or validated snapshot of live train telemetry.
 */
data class TrainTelemetrySnapshot(
    val trainNumber: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmH: Double,
    val headingDegrees: Double = 0.0,
    val currentStopIndex: Int,
    val currentDelayMinutes: Int,
    val timestampEpochMillis: Long
)

/**
 * Normalized feature vector ingested by ML and heuristic ETA predictors.
 * 
 * Encompasses all 15 operational feature domains:
 * 1. Scheduled arrival time
 * 2. Scheduled departure time
 * 3. Current train position (lat, lon)
 * 4. Current speed
 * 5. Current delay
 * 6. Previous station delay
 * 7. Route information
 * 8. Station sequence
 * 9. Historical travel time
 * 10. Historical delay patterns
 * 11. Time of day
 * 12. Day of week
 * 13. Downstream operational conditions
 * 14. Accumulated delay
 * 15. Segment travel time
 */
data class TrainJourneyFeatureVector(
    val trainNumber: String,
    val trainType: TrainType,
    val originCode: String,
    val currentStationCode: String,
    val targetStationCode: String,
    val distanceRemainingKm: Double,
    val currentSpeedKmH: Double,
    val sectionMpsKmH: Double,
    val speedDeltaVsMps: Double,
    val currentDelayMinutes: Int,
    val scheduledSlackMinutes: Int,
    val gradientFactor: Double, // 1.0 = flat, 1.37 = Thal Ghat steep incline
    val weatherImpairmentIndex: Float = 0.0f, // 0.0 = clear, 1.0 = heavy fog/monsoon
    val precedingHeadwayMinutes: Int = 15,
    val isSingleTrackAhead: Boolean = false,
    val isJunctionAhead: Boolean = false,
    val junctionCongestionFactor: Double = 1.0,

    // --- Explicit 15 Operational Features ---
    val scheduledArrivalEpochMillis: Long = 0L,
    val scheduledArrivalHHmm: String = "",
    val scheduledDepartureEpochMillis: Long = 0L,
    val scheduledDepartureHHmm: String = "",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val previousStationDelayMinutes: Int = 0,
    val routeId: String = "CR-CSMT-MMR-01",
    val currentStationSequence: Int = 0,
    val targetStationSequence: Int = 1,
    val totalStationsInRoute: Int = 8,
    val historicalTravelTimeMinutes: Double = 32.0,
    val historicalMeanDelayMinutes: Double = 3.5,
    val historicalDelayStdDev: Double = 2.0,
    val timeOfDayMinutes: Int = 540, // 09:00 AM
    val timeOfDayBucket: String = "MORNING_PEAK",
    val dayOfWeek: Int = 1, // 1 = Monday
    val dayOfWeekName: String = "Monday",
    val downstreamCongestionFactor: Double = 1.0,
    val accumulatedDelayMinutes: Int = 0,
    val scheduledSegmentTravelTimeMinutes: Int = 30,
    val kinematicSegmentTravelTimeMinutes: Double = 28.0
)

/**
 * Supported ML / Prediction Model architectures.
 */
enum class ModelArchitecture(val displayName: String) {
    HEURISTIC_BASELINE("Kinematic & Engineering Slack Recovery"),
    LINEAR_REGRESSION("Multi-variable Regularized Linear Baseline"),
    QUANTILE_GRADIENT_BOOST("LightGBM / CatBoost Quantile Regressor"),
    ONNX_TRANSFORMER("ONNX Runtime Deep Sequence Model"),
    SPATIO_TEMPORAL_GNN("PyTorch Spatio-Temporal Graph Neural Network")
}

/**
 * Standard prediction result produced by any IEtaPredictor.
 */
data class PredictionResult(
    val pointEtaEpochMillis: Long,
    val predictedEtaHHmm: String,
    val predictedDelayMinutes: Int,
    val quantileLowerMinutes: Int, // e.g. p10 (best case)
    val quantileUpperMinutes: Int, // e.g. p90 (worst case)
    val featureImportances: Map<String, Float> = emptyMap(),
    val modelArchitecture: ModelArchitecture,
    val modelVersion: String,
    val predictedArrivalEpochMillis: Long = pointEtaEpochMillis,
    val confidenceScore: Double = 0.85,
    val predictionTimestampEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Comparison between consecutive ETA predictions for explainability.
 */
data class PredictionComparison(
    val trainNumber: String,
    val stationCode: String,
    val previousDelayMinutes: Int,
    val currentDelayMinutes: Int,
    val deltaMinutes: Int, // current - previous
    val changeExplanation: String,
    val isSignificantChange: Boolean
)

/**
 * Ground truth predicted vs actual arrival record for model evaluation.
 */
data class PredictedVsActualArrival(
    val trainNumber: String,
    val stationCode: String,
    val scheduledArrivalEpochMillis: Long,
    val predictedArrivalEpochMillis: Long,
    val actualArrivalEpochMillis: Long,
    val errorMinutes: Double,
    val absoluteErrorMinutes: Double,
    val squaredErrorMinutes: Double,
    val modelVersion: String
)

/**
 * Error distribution buckets for railway dispatch evaluation.
 */
data class ErrorDistributionHistogram(
    val under1MinCount: Int,
    val under1MinPercent: Double,
    val oneTo3MinsCount: Int,
    val oneTo3MinsPercent: Double,
    val threeTo5MinsCount: Int,
    val threeTo5MinsPercent: Double,
    val over5MinsCount: Int,
    val over5MinsPercent: Double
)

/**
 * Comprehensive ML model performance and evaluation report.
 */
data class ModelEvaluationReport(
    val modelVersion: String,
    val modelArchitecture: ModelArchitecture,
    val totalPredictions: Int,
    val meanAbsoluteErrorMinutes: Double, // MAE
    val rootMeanSquaredErrorMinutes: Double, // RMSE
    val medianErrorMinutes: Double,
    val p90ErrorMinutes: Double,
    val errorDistribution: ErrorDistributionHistogram,
    val predictedVsActualArrivals: List<PredictedVsActualArrival>,
    val evaluatedAtEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Result of the continuous live ETA update pipeline.
 */
data class EtaContinuousUpdateResult(
    val validatedSnapshot: TrainTelemetrySnapshot,
    val featureVector: TrainJourneyFeatureVector,
    val currentPrediction: PredictionResult,
    val confidence: ConfidenceScore,
    val comparison: PredictionComparison?,
    val explanations: List<String>,
    val alerts: List<RailAlert>,
    val processedAtEpochMillis: Long
)

/**
 * Distinguishes telemetry-verified past/present delays from forecasted downstream delays.
 */
enum class DelayObservationType(val label: String, val badgeTitle: String) {
    OBSERVED_ACTUAL("Observed Actual Delay", "OBSERVED DELAY"),
    PREDICTED_DOWNSTREAM("Predicted Downstream Delay", "PREDICTED DOWNSTREAM DELAY")
}

/**
 * Severity ranking for route delay propagation.
 */
enum class DelaySeverity(val label: String, val colorHex: Long) {
    LOW("Low Impact (<10m)", 0xFF10B981),          // Green
    MODERATE("Moderate Cascade (10-19m)", 0xFF06B6D4), // Cyan
    HIGH("High Congestion (20-29m)", 0xFFF59E0B),    // Amber
    CRITICAL("Critical Bottleneck (>=30m)", 0xFFEF4444) // Red
}

/**
 * Step-by-step station delay progression entity along a train route.
 */
data class StationDelayProgression(
    val stationCode: String,
    val stationName: String,
    val sequence: Int,
    val observationType: DelayObservationType,
    val scheduledArrival: String,
    val scheduledDeparture: String,
    val predictedEta: String,
    val delayMinutes: Int,
    val deltaFromPreviousMinutes: Int, // e.g. +3, +4, -2
    val slackRecoveryMinutes: Int,
    val congestionPenaltyMinutes: Int,
    val dwellInflationMinutes: Int,
    val cascadeRisk: CascadeRisk,
    val explanation: String
)

/**
 * Comprehensive delay propagation analysis along the entire downstream corridor.
 */
data class DelayPropagationReport(
    val trainNumber: String,
    val currentStationCode: String,
    val currentStationIndex: Int,
    val observedDelayMinutes: Int,
    val stationProgressions: List<StationDelayProgression>,
    val expectedAffectedStationsCount: Int,
    val severity: DelaySeverity,
    val confidencePercent: Int,
    val maxProjectedDelayMinutes: Int,
    val finalImpactDelayMinutes: Int,
    val totalSlackRecoveryMinutes: Int,
    val progressionSummary: String,
    val generatedAtEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Models occupancy of physical track blocks for network-aware routing.
 */
data class TrackBlockOccupancy(
    val blockId: String,
    val blockName: String,
    val isSingleTrack: Boolean,
    val currentTrainNumber: String? = null,
    val occupiedSinceEpochMillis: Long? = null,
    val lengthKm: Double = 10.0
)

/**
 * Outcome of track contention or crossing arbitration between two trains.
 */
data class PrecedenceResolution(
    val higherPriorityTrainNumber: String,
    val lowerPriorityTrainNumber: String,
    val crossingStationCode: String,
    val lowerPriorityDelayPenaltyMinutes: Int,
    val reason: String
)

/**
 * Confidence ranking for predictions.
 */
enum class ConfidenceLevel(val label: String, val colorHex: Long) {
    HIGH("High Confidence", 0xFF00E676),      // Green
    MEDIUM("Moderate Confidence", 0xFF00E5FF), // Cyan
    LOW("Low Confidence", 0xFFFFB300),        // Amber
    VOLATILE("Volatile / High Uncertainty", 0xFFFF5252) // Red
}

/**
 * Computed confidence metrics.
 */
data class ConfidenceScore(
    val scorePercent: Int, // 0 .. 100
    val level: ConfidenceLevel,
    val spreadMinutes: Int, // (p90 - p10)
    val dataFreshnessSeconds: Long,
    val explanation: String
)

/**
 * Alert classifications.
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class AlertType {
    WAKEUP,
    CONNECTION_RISK,
    DELAY_SPIKE,
    PLATFORM_CONFLICT,
    GHAT_SECTION_ALERT
}

/**
 * Proactive alert entity dispatched to passengers and operations.
 */
data class RailAlert(
    val alertId: String,
    val trainNumber: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val timestampEpochMillis: Long,
    val triggerStopCode: String? = null
)
