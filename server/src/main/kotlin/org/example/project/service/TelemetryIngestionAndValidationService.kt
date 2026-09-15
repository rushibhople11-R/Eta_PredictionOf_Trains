package org.example.project.service

import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.engine.*
import org.example.project.model.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed class ValidationResult {
    data class Valid(val snapshot: TrainTelemetrySnapshot) : ValidationResult()
    data class Invalid(val reason: String, val rejectedSnapshot: TrainTelemetrySnapshot) : ValidationResult()
}

data class ProcessedTelemetryResult(
    val snapshot: TrainTelemetrySnapshot,
    val prediction: PredictionResult,
    val confidence: ConfidenceScore,
    val explanations: List<String>,
    val alerts: List<RailAlert>,
    val comparison: PredictionComparison? = null
)

/**
 * High-Throughput Telemetry Ingestion, Kinematic Validation & Engine Orchestration Service.
 */
class TelemetryIngestionAndValidationService(
    initialPredictor: IEtaPredictor = QuantileEtaPredictor()
) {
    // Thread-safe active predictor reference (supports live hot-swapping)
    private val activePredictor = AtomicReference<IEtaPredictor>(initialPredictor)

    // Continuous update orchestrator
    private val orchestrator = ContinuousEtaPipelineOrchestrator(initialPredictor)

    // In-memory cache of latest telemetry per train (analogous to Redis hot state)
    private val latestTelemetryCache = ConcurrentHashMap<String, TrainTelemetrySnapshot>()

    // In-memory dead-letter queue for rejected invalid telemetry
    private val deadLetterQueue = mutableListOf<ValidationResult.Invalid>()

    fun getActivePredictor(): IEtaPredictor = activePredictor.get()

    fun switchPredictor(newPredictor: IEtaPredictor): IEtaPredictor {
        activePredictor.set(newPredictor)
        orchestrator.activePredictor = newPredictor
        return newPredictor
    }

    /**
     * Validates kinematic physical limits and geospatial bounds of incoming GPS telemetry.
     */
    fun validate(snapshot: TrainTelemetrySnapshot, currentEpochMillis: Long): ValidationResult {
        // 1. Train number validity
        if (snapshot.trainNumber.isBlank()) {
            return ValidationResult.Invalid("Empty or blank train number", snapshot)
        }

        // 2. Speed sanity (Max train speed in India is 160 km/h for Vande Bharat / Gatimaan)
        if (snapshot.speedKmH < 0.0 || snapshot.speedKmH > 180.0) {
            return ValidationResult.Invalid(
                "Speed ${snapshot.speedKmH} km/h violates kinematic limits (0 - 180 km/h)",
                snapshot
            )
        }

        // 3. Indian Subcontinent Geospatial Boundary check
        // Latitude: ~6.0° N to 38.0° N, Longitude: ~68.0° E to 98.0° E
        val validLat = snapshot.latitude in 6.0..38.0
        val validLon = snapshot.longitude in 68.0..98.0
        if (!validLat || !validLon) {
            return ValidationResult.Invalid(
                "Coordinates (${snapshot.latitude}, ${snapshot.longitude}) fall outside Indian Railway territory",
                snapshot
            )
        }

        // 4. Timestamp Sanity: Reject timestamps from >5 mins in future or >24h in past
        val maxFutureMillis = currentEpochMillis + 300_000L
        val maxPastMillis = currentEpochMillis - (24 * 3600_000L)
        if (snapshot.timestampEpochMillis > maxFutureMillis) {
            return ValidationResult.Invalid("Telemetry timestamp is in the future", snapshot)
        }
        if (snapshot.timestampEpochMillis < maxPastMillis) {
            return ValidationResult.Invalid("Telemetry timestamp is stale (> 24 hours old)", snapshot)
        }

        // 5. Kinematic Teleportation Filter (if previous point exists for this train)
        val previous = latestTelemetryCache[snapshot.trainNumber]
        if (previous != null && snapshot.timestampEpochMillis > previous.timestampEpochMillis) {
            val deltaSeconds = (snapshot.timestampEpochMillis - previous.timestampEpochMillis) / 1000.0
            if (deltaSeconds > 0.0) {
                // Approximate distance via Euclidean degree approximation (1 deg ~ 111 km)
                val dLat = (snapshot.latitude - previous.latitude) * 111.0
                val dLon = (snapshot.longitude - previous.longitude) * 111.0
                val distKm = kotlin.math.sqrt(dLat * dLat + dLon * dLon)
                val impliedSpeedKmh = (distKm / deltaSeconds) * 3600.0
                if (impliedSpeedKmh > 220.0) {
                    return ValidationResult.Invalid(
                        "GPS jump detected: implied speed ${impliedSpeedKmh.toInt()} km/h exceeds 220 km/h threshold",
                        snapshot
                    )
                }
            }
        }

        return ValidationResult.Valid(snapshot)
    }

    /**
     * Ingests, validates, executes feature engineering, calls active predictor,
     * runs confidence & explainability, and returns full processed result.
     */
    fun ingestAndProcess(
        snapshot: TrainTelemetrySnapshot,
        currentEpochMillis: Long
    ): ProcessedTelemetryResult? {
        val validation = validate(snapshot, currentEpochMillis)
        if (validation is ValidationResult.Invalid) {
            synchronized(deadLetterQueue) {
                deadLetterQueue.add(validation)
                if (deadLetterQueue.size > 200) deadLetterQueue.removeAt(0)
            }
            return null
        }

        // Update hot cache
        latestTelemetryCache[snapshot.trainNumber] = snapshot

        // Lookup journey route
        val journey = findJourney(snapshot.trainNumber) ?: createDefaultJourney(snapshot.trainNumber)

        val updateResult = orchestrator.processLiveTelemetry(
            snapshot = snapshot,
            journey = journey,
            currentEpochMillis = currentEpochMillis
        ) ?: return null

        return ProcessedTelemetryResult(
            snapshot = updateResult.validatedSnapshot,
            prediction = updateResult.currentPrediction,
            confidence = updateResult.confidence,
            explanations = updateResult.explanations,
            alerts = updateResult.alerts,
            comparison = updateResult.comparison
        )
    }

    fun getLatestTelemetry(trainNumber: String): TrainTelemetrySnapshot? =
        latestTelemetryCache[trainNumber]

    fun getAllActiveTrainNumbers(): Set<String> = latestTelemetryCache.keys.toSet()

    fun getDeadLetterQueue(): List<ValidationResult.Invalid> =
        synchronized(deadLetterQueue) { deadLetterQueue.toList() }

    private fun findJourney(trainNumber: String): TrainJourney? {
        if (trainNumber == NashikStationData.trainVandeBharat22223.trainNumber) {
            return NashikStationData.trainVandeBharat22223
        }
        return SampleRailwayData.allSampleTrains.find { it.trainNumber == trainNumber }
    }

    private fun createDefaultJourney(trainNumber: String): TrainJourney {
        return NashikStationData.trainVandeBharat22223.copy(trainNumber = trainNumber)
    }
}
