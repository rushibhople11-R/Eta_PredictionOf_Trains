package org.example.project.service

import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.example.project.model.IndianStandardTime
import org.example.project.model.IstTime
import org.example.project.model.RailAlert
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-Safe Real-Time WebSocket & SSE Broadcast Hub.
 */
class LiveStreamBroadcastHub {

    // Active WebSocket sessions
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<WebSocketSession, Boolean>())

    // Shared Flow for Server-Sent Events (SSE)
    private val _eventFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    fun registerSession(session: WebSocketSession) {
        sessions.add(session)
    }

    fun unregisterSession(session: WebSocketSession) {
        sessions.remove(session)
    }

    fun getActiveConnectionCount(): Int = sessions.size

    /**
     * Broadcasts the 1-second ticking Indian Standard Time event to all connected clients.
     */
    suspend fun broadcastTick(istTime: IstTime) {
        val payload = buildString {
            append("{\"type\":\"TICK_IST\",")
            append("\"epochMillis\":${istTime.epochMillis},")
            append("\"time12\":\"${istTime.time12}\",")
            append("\"time24\":\"${istTime.time24}\",")
            append("\"dateStr\":\"${istTime.dateStr}\"}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    /**
     * Broadcasts telemetry and prediction updates to all connected clients.
     */
    suspend fun broadcastTelemetry(result: ProcessedTelemetryResult) {
        val payload = buildString {
            append("{\"type\":\"TELEMETRY_UPDATE\",")
            append("\"trainNumber\":\"${result.snapshot.trainNumber}\",")
            append("\"speedKmH\":${result.snapshot.speedKmH},")
            append("\"predictedEta\":\"${result.prediction.predictedEtaHHmm}\",")
            append("\"predictedDelayMinutes\":${result.prediction.predictedDelayMinutes},")
            append("\"p10LowerMinutes\":${result.prediction.quantileLowerMinutes},")
            append("\"p90UpperMinutes\":${result.prediction.quantileUpperMinutes},")
            append("\"confidencePercent\":${result.confidence.scorePercent},")
            append("\"confidenceLevel\":\"${result.confidence.level.label}\",")
            append("\"modelArchitecture\":\"${result.prediction.modelArchitecture.displayName}\",")
            append("\"modelVersion\":\"${result.prediction.modelVersion}\",")
            append("\"explanation\":\"${result.explanations.firstOrNull() ?: "Normal Run"}\"}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    /**
     * Broadcasts an actual station movement update to all connected clients.
     */
    suspend fun broadcastActualMovement(
        movementId: Long,
        runId: String,
        trainNumber: String,
        stationCode: String,
        stationName: String,
        actualArrival: String,
        actualDeparture: String,
        observedDelayMinutes: Int,
        platformUsed: Int,
        timestampEpoch: Long
    ) {
        val payload = buildString {
            append("{\"type\":\"ACTUAL_MOVEMENT\",")
            append("\"movementId\":$movementId,")
            append("\"runId\":\"$runId\",")
            append("\"trainNumber\":\"$trainNumber\",")
            append("\"stationCode\":\"$stationCode\",")
            append("\"stationName\":\"$stationName\",")
            append("\"actualArrival\":\"$actualArrival\",")
            append("\"actualDeparture\":\"$actualDeparture\",")
            append("\"observedDelayMinutes\":$observedDelayMinutes,")
            append("\"platformUsed\":$platformUsed,")
            append("\"timestampEpoch\":$timestampEpoch}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    /**
     * Broadcasts a delay event to all connected clients.
     */
    suspend fun broadcastDelayEvent(
        eventId: Long,
        runId: String,
        trainNumber: String,
        stationCode: String,
        delayDurationMinutes: Int,
        primaryReasonCategory: String,
        detailedExplanation: String,
        timestampEpoch: Long
    ) {
        val payload = buildString {
            append("{\"type\":\"DELAY_EVENT\",")
            append("\"eventId\":$eventId,")
            append("\"runId\":\"$runId\",")
            append("\"trainNumber\":\"$trainNumber\",")
            append("\"stationCode\":\"$stationCode\",")
            append("\"delayDurationMinutes\":$delayDurationMinutes,")
            append("\"primaryReasonCategory\":\"$primaryReasonCategory\",")
            append("\"detailedExplanation\":\"${detailedExplanation.replace("\"", "\\\"")}\",")
            append("\"timestampEpoch\":$timestampEpoch}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    /**
     * Broadcasts an alert to all connected clients.
     */
    suspend fun broadcastAlert(alert: RailAlert) {
        val payload = buildString {
            append("{\"type\":\"ALERT_BROADCAST\",")
            append("\"alertId\":\"${alert.alertId}\",")
            append("\"trainNumber\":\"${alert.trainNumber}\",")
            append("\"title\":\"${alert.title}\",")
            append("\"message\":\"${alert.message}\",")
            append("\"severity\":\"${alert.severity.name}\",")
            append("\"timestamp\":${alert.timestampEpochMillis}}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    /**
     * Broadcasts a continuous recalibration update to all connected WebSocket & SSE clients.
     */
    suspend fun broadcastRecalibration(report: org.example.project.model.ContinuousRecalibrationReport) {
        val payload = buildString {
            append("{\"type\":\"RECALIBRATION_UPDATE\",")
            append("\"trainNumber\":\"${report.trainNumber}\",")
            append("\"currentStationCode\":\"${report.currentStationCode}\",")
            append("\"nextStationCode\":\"${report.nextStationCode}\",")
            append("\"currentEtaHHmm\":\"${report.currentEtaHHmm}\",")
            append("\"lastUpdatedHHmm\":\"${report.lastUpdatedHHmm}\",")
            append("\"previousEtaHHmm\":\"${report.previousEtaHHmm}\",")
            append("\"changeMinutes\":${report.changeMinutes},")
            append("\"changeReason\":\"${report.changeReason.replace("\"", "\\\"")}\",")
            append("\"confidencePercent\":${report.confidencePercent},")
            append("\"observedDelayMinutes\":${report.observedDelayMinutes},")
            append("\"predictedDelayMinutes\":${report.predictedDelayMinutes},")
            append("\"timestampEpoch\":${report.timestampEpoch}}")
        }
        sendToAll(payload)
        _eventFlow.tryEmit(payload)
    }

    private suspend fun sendToAll(message: String) {
        val deadSessions = mutableListOf<WebSocketSession>()
        sessions.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                deadSessions.add(session)
            }
        }
        sessions.removeAll(deadSessions)
    }
}
