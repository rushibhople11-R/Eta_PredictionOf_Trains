package org.example.project.engine

import org.example.project.model.PrecedenceResolution
import org.example.project.model.TrackBlockOccupancy
import org.example.project.model.TrainJourney

/**
 * Network-Aware Track & Precedence Analysis Engine.
 *
 * Models physical track section occupancy, single-line crossing conflicts,
 * and Indian Railways train precedence arbitration.
 */
object NetworkAwareAnalysisEngine {

    /**
     * Resolves track contention or crossing conflict between two active trains on a route.
     *
     * Evaluates train types and priority scores:
     * Vande Bharat (10) > Rajdhani (9) > Shatabdi (8) > Superfast (6) > Mail/Express (4) > Passenger (2)
     */
    fun resolveCrossingPrecedence(
        trainA: TrainJourney,
        trainB: TrainJourney,
        conflictBlock: TrackBlockOccupancy,
        crossingStationCode: String
    ): PrecedenceResolution {
        val priorityA = trainA.trainType.priorityScore
        val priorityB = trainB.trainType.priorityScore

        val (higher, lower) = if (priorityA >= priorityB) {
            Pair(trainA, trainB)
        } else {
            Pair(trainB, trainA)
        }

        // Calculate crossing delay penalty for the yielding train
        // Waiting in loop line for clear block takes 8 - 18 minutes depending on block length
        val baseCrossingWait = 8
        val blockTransitPenalty = (conflictBlock.lengthKm / 2.0).toInt().coerceIn(3, 10)
        val totalPenalty = baseCrossingWait + blockTransitPenalty

        val reason = "Yielding single-line track at $crossingStationCode to high-priority ${higher.trainType.displayName} (${higher.trainNumber})"

        return PrecedenceResolution(
            higherPriorityTrainNumber = higher.trainNumber,
            lowerPriorityTrainNumber = lower.trainNumber,
            crossingStationCode = crossingStationCode,
            lowerPriorityDelayPenaltyMinutes = totalPenalty,
            reason = reason
        )
    }

    /**
     * Detects potential block collision or close headway risk between two trains
     * sharing consecutive block sections.
     */
    fun detectHeadwayRisk(
        leadingTrainSpeedKmH: Double,
        trailingTrainSpeedKmH: Double,
        distanceGapKm: Double
    ): Boolean {
        // If trailing train is faster and within 10 km, yellow/double-yellow signal warning occurs
        return trailingTrainSpeedKmH > leadingTrainSpeedKmH && distanceGapKm < 10.0
    }
}
