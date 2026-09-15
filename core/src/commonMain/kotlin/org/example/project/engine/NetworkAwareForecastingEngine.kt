package org.example.project.engine

import org.example.project.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Network-Aware Chained Corridor Forecasting Engine.
 *
 * Models railway progression as a continuous chained path:
 * Train -> Station 1 -> Segment 1-2 -> Station 2 -> Segment 2-3 -> Station 3 -> Segment 3-4 -> Station 4
 *
 * Replaces independent station-wise predictions by factoring:
 * 1. Physical segment kinematics (track distance & MPS limits)
 * 2. Delay-induced platform dwell expansion (crowd boarding bottlenecks)
 * 3. Shared infrastructure block contention (single-track crossings & junction clearance)
 * 4. Sectional engineering slack absorption
 * 5. Multi-train interaction across shared track blocks
 */
object NetworkAwareForecastingEngine {

    /**
     * Executes chained forward simulation across all stations and intermediate segments.
     */
    fun forecastJourney(
        journey: TrainJourney,
        currentStopIndex: Int,
        observedDelayMinutes: Int,
        currentTimeEpochMillis: Long = System.currentTimeMillis()
    ): NetworkAwareForecastReport {
        val stops = journey.stops
        val safeCurrentIdx = currentStopIndex.coerceIn(0, stops.lastIndex)
        val currentStop = stops[safeCurrentIdx]

        val stationForecasts = mutableListOf<ChainedStationForecast>()
        val segmentTransits = mutableListOf<ChainedSegmentTransit>()
        val multiTrainConflicts = mutableListOf<PrecedenceResolution>()

        // Helper to parse HH:mm into epoch millis on the current operational date
        val baseDateEpoch = currentTimeEpochMillis - (currentTimeEpochMillis % 86_400_000L)
        fun parseTimetableEpoch(hhmm: String): Long {
            val parts = hhmm.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return baseDateEpoch + (h * 3600_000L) + (m * 60_000L) - IndianStandardTime.IST_OFFSET_MILLIS
        }

        var runningDelay = max(0, observedDelayMinutes)
        var previousDepEpoch = parseTimetableEpoch(currentStop.scheduledDeparture) + (runningDelay * 60_000L)

        // Process each station and segment in sequence
        stops.forEachIndexed { idx, stop ->
            val schedArrEpoch = parseTimetableEpoch(stop.scheduledArrival)
            val schedDepEpoch = parseTimetableEpoch(stop.scheduledDeparture)

            val stationNode = StationNode(
                stationCode = stop.station.code,
                stationName = stop.station.name,
                sequence = idx + 1,
                isJunction = stop.station.isJunction,
                platformCount = if (stop.station.isJunction) 6 else 3,
                scheduledDwellMinutes = stop.scheduledDwellMinutes
            )

            when {
                idx < safeCurrentIdx -> {
                    // Past cleared station
                    stationForecasts.add(
                        ChainedStationForecast(
                            stationNode = stationNode,
                            scheduledArrivalEpoch = schedArrEpoch,
                            scheduledDepartureEpoch = schedDepEpoch,
                            predictedArrivalEpoch = schedArrEpoch,
                            predictedDepartureEpoch = schedDepEpoch,
                            predictedArrivalHHmm = stop.scheduledArrival,
                            predictedDepartureHHmm = stop.scheduledDeparture,
                            arrivalDelayMinutes = 0,
                            departureDelayMinutes = 0,
                            dwellDurationMinutes = stop.scheduledDwellMinutes,
                            dwellInflationMinutes = 0,
                            dwellExplanation = "Cleared on schedule"
                        )
                    )
                }

                idx == safeCurrentIdx -> {
                    // Current station: OBSERVED DELAY directly from GPS / track telemetry
                    val arrEpoch = schedArrEpoch + (observedDelayMinutes * 60_000L)
                    val dwellInflation = if (observedDelayMinutes >= 15) 2 else if (observedDelayMinutes >= 8) 1 else 0
                    val dwellTotal = stop.scheduledDwellMinutes + dwellInflation
                    val depEpoch = arrEpoch + (dwellTotal * 60_000L)
                    previousDepEpoch = depEpoch

                    val arrIst = IndianStandardTime.fromEpochMillis(arrEpoch)
                    val depIst = IndianStandardTime.fromEpochMillis(depEpoch)

                    stationForecasts.add(
                        ChainedStationForecast(
                            stationNode = stationNode,
                            scheduledArrivalEpoch = schedArrEpoch,
                            scheduledDepartureEpoch = schedDepEpoch,
                            predictedArrivalEpoch = arrEpoch,
                            predictedDepartureEpoch = depEpoch,
                            predictedArrivalHHmm = arrIst.time24Short,
                            predictedDepartureHHmm = depIst.time24Short,
                            arrivalDelayMinutes = observedDelayMinutes,
                            departureDelayMinutes = ((depEpoch - schedDepEpoch) / 60_000L).toInt(),
                            dwellDurationMinutes = dwellTotal,
                            dwellInflationMinutes = dwellInflation,
                            dwellExplanation = if (dwellInflation > 0) {
                                "Platform rush: +$dwellInflation min dwell expansion due to crowd accumulation"
                            } else "Normal platform interchange dwell"
                        )
                    )
                }

                else -> {
                    // Upcoming station: Traverse intermediate segment first!
                    val prevStop = stops[idx - 1]
                    val segment = RailwayNetworkTopologyRegistry.getSegment(prevStop.station.code, stop.station.code)

                    // 1. Physical Kinematic Base Runtime (bounded by MPS)
                    val effectiveMps = max(40.0, segment.speedLimitKmh * 0.82)
                    var kinematicRuntime = (segment.distanceKm / effectiveMps) * 60.0
                    if (segment.isGhatSection) {
                        kinematicRuntime *= segment.gradientFactor // Kasara - Igatpuri incline
                    }

                    // 2. Segment Contention & Delays
                    var congestionPenalty = 0
                    val reasons = mutableListOf<String>()

                    // Single-track block crossing conflict
                    if (segment.isSingleTrack && runningDelay > 5) {
                        val wait = if (journey.trainType.priorityScore >= 8) 4 else 8
                        congestionPenalty += wait
                        reasons.add("Single track token section crossing wait: +$wait min")
                    }

                    // Active trains in block conflict (Multi-train network awareness)
                    val otherTrains = RailwayNetworkTopologyRegistry.getActiveTrainsInBlock(segment.segmentId)
                        .filter { it != journey.trainNumber }
                    if (otherTrains.isNotEmpty()) {
                        val activeTrain = otherTrains.first()
                        congestionPenalty += 5
                        reasons.add("Block headway spacing behind train $activeTrain: +5 min")
                        multiTrainConflicts.add(
                            PrecedenceResolution(
                                higherPriorityTrainNumber = journey.trainNumber,
                                lowerPriorityTrainNumber = activeTrain,
                                crossingStationCode = stop.station.code,
                                lowerPriorityDelayPenaltyMinutes = 5,
                                reason = "Line occupancy headway on segment ${segment.segmentId}"
                            )
                        )
                    }

                    // Junction clearance
                    if (stop.station.isJunction && stop.junctionCongestionFactor > 1.05 && runningDelay > 8) {
                        val juncPenalty = ((stop.junctionCongestionFactor - 1.0) * 8.0).roundToInt()
                        congestionPenalty += juncPenalty
                        reasons.add("Junction approach queue: +$juncPenalty min")
                    }

                    // 3. Section Engineering Slack Absorption
                    val priorityWeight = journey.trainType.priorityScore / 10.0
                    val recovery = if (runningDelay > 0 && segment.sectionSlackMinutes > 0) {
                        val potRecovery = (segment.sectionSlackMinutes * priorityWeight * 0.75).roundToInt()
                        min(runningDelay, max(1, potRecovery))
                    } else 0
                    if (recovery > 0) reasons.add("Absorbing -$recovery min scheduled segment slack")

                    val netTransitVariance = congestionPenalty - recovery
                    val effectiveRuntime = max(kinematicRuntime * 0.9, kinematicRuntime + netTransitVariance)

                    segmentTransits.add(
                        ChainedSegmentTransit(
                            segment = segment,
                            kinematicRuntimeMinutes = kinematicRuntime,
                            congestionPenaltyMinutes = congestionPenalty,
                            slackAbsorptionMinutes = recovery,
                            effectiveRuntimeMinutes = effectiveRuntime,
                            precedenceConflictWithTrain = otherTrains.firstOrNull(),
                            transitExplanation = if (reasons.isNotEmpty()) reasons.joinToString(" • ") else "Kinematic nominal transit"
                        )
                    )

                    // Arrive at Station idx:
                    val arrEpoch = previousDepEpoch + (effectiveRuntime * 60_000L).toLong()
                    runningDelay = max(0, ((arrEpoch - schedArrEpoch) / 60_000L).toInt())

                    // Dwell inflation at upcoming station
                    val dwellInflation = when {
                        runningDelay >= 20 && stop.scheduledDwellMinutes >= 2 -> 3
                        runningDelay >= 10 && stop.scheduledDwellMinutes >= 2 -> 2
                        runningDelay >= 5 && stop.scheduledDwellMinutes >= 2 -> 1
                        else -> 0
                    }
                    val dwellTotal = stop.scheduledDwellMinutes + dwellInflation
                    val depEpoch = arrEpoch + (dwellTotal * 60_000L)
                    previousDepEpoch = depEpoch

                    val arrIst = IndianStandardTime.fromEpochMillis(arrEpoch)
                    val depIst = IndianStandardTime.fromEpochMillis(depEpoch)

                    stationForecasts.add(
                        ChainedStationForecast(
                            stationNode = stationNode,
                            scheduledArrivalEpoch = schedArrEpoch,
                            scheduledDepartureEpoch = schedDepEpoch,
                            predictedArrivalEpoch = arrEpoch,
                            predictedDepartureEpoch = depEpoch,
                            predictedArrivalHHmm = arrIst.time24Short,
                            predictedDepartureHHmm = depIst.time24Short,
                            arrivalDelayMinutes = runningDelay,
                            departureDelayMinutes = ((depEpoch - schedDepEpoch) / 60_000L).toInt(),
                            dwellDurationMinutes = dwellTotal,
                            dwellInflationMinutes = dwellInflation,
                            dwellExplanation = if (dwellInflation > 0) {
                                "+$dwellInflation min passenger boarding rush (train arriving delayed)"
                            } else "Timetable dwell duration"
                        )
                    )
                }
            }
        }

        val totalDist = segmentTransits.sumOf { it.segment.distanceKm }
        val totalRuntime = segmentTransits.sumOf { it.effectiveRuntimeMinutes } + stationForecasts.sumOf { it.dwellDurationMinutes.toDouble() }

        val networkAdvantage = "Network-aware chained forecasting enforces physical kinematic speed limits across intermediate segments and dynamically accounts for dwell expansion (+1-3m) and shared single-track block contention, preventing unphysical station arrival times."

        return NetworkAwareForecastReport(
            trainNumber = journey.trainNumber,
            routeId = "CORRIDOR-CR-CSMT-MMR",
            currentStationIndex = safeCurrentIdx,
            currentStationCode = currentStop.station.code,
            initialObservedDelayMinutes = observedDelayMinutes,
            stationForecasts = stationForecasts,
            segmentTransits = segmentTransits,
            totalJourneyDistanceKm = totalDist,
            totalRuntimeMinutes = totalRuntime,
            accumulatedDelayMinutes = runningDelay,
            multiTrainConflicts = multiTrainConflicts,
            networkAdvantageExplanation = networkAdvantage,
            generatedAtEpochMillis = currentTimeEpochMillis
        )
    }
}
