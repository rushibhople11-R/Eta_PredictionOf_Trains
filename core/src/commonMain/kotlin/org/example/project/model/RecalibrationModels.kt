package org.example.project.model

/**
 * Ingested live operational telemetry update from track circuits, axle counters, or GPS.
 */
data class LiveOperationalUpdate(
    val updateId: String,
    val trainNumber: String,
    val stationCode: String,
    val segmentId: String? = null,
    val speedKmph: Double,
    val observedDelayMinutes: Int,
    val dwellObservedMinutes: Int? = null,
    val timestampEpoch: Long = System.currentTimeMillis()
)

/**
 * Sensor and telemetry validation state.
 */
data class ValidationResult(
    val isValid: Boolean,
    val validationIssues: List<String> = emptyList()
)

/**
 * Historical recalibration state retained for comparing consecutive updates.
 */
data class RecalibrationState(
    val trainNumber: String,
    val lastStationCode: String,
    val lastPredictedEtaHHmm: String,
    val lastObservedDelayMinutes: Int,
    val lastPredictedDelayMinutes: Int,
    val lastConfidenceScore: Int,
    val lastUpdateEpoch: Long
)

/**
 * Detailed step in the 10-step continuous recalibration pipeline execution audit.
 */
data class RecalibrationStepAudit(
    val stepNumber: Int,
    val stepName: String,
    val status: String,
    val detail: String,
    val durationMs: Long
)

/**
 * Comprehensive report generated when the continuous recalibration engine executes.
 */
data class ContinuousRecalibrationReport(
    val trainNumber: String,
    val currentStationCode: String,
    val nextStationCode: String,
    val currentEtaHHmm: String,
    val lastUpdatedHHmm: String,
    val previousEtaHHmm: String,
    val changeMinutes: Int, // Signed: +3 min, -2 min, 0 min
    val changeReason: String,
    val confidencePercent: Int,
    val previousConfidencePercent: Int,
    val observedDelayMinutes: Int,
    val predictedDelayMinutes: Int,
    val triggeredAlerts: List<RailAlert> = emptyList(),
    val stepAuditLogs: List<RecalibrationStepAudit> = emptyList(),
    val timestampEpoch: Long = System.currentTimeMillis()
)
