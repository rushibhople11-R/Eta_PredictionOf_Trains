package org.example.project.engine

import org.example.project.model.AlertSeverity
import org.example.project.model.AlertType
import org.example.project.model.PredictionResult
import org.example.project.model.RailAlert

/**
 * Proactive Railway Alert & Notification Engine.
 *
 * Evaluates destination wakeup alarms, missed connection risks,
 * sudden delay surges, and mountain grade alerts.
 */
object AlertEngine {

    /**
     * Evaluates whether a destination wake-up alert should be armed or fired.
     * Fires when estimated arrival is within 15 minutes.
     */
    fun evaluateWakeupAlert(
        trainNumber: String,
        destinationStationCode: String,
        pointEtaEpochMillis: Long,
        currentEpochMillis: Long
    ): RailAlert? {
        val remainingMillis = pointEtaEpochMillis - currentEpochMillis
        val remainingMinutes = remainingMillis / 60_000L

        return if (remainingMinutes in 1..15) {
            RailAlert(
                alertId = "WAKEUP-$trainNumber-$destinationStationCode",
                trainNumber = trainNumber,
                type = AlertType.WAKEUP,
                severity = AlertSeverity.INFO,
                title = "Destination Approaching",
                message = "Train $trainNumber will arrive at $destinationStationCode in approximately ${remainingMinutes}m. Please gather your luggage.",
                timestampEpochMillis = currentEpochMillis,
                triggerStopCode = destinationStationCode
            )
        } else {
            null
        }
    }

    /**
     * Evaluates whether a passenger connection is in jeopardy.
     */
    fun evaluateConnectionRisk(
        trainNumber: String,
        connectingTrainNumber: String,
        interchangeStationCode: String,
        feasibilityScore: Int,
        cushionMinutes: Int,
        currentEpochMillis: Long
    ): RailAlert? {
        return if (feasibilityScore < 50 || cushionMinutes < 5) {
            val severity = if (feasibilityScore < 40 || cushionMinutes <= 3) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            RailAlert(
                alertId = "CONN-RISK-$trainNumber-$connectingTrainNumber",
                trainNumber = trainNumber,
                type = AlertType.CONNECTION_RISK,
                severity = severity,
                title = "Tight Connection Risk at $interchangeStationCode",
                message = "Transfer buffer to connecting train $connectingTrainNumber has shrunk to ${cushionMinutes}m (Feasibility: $feasibilityScore%). Risk of missed connection.",
                timestampEpochMillis = currentEpochMillis,
                triggerStopCode = interchangeStationCode
            )
        } else {
            null
        }
    }

    /**
     * Evaluates sudden delay spikes between consecutive checkpoints.
     */
    fun evaluateDelaySpike(
        trainNumber: String,
        currentStationCode: String,
        previousDelayMinutes: Int,
        currentDelayMinutes: Int,
        currentEpochMillis: Long
    ): RailAlert? {
        val delayDelta = currentDelayMinutes - previousDelayMinutes
        return if (delayDelta >= 10) {
            RailAlert(
                alertId = "SPIKE-$trainNumber-$currentStationCode",
                trainNumber = trainNumber,
                type = AlertType.DELAY_SPIKE,
                severity = AlertSeverity.WARNING,
                title = "Sudden Delay Increase (+${delayDelta}m)",
                message = "Train $trainNumber experienced a sudden ${delayDelta}m delay surge near $currentStationCode.",
                timestampEpochMillis = currentEpochMillis,
                triggerStopCode = currentStationCode
            )
        } else {
            null
        }
    }

    /**
     * Generates a Thal Ghat mountain section operational alert.
     */
    fun evaluateGhatAlert(
        trainNumber: String,
        currentStationCode: String,
        currentEpochMillis: Long
    ): RailAlert? {
        return if (currentStationCode == "KSRA" || currentStationCode == "IGP") {
            RailAlert(
                alertId = "GHAT-$trainNumber-$currentStationCode",
                trainNumber = trainNumber,
                type = AlertType.GHAT_SECTION_ALERT,
                severity = AlertSeverity.INFO,
                title = "Thal Ghat Operations Active",
                message = "Train is operating in the 1:37 Thal Ghat section ($currentStationCode). Banking engine attachment/detachment and brake safety tests in progress.",
                timestampEpochMillis = currentEpochMillis,
                triggerStopCode = currentStationCode
            )
        } else {
            null
        }
    }
}
