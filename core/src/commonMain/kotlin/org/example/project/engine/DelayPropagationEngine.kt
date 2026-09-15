package org.example.project.engine

import org.example.project.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DelayPropagationEngine {

    /**
     * Calculates dynamic station-by-station ETAs and delay cascade/recovery trends
     * along a train's journey.
     */
    fun predictJourney(
        journey: TrainJourney,
        currentStopIndex: Int,
        currentDelayMinutes: Int
    ): List<StopPrediction> {
        val stops = journey.stops
        val safeCurrentIndex = currentStopIndex.coerceIn(0, stops.lastIndex)
        var runningDelay = max(0, currentDelayMinutes)

        return stops.mapIndexed { index, stop ->
            when {
                index < safeCurrentIndex -> {
                    // Past station
                    val pastTime = formatTimeOffset(stop.scheduledArrival, 0)
                    StopPrediction(
                        stop = stop,
                        scheduledTime = stop.scheduledArrival,
                        predictedEta = pastTime,
                        delayMinutes = 0,
                        confidenceLowMinutes = 0,
                        confidenceHighMinutes = 0,
                        recoveryMinutes = 0,
                        cascadeRisk = CascadeRisk.STABLE,
                        explanation = "Departed on schedule",
                        isCurrentOrPassed = true,
                        isCurrentStation = false
                    )
                }

                index == safeCurrentIndex -> {
                    // Current station
                    val eta = formatTimeOffset(stop.scheduledArrival, runningDelay)
                    val risk = if (runningDelay > 30) CascadeRisk.MODERATE else CascadeRisk.STABLE
                    StopPrediction(
                        stop = stop,
                        scheduledTime = stop.scheduledArrival,
                        predictedEta = eta,
                        delayMinutes = runningDelay,
                        confidenceLowMinutes = max(0, runningDelay - 2),
                        confidenceHighMinutes = runningDelay + 3,
                        recoveryMinutes = 0,
                        cascadeRisk = risk,
                        explanation = if (runningDelay == 0) "Running right on time" else "Currently delayed by $runningDelay min",
                        isCurrentOrPassed = true,
                        isCurrentStation = true
                    )
                }

                else -> {
                    // Future station - calculate delay propagation & recovery
                    val prevStop = stops[index - 1]
                    val distanceSinceCurrent = stop.distanceKm - stops[safeCurrentIndex].distanceKm

                    // 1. Engineering Slack Recovery
                    val availableSlack = stop.sectionSlackMinutes
                    val recovered = if (runningDelay > 0 && availableSlack > 0) {
                        // High priority trains (Rajdhani/Vande Bharat) utilize slack better
                        val priorityFactor = journey.trainType.priorityScore / 10.0
                        val recoverable = (availableSlack * priorityFactor).roundToInt()
                        min(runningDelay, max(1, recoverable))
                    } else {
                        0
                    }
                    runningDelay = max(0, runningDelay - recovered)

                    // 2. Cascade Penalty: Single-track segment or junction congestion
                    var cascadePenalty = 0
                    val reasons = mutableListOf<String>()

                    if (recovered > 0) {
                        reasons.add("Making up $recovered min via section buffer")
                    }

                    if (runningDelay > 10 && stop.isSingleTrackSegment) {
                        // Crossing wait penalty
                        val penalty = if (journey.trainType.priorityScore >= 8) 6 else 14
                        cascadePenalty += penalty
                        reasons.add("Single track section: +$penalty min crossing risk")
                    }

                    if (stop.station.isJunction && stop.junctionCongestionFactor > 1.1 && runningDelay > 15) {
                        val junctionPenalty = ((stop.junctionCongestionFactor - 1.0) * 12).roundToInt()
                        cascadePenalty += junctionPenalty
                        reasons.add("Busy junction entry: +$junctionPenalty min signal queue")
                    }

                    // 3. Dwell inflation for heavy delays
                    if (runningDelay > 30 && stop.scheduledDwellMinutes > 2) {
                        val dwellInflation = if (journey.trainType.priorityScore >= 9) 1 else 3
                        cascadePenalty += dwellInflation
                        reasons.add("Crowded platform dwell: +$dwellInflation min")
                    }

                    runningDelay += cascadePenalty

                    // 4. Determine Confidence Interval based on distance and bottlenecks
                    val uncertainty = max(3, (distanceSinceCurrent * 0.025).roundToInt() + if (stop.isSingleTrackSegment) 4 else 1)
                    val confLow = max(0, runningDelay - uncertainty)
                    val confHigh = runningDelay + uncertainty + (cascadePenalty / 2)

                    // 5. Determine Cascade Risk Classification
                    val risk = when {
                        recovered > 0 && runningDelay < currentDelayMinutes -> CascadeRisk.RECOVERING
                        cascadePenalty >= 10 -> CascadeRisk.HIGH
                        cascadePenalty > 0 || runningDelay > 30 -> CascadeRisk.MODERATE
                        else -> CascadeRisk.STABLE
                    }

                    val explanation = if (reasons.isNotEmpty()) {
                        reasons.joinToString(" • ")
                    } else if (runningDelay == 0) {
                        "Scheduled run time maintained"
                    } else {
                        "Maintaining stable delay offset"
                    }

                    val eta = formatTimeOffset(stop.scheduledArrival, runningDelay)

                    StopPrediction(
                        stop = stop,
                        scheduledTime = stop.scheduledArrival,
                        predictedEta = eta,
                        delayMinutes = runningDelay,
                        confidenceLowMinutes = confLow,
                        confidenceHighMinutes = confHigh,
                        recoveryMinutes = recovered,
                        cascadeRisk = risk,
                        explanation = explanation,
                        isCurrentOrPassed = false,
                        isCurrentStation = false
                    )
                }
            }
        }
    }

    /**
     * Advanced delay propagation analysis across upcoming corridor stations.
     *
     * Considers:
     * - Current observed delay
     * - Route sequence
     * - Historical recovery patterns & engineering slack absorption
     * - Segment travel times (kinematic vs scheduled)
     * - Delay-induced station dwell inflation (platform crowding)
     * - Downstream congestion (single-track crossings, junction queues, mountain gradients)
     * - Operational conditions (headway compression, weather impairment)
     */
    fun analyzePropagation(
        journey: TrainJourney,
        currentStopIndex: Int,
        observedDelayMinutes: Int,
        precedingHeadwayMinutes: Int = 15,
        weatherImpairmentIndex: Float = 0.0f
    ): DelayPropagationReport {
        val stops = journey.stops
        val safeCurrentIndex = currentStopIndex.coerceIn(0, stops.lastIndex)
        val currentStop = stops[safeCurrentIndex]

        val progressions = mutableListOf<StationDelayProgression>()
        var runningDelay = max(0, observedDelayMinutes)
        var totalSlackRecovery = 0
        var maxProjectedDelay = runningDelay

        stops.forEachIndexed { index, stop ->
            when {
                index < safeCurrentIndex -> {
                    // Past cleared station: historical actual or nominal
                    progressions.add(
                        StationDelayProgression(
                            stationCode = stop.station.code,
                            stationName = stop.station.name,
                            sequence = index + 1,
                            observationType = DelayObservationType.OBSERVED_ACTUAL,
                            scheduledArrival = stop.scheduledArrival,
                            scheduledDeparture = stop.scheduledDeparture,
                            predictedEta = stop.scheduledArrival,
                            delayMinutes = 0,
                            deltaFromPreviousMinutes = 0,
                            slackRecoveryMinutes = 0,
                            congestionPenaltyMinutes = 0,
                            dwellInflationMinutes = 0,
                            cascadeRisk = CascadeRisk.STABLE,
                            explanation = "Cleared and departed on schedule"
                        )
                    )
                }

                index == safeCurrentIndex -> {
                    // Current station: OBSERVED DELAY directly from GPS / track telemetry
                    val eta = formatTimeOffset(stop.scheduledArrival, runningDelay)
                    progressions.add(
                        StationDelayProgression(
                            stationCode = stop.station.code,
                            stationName = stop.station.name,
                            sequence = index + 1,
                            observationType = DelayObservationType.OBSERVED_ACTUAL,
                            scheduledArrival = stop.scheduledArrival,
                            scheduledDeparture = stop.scheduledDeparture,
                            predictedEta = eta,
                            delayMinutes = runningDelay,
                            deltaFromPreviousMinutes = 0,
                            slackRecoveryMinutes = 0,
                            congestionPenaltyMinutes = 0,
                            dwellInflationMinutes = 0,
                            cascadeRisk = if (runningDelay > 20) CascadeRisk.MODERATE else CascadeRisk.STABLE,
                            explanation = if (runningDelay == 0) "Right on time" else "Observed delay at current station: +$runningDelay min"
                        )
                    )
                }

                else -> {
                    // Upcoming station: PREDICTED DOWNSTREAM DELAY
                    val prevDelay = runningDelay
                    val factors = mutableListOf<String>()

                    // 1. Engineering Slack Recovery
                    val availableSlack = stop.sectionSlackMinutes
                    val priorityFactor = journey.trainType.priorityScore / 10.0
                    val recovery = if (runningDelay > 0 && availableSlack > 0) {
                        val potentialRecovery = (availableSlack * priorityFactor * 0.70).roundToInt()
                        min(runningDelay, max(1, potentialRecovery))
                    } else 0

                    // 2. Station Dwell Time Inflation
                    val dwellInflation = when {
                        runningDelay >= 20 && stop.scheduledDwellMinutes >= 2 -> 3
                        runningDelay >= 10 && stop.scheduledDwellMinutes >= 2 -> 2
                        runningDelay >= 5 && stop.scheduledDwellMinutes >= 2 -> 1
                        else -> 0
                    }

                    // 3. Downstream Congestion: Single-track section crossing
                    var congestionPenalty = 0
                    if (stop.isSingleTrackSegment && runningDelay > 5) {
                        val singleTrackWait = if (journey.trainType.priorityScore >= 8) 4 else 8
                        congestionPenalty += singleTrackWait
                        factors.add("+$singleTrackWait min single-track crossing wait")
                    }

                    // 4. Downstream Congestion: Busy Junction signal queue
                    if (stop.station.isJunction && stop.junctionCongestionFactor > 1.05 && runningDelay > 5) {
                        val junctionWait = ((stop.junctionCongestionFactor - 1.0) * 10.0).roundToInt()
                        congestionPenalty += junctionWait
                        factors.add("+$junctionWait min junction approach signal clearance")
                    }

                    // 5. Downstream Congestion: Thal Ghat mountain incline
                    if ((stop.station.code == "IGP" || stop.station.code == "KSRA") && runningDelay > 3) {
                        val ghatWait = if (journey.trainType.priorityScore >= 9) 3 else 7
                        congestionPenalty += ghatWait
                        factors.add("+$ghatWait min banking engine detachment")
                    }

                    // 6. Operational Conditions: Headway braking wave
                    if (precedingHeadwayMinutes < 12) {
                        val headwayWait = ((12 - precedingHeadwayMinutes) * 0.8).roundToInt()
                        congestionPenalty += headwayWait
                        factors.add("+$headwayWait min cautionary double-yellow signaling")
                    }

                    // 7. Operational Conditions: Weather impairment
                    if (weatherImpairmentIndex > 0.15f) {
                        val weatherWait = (weatherImpairmentIndex * 5.0).roundToInt()
                        congestionPenalty += weatherWait
                        factors.add("+$weatherWait min reduced visibility speed restriction")
                    }

                    if (dwellInflation > 0) factors.add("+$dwellInflation min platform dwell rush")
                    if (recovery > 0) factors.add("-$recovery min slack recovery")

                    // Net compounding or recovery
                    val netVariance = congestionPenalty + dwellInflation - recovery
                    runningDelay = max(0, runningDelay + netVariance)
                    totalSlackRecovery += recovery
                    if (runningDelay > maxProjectedDelay) maxProjectedDelay = runningDelay

                    val deltaFromPrev = runningDelay - prevDelay
                    val risk = when {
                        recovery > 0 && deltaFromPrev < 0 -> CascadeRisk.RECOVERING
                        deltaFromPrev >= 5 || runningDelay >= 30 -> CascadeRisk.HIGH
                        deltaFromPrev > 0 || runningDelay >= 15 -> CascadeRisk.MODERATE
                        else -> CascadeRisk.STABLE
                    }

                    val explanation = if (factors.isNotEmpty()) {
                        factors.joinToString(", ")
                    } else if (deltaFromPrev == 0) {
                        "Scheduled runtime maintained without cascade"
                    } else {
                        "Net delay variance: ${if (deltaFromPrev > 0) "+$deltaFromPrev" else "$deltaFromPrev"} min"
                    }

                    val eta = formatTimeOffset(stop.scheduledArrival, runningDelay)

                    progressions.add(
                        StationDelayProgression(
                            stationCode = stop.station.code,
                            stationName = stop.station.name,
                            sequence = index + 1,
                            observationType = DelayObservationType.PREDICTED_DOWNSTREAM,
                            scheduledArrival = stop.scheduledArrival,
                            scheduledDeparture = stop.scheduledDeparture,
                            predictedEta = eta,
                            delayMinutes = runningDelay,
                            deltaFromPreviousMinutes = deltaFromPrev,
                            slackRecoveryMinutes = recovery,
                            congestionPenaltyMinutes = congestionPenalty,
                            dwellInflationMinutes = dwellInflation,
                            cascadeRisk = risk,
                            explanation = explanation
                        )
                    )
                }
            }
        }

        // Count expected affected stations (upcoming stations with delay >= 1 min)
        val downstreamProgressions = progressions.filter { it.observationType == DelayObservationType.PREDICTED_DOWNSTREAM }
        val affectedStationsCount = downstreamProgressions.count { it.delayMinutes >= 1 }
        val finalDelay = downstreamProgressions.lastOrNull()?.delayMinutes ?: observedDelayMinutes

        // Classify propagation severity
        val severity = when {
            maxProjectedDelay >= 30 || affectedStationsCount >= 4 && maxProjectedDelay >= 20 -> DelaySeverity.CRITICAL
            maxProjectedDelay >= 20 -> DelaySeverity.HIGH
            maxProjectedDelay >= 10 -> DelaySeverity.MODERATE
            else -> DelaySeverity.LOW
        }

        // Confidence calculation: decays gracefully with downstream station distance and single-track risk
        val singleTrackCount = downstreamProgressions.count {
            stops.getOrNull(it.sequence - 1)?.isSingleTrackSegment == true
        }
        val distanceDecay = (downstreamProgressions.size * 1.5).toInt()
        val confidence = (94 - distanceDecay - (singleTrackCount * 3)).coerceIn(68, 95)

        // Progression summary string (e.g. "DVL (+18m OBSERVED) ➔ NK (+21m) ➔ ODH (+25m) ➔ MMR (+27m)")
        val summaryParts = mutableListOf<String>()
        summaryParts.add("${currentStop.station.code} (+${observedDelayMinutes}m OBSERVED)")
        downstreamProgressions.take(4).forEach {
            summaryParts.add("${it.stationCode} (+${it.delayMinutes}m)")
        }
        if (downstreamProgressions.size > 4) {
            summaryParts.add("... +${downstreamProgressions.size - 4} more")
        }
        val progressionSummary = summaryParts.joinToString(" ➔ ")

        return DelayPropagationReport(
            trainNumber = journey.trainNumber,
            currentStationCode = currentStop.station.code,
            currentStationIndex = safeCurrentIndex,
            observedDelayMinutes = observedDelayMinutes,
            stationProgressions = progressions,
            expectedAffectedStationsCount = affectedStationsCount,
            severity = severity,
            confidencePercent = confidence,
            maxProjectedDelayMinutes = maxProjectedDelay,
            finalImpactDelayMinutes = finalDelay,
            totalSlackRecoveryMinutes = totalSlackRecovery,
            progressionSummary = progressionSummary,
            generatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    /**
     * Adds minutes to a "HH:mm" 24-hr time string and wraps around midnight.
     */
    fun formatTimeOffset(timeStr: String, offsetMinutes: Int): String {
        val parts = timeStr.split(":")
        if (parts.size != 2) return timeStr
        val hours = parts[0].toIntOrNull() ?: return timeStr
        val mins = parts[1].toIntOrNull() ?: return timeStr

        val totalMins = (hours * 60 + mins + offsetMinutes).mod(24 * 60)
        val newH = (totalMins / 60).toString().padStart(2, '0')
        val newM = (totalMins % 60).toString().padStart(2, '0')
        return "$newH:$newM"
    }
}
