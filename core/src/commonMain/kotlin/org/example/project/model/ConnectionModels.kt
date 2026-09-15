package org.example.project.model

import kotlin.math.max
import kotlin.math.min

enum class ServiceType(val label: String) {
    TRAIN("Connecting Train"),
    FLIGHT("Connecting Flight"),
    CAB("Pre-booked Cab / Taxi"),
    METRO("Metro Transit"),
    BUS("Connecting Bus Transit")
}

enum class TransferFeasibility(val label: String, val colorHex: Long) {
    SAFE("High Chance of Connection", 0xFF00E676),      // Green
    TIGHT_RISK("Tight Connection Risk", 0xFFFFB300),   // Amber
    CRITICAL_MISS("High Risk of Missed Connection", 0xFFFF5252) // Red
}

data class ConnectingService(
    val serviceNumber: String,
    val serviceName: String,
    val serviceType: ServiceType,
    val interchangeStationCode: String,
    val departureTime: String, // HH:mm
    val departurePlatform: String = "Pf 1"
)

data class TransferEvaluation(
    val connectingService: ConnectingService,
    val incomingArrivalEta: String,
    val transferBufferMinutes: Int,
    val feasibilityScore: Int, // 0 to 100%
    val feasibility: TransferFeasibility,
    val recommendation: String
)

object ConnectionEngine {

    /**
     * Evaluates whether a passenger can safely transfer to a connecting service
     * based on the incoming train's dynamic predicted arrival time.
     */
    fun evaluateTransfer(
        incomingArrivalEtaStr: String,
        connectingService: ConnectingService,
        platformWalkBufferMinutes: Int = 10
    ): TransferEvaluation {
        val incomingMins = parseTimeToMinutes(incomingArrivalEtaStr)
        var connectingMins = parseTimeToMinutes(connectingService.departureTime)

        var diff = connectingMins - incomingMins
        // Handle midnight rollover:
        // Case 1: Incoming is late evening (e.g. 23:45) and connecting is early morning (e.g. 00:30) -> diff should be +45
        if (diff < -12 * 60) {
            diff += 24 * 60
        }
        // Case 2: Incoming rolled over to early morning (e.g. 00:05) and connecting was scheduled previous evening (e.g. 23:55) -> diff should be -10
        else if (diff > 12 * 60) {
            diff -= 24 * 60
        }

        val effectiveBuffer = diff - platformWalkBufferMinutes

        val score = when {
            effectiveBuffer >= 40 -> 98
            effectiveBuffer >= 25 -> 85
            effectiveBuffer >= 15 -> 65
            effectiveBuffer >= 5 -> 40
            effectiveBuffer >= 0 -> 20
            else -> 5
        }

        val feasibility = when {
            effectiveBuffer >= 25 -> TransferFeasibility.SAFE
            effectiveBuffer >= 5 -> TransferFeasibility.TIGHT_RISK
            else -> TransferFeasibility.CRITICAL_MISS
        }

        val recommendation = when (feasibility) {
            TransferFeasibility.SAFE ->
                "Comfortable transfer! You have ~${effectiveBuffer} min cushion after walking between platforms."
            TransferFeasibility.TIGHT_RISK ->
                "Tight transfer! You have only ${effectiveBuffer} min buffer. Keep luggage ready and move promptly to ${connectingService.departurePlatform}."
            TransferFeasibility.CRITICAL_MISS ->
                "Critical miss alert! Incoming train ETA leaves less than ${max(0, effectiveBuffer)} min to board. Consider exploring backup trains."
        }

        return TransferEvaluation(
            connectingService = connectingService,
            incomingArrivalEta = incomingArrivalEtaStr,
            transferBufferMinutes = effectiveBuffer,
            feasibilityScore = score,
            feasibility = feasibility,
            recommendation = recommendation
        )
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }
}
