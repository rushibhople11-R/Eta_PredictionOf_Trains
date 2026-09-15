package org.example.project.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.project.model.*

enum class ServerConnectionStatus(val label: String, val isConnected: Boolean) {
    ONLINE_STREAMING("LIVE SERVER (Connected)", true),
    CONNECTING("Connecting to Ktor Server...", false),
    OFFLINE_SIMULATION("OFFLINE LOCAL SIMULATION", false)
}

sealed class LiveStreamMessage {
    data class Connected(val serverTime: String, val model: String) : LiveStreamMessage()
    data class ClockTick(val istFormatted: String, val epochMillis: Long) : LiveStreamMessage()
    data class TelemetryUpdate(val trainNumber: String, val speedKmH: Double, val delayMinutes: Int, val eta: String) : LiveStreamMessage()
    data class ContingencyAlert(val title: String, val penaltyMinutes: Int, val affectedTrain: String) : LiveStreamMessage()

    data class ActualMovement(
        val movementId: Long,
        val runId: String,
        val trainNumber: String,
        val stationCode: String,
        val stationName: String,
        val actualArrival: String,
        val actualDeparture: String,
        val observedDelayMinutes: Int,
        val platformUsed: Int,
        val timestampEpoch: Long
    ) : LiveStreamMessage()

    data class DelayEvent(
        val eventId: Long,
        val runId: String,
        val trainNumber: String,
        val stationCode: String,
        val delayDurationMinutes: Int,
        val primaryReasonCategory: String,
        val detailedExplanation: String,
        val timestampEpoch: Long
    ) : LiveStreamMessage()
}

/**
 * High-resilience real-time streaming client bridge.
 * Bridges `:server` Ktor WebSocket streams with the Compose Multiplatform UI.
 */
class RailNexusLiveStreamClient(
    private val serverBaseUrl: String = "ws://localhost:8080/ws/live-stream"
) {
    private val _status = MutableStateFlow(ServerConnectionStatus.CONNECTING)
    val status: StateFlow<ServerConnectionStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<LiveStreamMessage>(replay = 10, extraBufferCapacity = 64)
    val events: SharedFlow<LiveStreamMessage> = _events.asSharedFlow()

    private var activeJob: Job? = null
    private var closeSocket: (() -> Unit)? = null

    fun startListening(scope: CoroutineScope) {
        stop()
        activeJob = scope.launch {
            _status.value = ServerConnectionStatus.CONNECTING

            // 1. Try real platform WebSocket connection (JDK 11+ on JVM desktop)
            closeSocket = org.example.project.startPlatformWebSocket(
                url = serverBaseUrl,
                onMessage = { text -> handleIncomingJson(text) },
                onConnected = {
                    _status.value = ServerConnectionStatus.ONLINE_STREAMING
                    _events.tryEmit(
                        LiveStreamMessage.Connected(
                            serverTime = "LIVE Ktor WebSocket Active",
                            model = "Quantile LGBM (p10/p90)"
                        )
                    )
                },
                onDisconnected = {
                    _status.value = ServerConnectionStatus.OFFLINE_SIMULATION
                }
            )

            // 2. Resilient ticking clock & fallback heartbeat simulation
            var tickCounter = 0
            val corridorStations = listOf(
                Triple("KYN", "Kalyan Jn", 5),
                Triple("KSRA", "Kasara (Ghat Foot)", 12),
                Triple("IGP", "Igatpuri (Ghat Top)", 18),
                Triple("DVL", "Devlali", 19),
                Triple("NK", "Nashik Road", 21),
                Triple("ODH", "Odha", 22),
                Triple("MMR", "Manmad Jn", 24)
            )

            while (isActive) {
                delay(1000)
                tickCounter++
                val now = System.currentTimeMillis()
                val ist = IndianStandardTime.fromEpochMillis(now)
                _events.emit(
                    LiveStreamMessage.ClockTick(
                        istFormatted = ist.fullFormatted,
                        epochMillis = now
                    )
                )

                // If running in local simulation mode (or to guarantee periodic live UI movement if server isn't pushing)
                if (_status.value == ServerConnectionStatus.OFFLINE_SIMULATION && tickCounter % 9 == 0) {
                    val stopIdx = (tickCounter / 9) % corridorStations.size
                    val (code, name, delayMin) = corridorStations[stopIdx]
                    val movement = LiveStreamMessage.ActualMovement(
                        movementId = now,
                        runId = "run-12109-today",
                        trainNumber = "12109",
                        stationCode = code,
                        stationName = name,
                        actualArrival = ist.time24,
                        actualDeparture = ist.time24,
                        observedDelayMinutes = delayMin,
                        platformUsed = 2,
                        timestampEpoch = now
                    )
                    _events.emit(movement)

                    if (code in listOf("KSRA", "IGP", "NK")) {
                        val (cat, exp) = when (code) {
                            "KSRA" -> "BANKER_ATTACHMENT" to "Twin WAG-9 banking engines coupled for 1:37 Thal Ghat incline"
                            "IGP" -> "THAL_GHAT_BANKING" to "Banker detachment complete at Igatpuri yard; downline headway delay"
                            else -> "HEADWAY_SEPARATION" to "Cautious signal aspect on approach to Nashik Road platform line"
                        }
                        _events.emit(
                            LiveStreamMessage.DelayEvent(
                                eventId = now + 1,
                                runId = "run-12109-today",
                                trainNumber = "12109",
                                stationCode = code,
                                delayDurationMinutes = (delayMin - 6).coerceAtLeast(5),
                                primaryReasonCategory = cat,
                                detailedExplanation = exp,
                                timestampEpoch = now
                            )
                        )
                    }
                }
            }
        }
    }

    fun handleIncomingJson(json: String) {
        fun extractString(key: String): String? {
            val match = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)
            return match?.groupValues?.get(1)
        }
        fun extractLong(key: String): Long? {
            val match = Regex("\"$key\"\\s*:\\s*([0-9-]+)").find(json)
            return match?.groupValues?.get(1)?.toLongOrNull()
        }
        fun extractInt(key: String): Int? {
            val match = Regex("\"$key\"\\s*:\\s*([0-9-]+)").find(json)
            return match?.groupValues?.get(1)?.toIntOrNull()
        }
        fun extractDouble(key: String): Double? {
            val match = Regex("\"$key\"\\s*:\\s*([0-9.-]+)").find(json)
            return match?.groupValues?.get(1)?.toDoubleOrNull()
        }

        val type = extractString("type") ?: return
        when (type) {
            "CONNECTED" -> {
                val time = extractString("time") ?: ""
                val model = extractString("activeModel") ?: ""
                _status.value = ServerConnectionStatus.ONLINE_STREAMING
                _events.tryEmit(LiveStreamMessage.Connected(time, model))
            }
            "TICK_IST" -> {
                val epoch = extractLong("epochMillis") ?: 0L
                val time24 = extractString("time24") ?: ""
                _events.tryEmit(LiveStreamMessage.ClockTick(time24, epoch))
            }
            "TELEMETRY_UPDATE" -> {
                val train = extractString("trainNumber") ?: ""
                val speed = extractDouble("speedKmH") ?: 0.0
                val delay = extractInt("predictedDelayMinutes") ?: 0
                val eta = extractString("predictedEta") ?: ""
                _events.tryEmit(LiveStreamMessage.TelemetryUpdate(train, speed, delay, eta))
            }
            "ALERT_BROADCAST" -> {
                val title = extractString("title") ?: "Corridor Alert"
                val train = extractString("trainNumber") ?: ""
                _events.tryEmit(LiveStreamMessage.ContingencyAlert(title, 10, train))
            }
            "ACTUAL_MOVEMENT" -> {
                val movementId = extractLong("movementId") ?: 0L
                val runId = extractString("runId") ?: ""
                val trainNumber = extractString("trainNumber") ?: ""
                val stationCode = extractString("stationCode") ?: ""
                val stationName = extractString("stationName") ?: stationCode
                val actualArrival = extractString("actualArrival") ?: ""
                val actualDeparture = extractString("actualDeparture") ?: ""
                val delayMin = extractInt("observedDelayMinutes") ?: 0
                val platform = extractInt("platformUsed") ?: 1
                val timestamp = extractLong("timestampEpoch") ?: 0L
                _events.tryEmit(
                    LiveStreamMessage.ActualMovement(
                        movementId = movementId,
                        runId = runId,
                        trainNumber = trainNumber,
                        stationCode = stationCode,
                        stationName = stationName,
                        actualArrival = actualArrival,
                        actualDeparture = actualDeparture,
                        observedDelayMinutes = delayMin,
                        platformUsed = platform,
                        timestampEpoch = timestamp
                    )
                )
            }
            "DELAY_EVENT" -> {
                val eventId = extractLong("eventId") ?: 0L
                val runId = extractString("runId") ?: ""
                val trainNumber = extractString("trainNumber") ?: ""
                val stationCode = extractString("stationCode") ?: ""
                val delayMin = extractInt("delayDurationMinutes") ?: 0
                val category = extractString("primaryReasonCategory") ?: "GENERAL_DELAY"
                val explanation = extractString("detailedExplanation") ?: ""
                val timestamp = extractLong("timestampEpoch") ?: 0L
                _events.tryEmit(
                    LiveStreamMessage.DelayEvent(
                        eventId = eventId,
                        runId = runId,
                        trainNumber = trainNumber,
                        stationCode = stationCode,
                        delayDurationMinutes = delayMin,
                        primaryReasonCategory = category,
                        detailedExplanation = explanation,
                        timestampEpoch = timestamp
                    )
                )
            }
        }
    }

    fun dispatchSyntheticContingency(title: String, penalty: Int, train: String) {
        _events.tryEmit(
            LiveStreamMessage.ContingencyAlert(
                title = title,
                penaltyMinutes = penalty,
                affectedTrain = train
            )
        )
    }

    fun dispatchSyntheticMovement(trainNumber: String, stationCode: String, stationName: String, delayMin: Int, platform: Int) {
        val now = System.currentTimeMillis()
        val ist = IndianStandardTime.fromEpochMillis(now)
        _events.tryEmit(
            LiveStreamMessage.ActualMovement(
                movementId = now,
                runId = "run-$trainNumber-today",
                trainNumber = trainNumber,
                stationCode = stationCode,
                stationName = stationName,
                actualArrival = ist.time24,
                actualDeparture = ist.time24,
                observedDelayMinutes = delayMin,
                platformUsed = platform,
                timestampEpoch = now
            )
        )
    }

    fun dispatchSyntheticDelayEvent(trainNumber: String, stationCode: String, delayMin: Int, category: String, explanation: String) {
        val now = System.currentTimeMillis()
        _events.tryEmit(
            LiveStreamMessage.DelayEvent(
                eventId = now,
                runId = "run-$trainNumber-today",
                trainNumber = trainNumber,
                stationCode = stationCode,
                delayDurationMinutes = delayMin,
                primaryReasonCategory = category,
                detailedExplanation = explanation,
                timestampEpoch = now
            )
        )
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        try {
            closeSocket?.invoke()
        } catch (_: Exception) {}
        closeSocket = null
        _status.value = ServerConnectionStatus.OFFLINE_SIMULATION
    }
}
