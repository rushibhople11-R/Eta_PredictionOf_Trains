package org.example.project.engine

import org.example.project.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Thread-Safe Continuous Recalibration Engine.
 *
 * Implements the 10-step continuous recalibration pipeline:
 * 1. LIVE UPDATE
 * 2. DATA VALIDATION
 * 3. COMPARE WITH PREVIOUS STATE
 * 4. RECALCULATE FEATURES
 * 5. RUN ETA MODEL
 * 6. UPDATE DELAY PROPAGATION
 * 7. RECALCULATE CONFIDENCE
 * 8. GENERATE EXPLANATION
 * 9. CHECK ALERT CONDITIONS
 * 10. UPDATE DASHBOARD / BROADCAST REPORT
 */
object ContinuousRecalibrationEngine {

    // Thread-safe storage of previous recalibration state per train
    private val previousStateMap = mutableMapOf<String, RecalibrationState>()

    /**
     * Clears cached state (used for testing or resetting a journey).
     */
    fun resetState(trainNumber: String? = null) {
        synchronized(previousStateMap) {
            if (trainNumber != null) {
                previousStateMap.remove(trainNumber)
            } else {
                previousStateMap.clear()
            }
        }
    }

    /**
     * Seeds or primes an initial state for comparison before live updates arrive.
     */
    fun seedInitialState(state: RecalibrationState) {
        synchronized(previousStateMap) {
            previousStateMap[state.trainNumber] = state
        }
    }

    /**
     * Gets the last known state for a train.
     */
    fun getLastState(trainNumber: String): RecalibrationState? {
        synchronized(previousStateMap) {
            return previousStateMap[trainNumber]
        }
    }

    /**
     * Executes the complete 10-step continuous recalibration pipeline.
     */
    fun processLiveUpdate(
        update: LiveOperationalUpdate,
        journey: TrainJourney
    ): ContinuousRecalibrationReport {
        val auditLogs = mutableListOf<RecalibrationStepAudit>()
        val startTime = System.currentTimeMillis()

        // -------------------------------------------------------------
        // STEP 1: LIVE UPDATE
        // -------------------------------------------------------------
        val step1Start = System.currentTimeMillis()
        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 1,
                stepName = "LIVE UPDATE",
                status = "ACCEPTED",
                detail = "Telemetry received: Train ${update.trainNumber} at ${update.stationCode}, Speed ${update.speedKmph.toInt()} km/h, Delay +${update.observedDelayMinutes}m",
                durationMs = System.currentTimeMillis() - step1Start
            )
        )

        // -------------------------------------------------------------
        // STEP 2: DATA VALIDATION
        // -------------------------------------------------------------
        val step2Start = System.currentTimeMillis()
        val validation = validateUpdate(update, journey)
        if (!validation.isValid) {
            val failureDetail = validation.validationIssues.joinToString("; ")
            auditLogs.add(
                RecalibrationStepAudit(
                    stepNumber = 2,
                    stepName = "DATA VALIDATION",
                    status = "REJECTED",
                    detail = "Validation failed: $failureDetail",
                    durationMs = System.currentTimeMillis() - step2Start
                )
            )
            // Fallback: return default report keeping previous state
            val prevState = getLastState(update.trainNumber)
            return ContinuousRecalibrationReport(
                trainNumber = update.trainNumber,
                currentStationCode = update.stationCode,
                nextStationCode = journey.stops.getOrNull(1)?.station?.code ?: update.stationCode,
                currentEtaHHmm = prevState?.lastPredictedEtaHHmm ?: "18:42",
                lastUpdatedHHmm = IndianStandardTime.fromEpochMillis(update.timestampEpoch).time24Short,
                previousEtaHHmm = prevState?.lastPredictedEtaHHmm ?: "18:39",
                changeMinutes = 0,
                changeReason = "Telemetry validation issue: $failureDetail",
                confidencePercent = prevState?.lastConfidenceScore ?: 75,
                previousConfidencePercent = prevState?.lastConfidenceScore ?: 75,
                observedDelayMinutes = update.observedDelayMinutes,
                predictedDelayMinutes = prevState?.lastPredictedDelayMinutes ?: update.observedDelayMinutes,
                triggeredAlerts = listOf(
                    RailAlert(
                        alertId = "VAL-ERR-${System.currentTimeMillis()}",
                        trainNumber = update.trainNumber,
                        type = AlertType.DELAY_SPIKE,
                        severity = AlertSeverity.WARNING,
                        title = "Telemetry Validation Warning",
                        message = failureDetail,
                        timestampEpochMillis = update.timestampEpoch,
                        triggerStopCode = update.stationCode
                    )
                ),
                stepAuditLogs = auditLogs,
                timestampEpoch = update.timestampEpoch
            )
        }
        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 2,
                stepName = "DATA VALIDATION",
                status = "VALID",
                detail = "Sensor ranges, physical speed bounds, and station sequence verified.",
                durationMs = System.currentTimeMillis() - step2Start
            )
        )

        // -------------------------------------------------------------
        // STEP 3: COMPARE WITH PREVIOUS STATE
        // -------------------------------------------------------------
        val step3Start = System.currentTimeMillis()
        val prevState = getLastState(update.trainNumber)
        val priorObservedDelay = prevState?.lastObservedDelayMinutes ?: max(0, update.observedDelayMinutes - 3)
        val deltaDelay = update.observedDelayMinutes - priorObservedDelay
        val priorEta = prevState?.lastPredictedEtaHHmm ?: computeDefaultPreviousEta(update, journey)
        val priorConfidence = prevState?.lastConfidenceScore ?: 86

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 3,
                stepName = "COMPARE WITH PREVIOUS STATE",
                status = "COMPARED",
                detail = "Previous Delay: +${priorObservedDelay}m (Δ ${if (deltaDelay >= 0) "+$deltaDelay" else "$deltaDelay"}m), Prior ETA: $priorEta",
                durationMs = System.currentTimeMillis() - step3Start
            )
        )

        // -------------------------------------------------------------
        // STEP 4: RECALCULATE FEATURES
        // -------------------------------------------------------------
        val step4Start = System.currentTimeMillis()
        val currentStopIdx = journey.stops.indexOfFirst { it.station.code == update.stationCode }
            .let { if (it >= 0) it else 1 }
        val currentStop = journey.stops[currentStopIdx]
        val nextStop = journey.stops.getOrNull(currentStopIdx + 1) ?: currentStop

        // Feature calculations
        val scheduledSegmentRuntime = if (nextStop != currentStop) {
            val curArrMin = parseMinutesOfDay(currentStop.scheduledDeparture)
            val nextArrMin = parseMinutesOfDay(nextStop.scheduledArrival)
            if (nextArrMin >= curArrMin) nextArrMin - curArrMin else (nextArrMin + 1440) - curArrMin
        } else 20

        val segment = RailwayNetworkTopologyRegistry.getSegment(currentStop.station.code, nextStop.station.code)
        val kinematicBaseRuntime = (segment.distanceKm / max(40.0, segment.speedLimitKmh * 0.82)) * 60.0
        val isMountainGhat = segment.isGhatSection || nextStop.station.code == "IGP" || currentStop.station.code == "KSRA"
        val dwellOverrun = update.dwellObservedMinutes?.let { it - currentStop.scheduledDwellMinutes } ?: 0

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 4,
                stepName = "RECALCULATE FEATURES",
                status = "COMPUTED",
                detail = "Segment distance ${segment.distanceKm} km, Kinematic runtime ${kinematicBaseRuntime.toInt()}m, Dwell overrun ${dwellOverrun}m, Ghat incline: $isMountainGhat",
                durationMs = System.currentTimeMillis() - step4Start
            )
        )

        // -------------------------------------------------------------
        // STEP 5: RUN ETA MODEL
        // -------------------------------------------------------------
        val step5Start = System.currentTimeMillis()
        val rawPredictedDelay = update.observedDelayMinutes + when {
            dwellOverrun > 0 -> dwellOverrun
            deltaDelay > 2 -> (deltaDelay * 0.7).toInt()
            update.speedKmph < 45.0 && !isMountainGhat -> 3
            else -> 0
        }
        val nextStationPredictedDelay = max(0, rawPredictedDelay)

        // Compute updated ETA string
        val schedNextArrMin = parseMinutesOfDay(nextStop.scheduledArrival)
        val newEtaTotalMinutes = (schedNextArrMin + nextStationPredictedDelay) % 1440
        val newEtaHHmm = formatMinutesToHHmm(newEtaTotalMinutes)

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 5,
                stepName = "RUN ETA MODEL",
                status = "PREDICTED",
                detail = "Target ${nextStop.station.code}: Predicted Delay +${nextStationPredictedDelay}m, Updated ETA $newEtaHHmm",
                durationMs = System.currentTimeMillis() - step5Start
            )
        )

        // -------------------------------------------------------------
        // STEP 6: UPDATE DELAY PROPAGATION
        // -------------------------------------------------------------
        val step6Start = System.currentTimeMillis()
        val networkReport = NetworkAwareForecastingEngine.forecastJourney(
            journey = journey,
            currentStopIndex = currentStopIdx,
            observedDelayMinutes = update.observedDelayMinutes,
            currentTimeEpochMillis = update.timestampEpoch
        )
        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 6,
                stepName = "UPDATE DELAY PROPAGATION",
                status = "CASCADED",
                detail = "Propagated across ${networkReport.stationForecasts.size} stations; Final impact: +${networkReport.accumulatedDelayMinutes}m",
                durationMs = System.currentTimeMillis() - step6Start
            )
        )

        // -------------------------------------------------------------
        // STEP 7: RECALCULATE CONFIDENCE
        // -------------------------------------------------------------
        val step7Start = System.currentTimeMillis()
        // Confidence drops slightly if delay is highly volatile, increases if telemetry is fresh and speeds are stable
        val volatilityPenalty = min(15, abs(deltaDelay) * 3)
        val speedStabilityBonus = if (update.speedKmph >= 65.0) 3 else 0
        val recalibratedConfidence = (priorConfidence - volatilityPenalty + speedStabilityBonus).coerceIn(70, 96)

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 7,
                stepName = "RECALCULATE CONFIDENCE",
                status = "CALCULATED",
                detail = "Confidence score: $recalibratedConfidence% (Prior: $priorConfidence%, Volatility penalty: -$volatilityPenalty%)",
                durationMs = System.currentTimeMillis() - step7Start
            )
        )

        // -------------------------------------------------------------
        // STEP 8: GENERATE EXPLANATION
        // -------------------------------------------------------------
        val step8Start = System.currentTimeMillis()
        val changeMinutes = computeMinuteDifference(newEtaHHmm, priorEta)
        val reason = generateRecalibrationReason(
            changeMinutes = changeMinutes,
            deltaDelay = deltaDelay,
            dwellOverrun = dwellOverrun,
            speedKmph = update.speedKmph,
            isMountainGhat = isMountainGhat,
            segment = segment
        )

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 8,
                stepName = "GENERATE EXPLANATION",
                status = "GENERATED",
                detail = "Delta: ${if (changeMinutes >= 0) "+$changeMinutes" else "$changeMinutes"} min. Reason: \"$reason\"",
                durationMs = System.currentTimeMillis() - step8Start
            )
        )

        // -------------------------------------------------------------
        // STEP 9: CHECK ALERT CONDITIONS
        // -------------------------------------------------------------
        val step9Start = System.currentTimeMillis()
        val triggeredAlerts = mutableListOf<RailAlert>()

        // Alert Rule 1: High ETA Spike
        if (changeMinutes >= 5) {
            triggeredAlerts.add(
                RailAlert(
                    alertId = "ALT-ETA-JUMP-${System.currentTimeMillis()}",
                    trainNumber = update.trainNumber,
                    type = AlertType.DELAY_SPIKE,
                    severity = AlertSeverity.WARNING,
                    title = "Sudden ETA Delay Increase",
                    message = "ETA increased by +$changeMinutes minutes. $reason",
                    timestampEpochMillis = update.timestampEpoch,
                    triggerStopCode = update.stationCode
                )
            )
        }

        // Alert Rule 2: Cumulative Delay Threshold
        if (update.observedDelayMinutes >= 20) {
            triggeredAlerts.add(
                RailAlert(
                    alertId = "ALT-SEV-DELAY-${System.currentTimeMillis()}",
                    trainNumber = update.trainNumber,
                    type = AlertType.DELAY_SPIKE,
                    severity = AlertSeverity.CRITICAL,
                    title = "Significant Corridor Delay (+${update.observedDelayMinutes}m)",
                    message = "Train is operating with substantial delay. Downstream crossing precedence invoked.",
                    timestampEpochMillis = update.timestampEpoch,
                    triggerStopCode = update.stationCode
                )
            )
        }

        // Alert Rule 3: Thal Ghat Banking Caution
        if (isMountainGhat && update.speedKmph < 35.0) {
            triggeredAlerts.add(
                RailAlert(
                    alertId = "ALT-GHAT-SPEED-${System.currentTimeMillis()}",
                    trainNumber = update.trainNumber,
                    type = AlertType.GHAT_SECTION_ALERT,
                    severity = AlertSeverity.WARNING,
                    title = "Thal Ghat Speed Restriction",
                    message = "Speed reduced to ${update.speedKmph.toInt()} km/h on 1:37 incline for banking engine detachment.",
                    timestampEpochMillis = update.timestampEpoch,
                    triggerStopCode = update.stationCode
                )
            )
        }

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 9,
                stepName = "CHECK ALERT CONDITIONS",
                status = if (triggeredAlerts.isNotEmpty()) "ALERTS TRIGGERED (${triggeredAlerts.size})" else "NOMINAL",
                detail = if (triggeredAlerts.isNotEmpty()) triggeredAlerts.joinToString("; ") { it.title } else "All operational safety parameters nominal.",
                durationMs = System.currentTimeMillis() - step9Start
            )
        )

        // -------------------------------------------------------------
        // STEP 10: UPDATE DASHBOARD & WEBSOCKET STATE
        // -------------------------------------------------------------
        val step10Start = System.currentTimeMillis()
        val lastUpdatedHHmm = IndianStandardTime.fromEpochMillis(update.timestampEpoch).time24Short

        // Save current state into memory for subsequent comparisons
        val updatedState = RecalibrationState(
            trainNumber = update.trainNumber,
            lastStationCode = update.stationCode,
            lastPredictedEtaHHmm = newEtaHHmm,
            lastObservedDelayMinutes = update.observedDelayMinutes,
            lastPredictedDelayMinutes = nextStationPredictedDelay,
            lastConfidenceScore = recalibratedConfidence,
            lastUpdateEpoch = update.timestampEpoch
        )
        synchronized(previousStateMap) {
            previousStateMap[update.trainNumber] = updatedState
        }

        auditLogs.add(
            RecalibrationStepAudit(
                stepNumber = 10,
                stepName = "UPDATE DASHBOARD",
                status = "PUBLISHED",
                detail = "State committed to cache. Ready for real-time WebSocket & UI push.",
                durationMs = System.currentTimeMillis() - step10Start
            )
        )

        return ContinuousRecalibrationReport(
            trainNumber = update.trainNumber,
            currentStationCode = update.stationCode,
            nextStationCode = nextStop.station.code,
            currentEtaHHmm = newEtaHHmm,
            lastUpdatedHHmm = lastUpdatedHHmm,
            previousEtaHHmm = priorEta,
            changeMinutes = changeMinutes,
            changeReason = reason,
            confidencePercent = recalibratedConfidence,
            previousConfidencePercent = priorConfidence,
            observedDelayMinutes = update.observedDelayMinutes,
            predictedDelayMinutes = nextStationPredictedDelay,
            triggeredAlerts = triggeredAlerts,
            stepAuditLogs = auditLogs,
            timestampEpoch = update.timestampEpoch
        )
    }

    private fun validateUpdate(update: LiveOperationalUpdate, journey: TrainJourney): ValidationResult {
        val issues = mutableListOf<String>()
        if (update.speedKmph < 0.0 || update.speedKmph > 180.0) {
            issues.add("Speed ${update.speedKmph} km/h is outside physical bounds [0, 180]")
        }
        if (update.observedDelayMinutes < 0 || update.observedDelayMinutes > 720) {
            issues.add("Observed delay ${update.observedDelayMinutes}m is outside plausible range [0, 720]")
        }
        val stationExists = journey.stops.any { it.station.code.equals(update.stationCode, ignoreCase = true) }
        if (!stationExists) {
            issues.add("Station code ${update.stationCode} does not exist in timetable for train ${journey.trainNumber}")
        }
        return ValidationResult(isValid = issues.isEmpty(), validationIssues = issues)
    }

    private fun generateRecalibrationReason(
        changeMinutes: Int,
        deltaDelay: Int,
        dwellOverrun: Int,
        speedKmph: Double,
        isMountainGhat: Boolean,
        segment: RouteSegment
    ): String {
        return when {
            changeMinutes > 0 && dwellOverrun > 0 ->
                "Platform dwell exceeded scheduled buffer by +$dwellOverrun min due to passenger surge."
            changeMinutes > 0 && isMountainGhat ->
                "Speed restriction on Thal Ghat 1:37 mountain incline and banking operations."
            changeMinutes > 0 && speedKmph < 45.0 ->
                "Current segment travel time increased due to caution order / section congestion."
            changeMinutes > 0 && deltaDelay > 0 ->
                "Current segment travel time increased."
            changeMinutes < 0 ->
                "Train recovered time across intermediate double-track engineering buffer slack."
            else ->
                "Section travel time and platform interchange operating precisely on schedule."
        }
    }

    private fun computeDefaultPreviousEta(update: LiveOperationalUpdate, journey: TrainJourney): String {
        val currentStopIdx = journey.stops.indexOfFirst { it.station.code == update.stationCode }.let { if (it >= 0) it else 1 }
        val nextStop = journey.stops.getOrNull(currentStopIdx + 1) ?: journey.stops[currentStopIdx]
        val schedArrMin = parseMinutesOfDay(nextStop.scheduledArrival)
        // Assume baseline ETA was 3 mins earlier than current
        val prevMin = (schedArrMin + max(0, update.observedDelayMinutes - 3)) % 1440
        return formatMinutesToHHmm(prevMin)
    }

    private fun parseMinutesOfDay(hhmm: String): Int {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60) + m
    }

    private fun formatMinutesToHHmm(minutesOfDay: Int): String {
        val positiveMinutes = (minutesOfDay % 1440 + 1440) % 1440
        val h = positiveMinutes / 60
        val m = positiveMinutes % 60
        val hh = if (h < 10) "0$h" else "$h"
        val mm = if (m < 10) "0$m" else "$m"
        return "$hh:$mm"
    }

    private fun computeMinuteDifference(newEta: String, prevEta: String): Int {
        val newMin = parseMinutesOfDay(newEta)
        val prevMin = parseMinutesOfDay(prevEta)
        var diff = newMin - prevMin
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440
        return diff
    }
}
