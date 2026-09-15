package org.example.project.engine

import org.example.project.model.ScheduleStop
import org.example.project.model.TrainJourney
import org.example.project.model.TrainJourneyFeatureVector
import org.example.project.model.TrainTelemetrySnapshot
import kotlin.math.max

/**
 * Feature Engineering Engine.
 *
 * Extracts and normalizes input variables into standard TrainJourneyFeatureVector
 * for consumption by any IEtaPredictor implementation.
 */
object FeatureEngineeringEngine {

    /**
     * Extracts an inference feature vector for an upcoming target stop.
     */
    fun extractFeatures(
        journey: TrainJourney,
        currentStopIndex: Int,
        targetStopIndex: Int,
        telemetry: TrainTelemetrySnapshot,
        precedingHeadwayMinutes: Int = 15,
        weatherImpairmentIndex: Float = 0.0f
    ): TrainJourneyFeatureVector {
        val stops = journey.stops
        val safeCurrentIdx = currentStopIndex.coerceIn(0, stops.lastIndex)
        val safeTargetIdx = targetStopIndex.coerceIn(safeCurrentIdx, stops.lastIndex)

        val currentStop = stops[safeCurrentIdx]
        val targetStop = stops[safeTargetIdx]

        val distanceRemaining = max(0, targetStop.distanceKm - currentStop.distanceKm).toDouble()

        // Section MPS (Maximum Permissible Speed)
        val sectionMps = targetStop.speedLimitKmph.toDouble()
        val speedDeltaVsMps = telemetry.speedKmH - sectionMps

        // Sum available slack between current position and target stop
        var accumulatedSlack = 0
        for (i in (safeCurrentIdx + 1)..safeTargetIdx) {
            accumulatedSlack += stops[i].sectionSlackMinutes
        }

        // Gradient & Terrain Recognition (e.g. Kasara - Igatpuri Thal Ghat 1:37 incline)
        val hasGhatSection = isGhatSectionBetween(stops, safeCurrentIdx, safeTargetIdx)
        val gradientFactor = if (hasGhatSection) 1.37 else 1.0

        // Single-track and junction checks
        var hasSingleTrack = false
        var hasJunction = false
        var maxJunctionCongestion = 1.0

        for (i in (safeCurrentIdx + 1)..safeTargetIdx) {
            val stop = stops[i]
            if (stop.isSingleTrackSegment) hasSingleTrack = true
            if (stop.station.isJunction) {
                hasJunction = true
                if (stop.junctionCongestionFactor > maxJunctionCongestion) {
                    maxJunctionCongestion = stop.junctionCongestionFactor
                }
            }
        }

        // Time of Day & Day of Week calculation from telemetry timestamp
        val ist = org.example.project.model.IndianStandardTime.fromEpochMillis(telemetry.timestampEpochMillis)
        val timeOfDayMinutes = ist.hour * 60 + ist.minute
        val timeOfDayBucket = when (timeOfDayMinutes) {
            in 420..660 -> "MORNING_PEAK" // 07:00 - 11:00
            in 661..960 -> "MIDDAY"       // 11:01 - 16:00
            in 961..1260 -> "EVENING_RUSH" // 16:01 - 21:00
            else -> "NIGHT"
        }

        // Day of week calculation (1 = Monday, 7 = Sunday)
        val daysSinceEpoch = (telemetry.timestampEpochMillis + org.example.project.model.IndianStandardTime.IST_OFFSET_MILLIS) / (86_400_000L)
        // 1970-01-01 was Thursday (4)
        val dayOfWeek = (((daysSinceEpoch + 3) % 7) + 1).toInt()
        val dayOfWeekName = when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Sunday"
        }

        // Parse scheduled segment travel time
        fun parseMinutesOfDay(timeStr: String): Int {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val h = parts[0].trim().toIntOrNull() ?: 0
                val m = parts[1].trim().toIntOrNull() ?: 0
                return h * 60 + m
            }
            return 0
        }
        val targetArrMinutes = parseMinutesOfDay(targetStop.scheduledArrival)
        val currentDepMinutes = parseMinutesOfDay(currentStop.scheduledDeparture)
        val rawSegmentMinutes = targetArrMinutes - currentDepMinutes
        val scheduledSegmentMinutes = if (rawSegmentMinutes > 0) rawSegmentMinutes else max(15, (distanceRemaining / (sectionMps * 0.75) * 60.0).toInt())

        // Kinematic segment travel time: pure physics travel time based on distance & MPS
        val effectiveSpeed = max(30.0, sectionMps * 0.82)
        val kinematicMinutes = if (distanceRemaining > 0) (distanceRemaining / effectiveSpeed) * 60.0 else 0.0

        // Historical travel time and delay distributions
        val historicalTravelTime = kinematicMinutes * 1.08 + (safeTargetIdx * 1.5)
        val historicalMeanDelay = (2.5 + (safeTargetIdx * 0.8)).coerceIn(1.5, 18.0)
        val historicalStdDev = (1.2 + (safeTargetIdx * 0.3)).coerceIn(1.0, 7.5)

        // Previous station delay
        val prevStationDelay = if (safeCurrentIdx > 0) telemetry.currentDelayMinutes else 0

        // Route Identification
        val routeId = "CR-${journey.source.code}-${journey.destination.code}-01"

        return TrainJourneyFeatureVector(
            trainNumber = journey.trainNumber,
            trainType = journey.trainType,
            originCode = journey.source.code,
            currentStationCode = currentStop.station.code,
            targetStationCode = targetStop.station.code,
            distanceRemainingKm = distanceRemaining,
            currentSpeedKmH = telemetry.speedKmH,
            sectionMpsKmH = sectionMps,
            speedDeltaVsMps = speedDeltaVsMps,
            currentDelayMinutes = telemetry.currentDelayMinutes,
            scheduledSlackMinutes = accumulatedSlack,
            gradientFactor = gradientFactor,
            weatherImpairmentIndex = weatherImpairmentIndex,
            precedingHeadwayMinutes = precedingHeadwayMinutes,
            isSingleTrackAhead = hasSingleTrack,
            isJunctionAhead = hasJunction,
            junctionCongestionFactor = maxJunctionCongestion,

            // Explicit 15 Operational Features
            scheduledArrivalEpochMillis = telemetry.timestampEpochMillis + (scheduledSegmentMinutes * 60_000L),
            scheduledArrivalHHmm = targetStop.scheduledArrival,
            scheduledDepartureEpochMillis = telemetry.timestampEpochMillis - (telemetry.currentDelayMinutes * 60_000L),
            scheduledDepartureHHmm = currentStop.scheduledDeparture,
            currentLatitude = telemetry.latitude,
            currentLongitude = telemetry.longitude,
            previousStationDelayMinutes = prevStationDelay,
            routeId = routeId,
            currentStationSequence = safeCurrentIdx + 1,
            targetStationSequence = safeTargetIdx + 1,
            totalStationsInRoute = stops.size,
            historicalTravelTimeMinutes = historicalTravelTime,
            historicalMeanDelayMinutes = historicalMeanDelay,
            historicalDelayStdDev = historicalStdDev,
            timeOfDayMinutes = timeOfDayMinutes,
            timeOfDayBucket = timeOfDayBucket,
            dayOfWeek = dayOfWeek,
            dayOfWeekName = dayOfWeekName,
            downstreamCongestionFactor = maxJunctionCongestion,
            accumulatedDelayMinutes = telemetry.currentDelayMinutes,
            scheduledSegmentTravelTimeMinutes = scheduledSegmentMinutes,
            kinematicSegmentTravelTimeMinutes = kinematicMinutes
        )
    }

    /**
     * Checks if the Thal Ghat (Kasara - Igatpuri) section lies within the segment.
     */
    private fun isGhatSectionBetween(stops: List<ScheduleStop>, fromIdx: Int, toIdx: Int): Boolean {
        for (i in fromIdx until toIdx) {
            val fromCode = stops[i].station.code
            val toCode = stops[i + 1].station.code
            val fromName = stops[i].station.name
            val toName = stops[i + 1].station.name
            if (fromCode == "KSRA" || toCode == "KSRA" || fromCode == "IGP" || toCode == "IGP" ||
                fromName.contains("Thal Ghat", ignoreCase = true) || toName.contains("Thal Ghat", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
