package org.example.project

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.engine.HeuristicEtaPredictor
import org.example.project.engine.NetworkAwareAnalysisEngine
import org.example.project.engine.QuantileEtaPredictor
import org.example.project.model.*
import org.example.project.routes.modelPerformanceRoutes
import org.example.project.routes.predictionRoutes
import org.example.project.routes.scenarioRoutes
import org.example.project.routes.nashikGoogleMapRoute
import org.example.project.routes.stationRoutes
import org.example.project.routes.trainRoutes
import org.example.project.service.LiveStreamBroadcastHub
import org.example.project.service.TelemetryIngestionAndValidationService
import org.example.project.service.ValidationResult

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // 1. Install Real-Time Capabilities
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(SSE)

    // 2. Instantiate Core Pipeline Singletons
    val ingestionService = TelemetryIngestionAndValidationService(
        initialPredictor = QuantileEtaPredictor() // Default to Quantile ML predictor
    )
    val broadcastHub = LiveStreamBroadcastHub()
    val corridorSimulator = org.example.project.engine.CorridorSimulationEngine(
        predictor = ingestionService.getActivePredictor()
    )

    // 3. Launch 1-second ticking background coroutine for Indian Standard Time (IST)
    launch {
        while (isActive) {
            val now = System.currentTimeMillis()
            val ist = IndianStandardTime.fromEpochMillis(now)
            broadcastHub.broadcastTick(ist)
            delay(1000)
        }
    }

    val database = org.example.project.db.RailNexusDatabase()

    // 4. Launch corridor actual movement & delay event background WebSocket broadcaster
    launch {
        val corridorStops = listOf(
            Triple("KYN", "Kalyan Jn", 5),
            Triple("KSRA", "Kasara (Ghat Foot)", 12),
            Triple("IGP", "Igatpuri (Ghat Top)", 18),
            Triple("DVL", "Devlali", 19),
            Triple("NK", "Nashik Road", 21),
            Triple("ODH", "Odha", 22),
            Triple("MMR", "Manmad Jn", 24)
        )
        var stopIndex = 0
        var movementCounter = 1000L
        var eventCounter = 2000L

        while (isActive) {
            delay(8000) // Pushes every 8 seconds
            val nowEpoch = System.currentTimeMillis()
            val ist = IndianStandardTime.fromEpochMillis(nowEpoch)
            val (stnCode, stnName, baseDelay) = corridorStops[stopIndex % corridorStops.size]
            val actualDelay = (baseDelay + (-1..2).random()).coerceAtLeast(0)
            val runId = "run-12109-today"
            val trainNum = "12109"

            val movement = org.example.project.db.ActualMovementEntity(
                movementId = ++movementCounter,
                runId = runId,
                stationCode = stnCode,
                actualArrival = ist.time24,
                actualDeparture = ist.time24,
                observedDelayMinutes = actualDelay,
                actualDwellMinutes = 2,
                actualPlatformUsed = (1..3).random(),
                recordedAtEpoch = nowEpoch
            )
            database.recordActualMovement(movement)
            broadcastHub.broadcastActualMovement(
                movementId = movement.movementId,
                runId = movement.runId,
                trainNumber = trainNum,
                stationCode = movement.stationCode,
                stationName = stnName,
                actualArrival = movement.actualArrival,
                actualDeparture = movement.actualDeparture,
                observedDelayMinutes = movement.observedDelayMinutes,
                platformUsed = movement.actualPlatformUsed,
                timestampEpoch = movement.recordedAtEpoch
            )

            // At bottleneck stations, emit a delay event with root cause explanation
            if (stnCode in listOf("KSRA", "IGP", "NK")) {
                val (category, reason) = when (stnCode) {
                    "KSRA" -> "BANKER_ATTACHMENT" to "Twin WAG-9 banking engines coupled for 1:37 Thal Ghat gradient ascent"
                    "IGP" -> "THAL_GHAT_BANKING" to "Banker detachment complete at Igatpuri yard; downline headway regulation"
                    else -> "HEADWAY_SEPARATION" to "Cautious signal aspect on approach to Nashik Road Pf 2 due to downstream freight rake clearance"
                }
                val delayEvent = org.example.project.db.DelayEventEntity(
                    eventId = ++eventCounter,
                    runId = runId,
                    stationCode = stnCode,
                    delayDurationMinutes = (actualDelay - 5).coerceAtLeast(4),
                    primaryReasonCategory = category,
                    detailedExplanation = reason,
                    eventTimestamp = nowEpoch
                )
                database.insertDelayEvent(delayEvent)
                broadcastHub.broadcastDelayEvent(
                    eventId = delayEvent.eventId,
                    runId = delayEvent.runId,
                    trainNumber = trainNum,
                    stationCode = delayEvent.stationCode,
                    delayDurationMinutes = delayEvent.delayDurationMinutes,
                    primaryReasonCategory = delayEvent.primaryReasonCategory,
                    detailedExplanation = delayEvent.detailedExplanation,
                    timestampEpoch = delayEvent.eventTimestamp
                )
            }

            stopIndex++
        }
    }

    routing {
        // --- CLEAN REST APIS (Specification Endpoints) ---
        trainRoutes(database, broadcastHub = broadcastHub)
        stationRoutes(database)
        predictionRoutes(database)
        scenarioRoutes(database)
        modelPerformanceRoutes(database)
        nashikGoogleMapRoute()

        // Global Alerts Listing
        get("/api/alerts") {
            val list = database.getActiveAlerts()
            val json = buildString {
                append("[")
                list.forEachIndexed { i, a ->
                    if (i > 0) append(",")
                    append("""{"id":${a.alertId},"station":"${a.stationCode ?: "CORRIDOR"}","type":"${a.alertType}","severity":"${a.severity}","message":"${a.message}","isResolved":${a.isResolved}}""")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // Service Health & Metadata
        get("/") {
            val activeModel = ingestionService.getActivePredictor()
            val nowIst = IndianStandardTime.fromEpochMillis(System.currentTimeMillis())
            call.respondText(
                """{"app":"RailNexus API","version":"2.0.0","cityHub":"Nashik (NK)","status":"RUNNING","istTime":"${nowIst.fullFormatted}","activeModel":"${activeModel.modelArchitecture.displayName}","activeModelVersion":"${activeModel.modelVersion}","connectedClients":${broadcastHub.getActiveConnectionCount()}}""",
                ContentType.Application.Json
            )
        }

        // --- DATABASE & RELATIONAL API ROUTES ---
        route("/api/db") {
            // Serve Full ANSI SQL Schema DDL Script
            get("/schema") {
                val schemaStream = this::class.java.classLoader.getResourceAsStream("schema.sql")
                val ddl = schemaStream?.bufferedReader()?.use { it.readText() } ?: "-- schema.sql not found"
                call.respondText(ddl, ContentType.Text.Plain)
            }

            // Get Master Stations
            get("/stations") {
                val list = database.getAllStations()
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, s ->
                        if (i > 0) append(",")
                        append("""{"code":"${s.stationCode}","name":"${s.stationName}","lat":${s.latitude},"lng":${s.longitude},"division":"${s.division}","isJunction":${s.isJunction},"platforms":${s.totalPlatforms}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get Master Trains
            get("/trains") {
                val list = database.getAllTrains()
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, t ->
                        if (i > 0) append(",")
                        append("""{"number":"${t.trainNumber}","name":"${t.trainName}","type":"${t.trainType}","source":"${t.sourceStationCode}","destination":"${t.destinationStationCode}","dep":"${t.scheduledDeparture}","arr":"${t.scheduledArrival}","distanceKm":${t.totalDistanceKm},"priority":${t.priorityScore}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get Schedule for a Train
            get("/schedules/{trainNumber}") {
                val trainNum = call.parameters["trainNumber"] ?: "12109"
                val list = database.getScheduleForTrain(trainNum)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, s ->
                        if (i > 0) append(",")
                        append("""{"seq":${s.stopSequence},"station":"${s.stationCode}","arr":"${s.scheduledArrival}","dep":"${s.scheduledDeparture}","distKm":${s.distanceFromSourceKm},"slackMin":${s.sectionSlackMinutes},"platform":${s.platformNumber}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get Active Alerts
            get("/alerts") {
                val list = database.getActiveAlerts()
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, a ->
                        if (i > 0) append(",")
                        append("""{"id":${a.alertId},"station":"${a.stationCode ?: "CORRIDOR"}","type":"${a.alertType}","severity":"${a.severity}","message":"${a.message}","isResolved":${a.isResolved}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get Predictions for Run
            get("/predictions/{runId}") {
                val runId = call.parameters["runId"] ?: "run-12109-today"
                val list = database.getLatestPredictions(runId)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, p ->
                        if (i > 0) append(",")
                        append("""{"targetStation":"${p.targetStationCode}","eta":"${p.predictedEta}","confidence":${p.confidenceScore},"delayMin":${p.predictedDelayMinutes},"p10":${p.quantileP10Delay},"p90":${p.quantileP90Delay},"explanation":"${p.primaryExplanation}"}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get What-If Scenarios
            get("/what-if/{runId}") {
                val runId = call.parameters["runId"] ?: "run-12109-today"
                val list = database.getWhatIfScenariosForRun(runId)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, w ->
                        if (i > 0) append(",")
                        append("""{"scenarioId":"${w.scenarioId}","station":"${w.regulationStationCode}","holdMin":${w.hypotheticalDelayMinutes},"savedMin":${w.corridorMinutesSaved},"safetyStatus":"${w.safetyAuditStatus}","action":"${w.controllerAction}"}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get Recorded Actual Movements
            get("/movements/{runId}") {
                val runId = call.parameters["runId"] ?: "run-12109-today"
                val list = database.getMovementsForRun(runId)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, m ->
                        if (i > 0) append(",")
                        append("""{"id":${m.movementId},"station":"${m.stationCode}","arr":"${m.actualArrival}","dep":"${m.actualDeparture}","delayMin":${m.observedDelayMinutes},"platform":${m.actualPlatformUsed},"epoch":${m.recordedAtEpoch}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Ingest & Broadcast Actual Movement
            post("/movement") {
                val runId = call.request.queryParameters["runId"] ?: "run-12109-today"
                val trainNum = call.request.queryParameters["trainNumber"] ?: "12109"
                val stnCode = call.request.queryParameters["stationCode"] ?: "NK"
                val stnName = database.getAllStations().find { it.stationCode == stnCode }?.stationName ?: stnCode
                val delayMin = call.request.queryParameters["delayMinutes"]?.toIntOrNull() ?: 18
                val pf = call.request.queryParameters["platform"]?.toIntOrNull() ?: 2
                val nowEpoch = System.currentTimeMillis()
                val ist = IndianStandardTime.fromEpochMillis(nowEpoch)

                val movement = org.example.project.db.ActualMovementEntity(
                    movementId = System.currentTimeMillis(),
                    runId = runId,
                    stationCode = stnCode,
                    actualArrival = ist.time24,
                    actualDeparture = ist.time24,
                    observedDelayMinutes = delayMin,
                    actualDwellMinutes = 2,
                    actualPlatformUsed = pf,
                    recordedAtEpoch = nowEpoch
                )
                database.recordActualMovement(movement)
                broadcastHub.broadcastActualMovement(
                    movementId = movement.movementId,
                    runId = movement.runId,
                    trainNumber = trainNum,
                    stationCode = movement.stationCode,
                    stationName = stnName,
                    actualArrival = movement.actualArrival,
                    actualDeparture = movement.actualDeparture,
                    observedDelayMinutes = movement.observedDelayMinutes,
                    platformUsed = movement.actualPlatformUsed,
                    timestampEpoch = movement.recordedAtEpoch
                )
                call.respondText("""{"status":"RECORDED_AND_BROADCAST","movementId":${movement.movementId}}""", ContentType.Application.Json)
            }

            // Get Recorded Delay Events
            get("/delay-events/{runId}") {
                val runId = call.parameters["runId"] ?: "run-12109-today"
                val list = database.getDelayEvents(runId)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, d ->
                        if (i > 0) append(",")
                        append("""{"id":${d.eventId},"station":"${d.stationCode}","delayMin":${d.delayDurationMinutes},"category":"${d.primaryReasonCategory}","explanation":"${d.detailedExplanation}","epoch":${d.eventTimestamp}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Ingest & Broadcast Delay Event
            post("/delay-event") {
                val runId = call.request.queryParameters["runId"] ?: "run-12109-today"
                val trainNum = call.request.queryParameters["trainNumber"] ?: "12109"
                val stnCode = call.request.queryParameters["stationCode"] ?: "IGP"
                val delayMin = call.request.queryParameters["delayMinutes"]?.toIntOrNull() ?: 11
                val category = call.request.queryParameters["category"] ?: "THAL_GHAT_BANKING"
                val explanation = call.request.queryParameters["explanation"] ?: "Banker detachment and headway regulation in Igatpuri yard"
                val nowEpoch = System.currentTimeMillis()

                val delayEvent = org.example.project.db.DelayEventEntity(
                    eventId = System.currentTimeMillis(),
                    runId = runId,
                    stationCode = stnCode,
                    delayDurationMinutes = delayMin,
                    primaryReasonCategory = category,
                    detailedExplanation = explanation,
                    eventTimestamp = nowEpoch
                )
                database.insertDelayEvent(delayEvent)
                broadcastHub.broadcastDelayEvent(
                    eventId = delayEvent.eventId,
                    runId = delayEvent.runId,
                    trainNumber = trainNum,
                    stationCode = delayEvent.stationCode,
                    delayDurationMinutes = delayEvent.delayDurationMinutes,
                    primaryReasonCategory = delayEvent.primaryReasonCategory,
                    detailedExplanation = delayEvent.detailedExplanation,
                    timestampEpoch = delayEvent.eventTimestamp
                )
                call.respondText("""{"status":"RECORDED_AND_BROADCAST","eventId":${delayEvent.eventId}}""", ContentType.Application.Json)
            }
        }

        // --- REAL TRAIN TRACKER & HISTORICAL ARCHIVE ROUTES ---
        route("/api/real-trains") {
            // Search or list real trains
            get("/search") {
                val query = call.request.queryParameters["q"] ?: ""
                val list = org.example.project.data.RealTrainRecordRegistry.searchTrains(query)
                val json = buildString {
                    append("[")
                    list.forEachIndexed { i, t ->
                        if (i > 0) append(",")
                        append("""{"trainNumber":"${t.trainNumber}","trainName":"${t.trainName}","type":"${t.trainType.name}","source":"${t.source.code}","destination":"${t.destination.code}","zone":"${t.operatingZone}","distanceKm":${t.totalDistanceKm},"avgSpeed":${t.averageSpeedKmh},"mps":${t.maxPermissibleSpeedKmh},"loco":"${t.locomotiveClass}","shed":"${t.locomotiveShed}","rake":"${t.rakeType}","punctuality":${t.onTimePunctuality30Days}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get train profile with station timetable
            get("/{trainNumber}") {
                val trainNumber = call.parameters["trainNumber"] ?: "12109"
                val train = org.example.project.data.RealTrainRecordRegistry.getTrainByNumber(trainNumber)
                if (train == null) {
                    call.respondText("""{"error":"Train not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@get
                }
                val json = buildString {
                    append("""{"trainNumber":"${train.trainNumber}","trainName":"${train.trainName}","type":"${train.trainType.name}","sourceCode":"${train.source.code}","sourceName":"${train.source.name}","destCode":"${train.destination.code}","destName":"${train.destination.name}","zone":"${train.operatingZone}","distanceKm":${train.totalDistanceKm},"avgSpeed":${train.averageSpeedKmh},"mps":${train.maxPermissibleSpeedKmh},"loco":"${train.locomotiveClass}","shed":"${train.locomotiveShed}","rake":"${train.rakeType}","punctuality":${train.onTimePunctuality30Days},"stops":[""")
                    train.stops.forEachIndexed { i, s ->
                        if (i > 0) append(",")
                        append("""{"code":"${s.stationCode}","name":"${s.stationName}","pf":"${s.platform}","schArr":"${s.scheduledArrival}","schDep":"${s.scheduledDeparture}","actual":"${s.actualArrivalOrEta}","delay":${s.delayMinutes},"status":"${s.status}","distKm":${s.distanceFromSourceKm}}""")
                    }
                    append("]}")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get 30-day historical runs
            get("/{trainNumber}/history") {
                val trainNumber = call.parameters["trainNumber"] ?: "12109"
                val runs = org.example.project.engine.HistoricalPunctualityEngine.get30DayRunHistory(trainNumber)
                val json = buildString {
                    append("[")
                    runs.forEachIndexed { i, r ->
                        if (i > 0) append(",")
                        append("""{"date":"${r.runDateFormatted}","delayMin":${r.delayMinutes},"ghatDelayMin":${r.thalGhatDelayMinutes},"slackRecovered":${r.slackRecoveredMinutes},"status":"${r.status}","reason":"${r.primaryDelayReason}"}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Get live RTIS telemetry snapshot
            get("/{trainNumber}/rtis") {
                val trainNumber = call.parameters["trainNumber"] ?: "12109"
                val tick = call.request.queryParameters["tick"]?.toIntOrNull() ?: 0
                val rtis = org.example.project.engine.RtisTelemetryEngine.generateLiveTelemetry(trainNumber, tick)
                val json = """{"trainNumber":"${rtis.trainNumber}","timestamp":"${rtis.timestampFormatted}","lat":${rtis.latitude},"lon":${rtis.longitude},"speed":${rtis.currentSpeedKmh},"mps":${rtis.maxPermissibleSpeedKmh},"kmPost":"${rtis.kilometerPost}","block":"${rtis.blockSectionName}","signal":"${rtis.signalAspect.name}","signalIcon":"${rtis.signalAspect.symbol}","signalLabel":"${rtis.signalAspect.label}","notch":"${rtis.throttleNotch}","currentAmps":${rtis.tractionCurrentAmps},"bpPressure":${rtis.brakePipePressureKgCm2},"navicStatus":"${rtis.satelliteNavIcStatus}","nextStation":"${rtis.nextStationCode}","nextDistanceKm":${rtis.nextStationDistanceKm},"etaNextMin":${rtis.estimatedTimeToNextMinutes}}"""
                call.respondText(json, ContentType.Application.Json)
            }
        }

        // --- NASHIK REGIONAL HUB ROUTES ---
        route("/api/city/nashik") {
            // Station Hub Profile & Thal Ghat Status
            get("/hub") {
                val profile = NashikStationData.hubProfile
                val json = buildString {
                    append("{")
                    append("\"city\":\"${profile.city}\",")
                    append("\"state\":\"${profile.state}\",")
                    append("\"stationCode\":\"${profile.primaryStation.code}\",")
                    append("\"stationName\":\"${profile.primaryStation.name}\",")
                    append("\"zone\":\"${profile.zone}\",")
                    append("\"division\":\"${profile.division}\",")
                    append("\"platforms\":${profile.totalPlatforms},")
                    append("\"ghatSectionAlert\":\"${profile.ghatSectionAlert}\",")
                    append("\"ghatDelayVarianceMin\":${profile.ghatDelayVarianceMin},")
                    append("\"transitCount\":${profile.transitServices.size}")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Live Station Arrival/Departure Board
            get("/live-board") {
                val trains = NashikStationData.liveStationTrains
                val json = buildString {
                    append("[")
                    trains.forEachIndexed { index, train ->
                        if (index > 0) append(",")
                        append("{")
                        append("\"trainNumber\":\"${train.trainNumber}\",")
                        append("\"trainName\":\"${train.trainName}\",")
                        append("\"origin\":\"${train.origin.name}\",")
                        append("\"destination\":\"${train.destination.name}\",")
                        append("\"scheduledArrival\":\"${train.scheduledArrival}\",")
                        append("\"scheduledDeparture\":\"${train.scheduledDeparture}\",")
                        append("\"predictedArrival\":\"${train.predictedArrivalEta}\",")
                        append("\"predictedDeparture\":\"${train.predictedDepartureEta}\",")
                        append("\"platform\":\"${train.platformNumber}\",")
                        append("\"delayMinutes\":${train.delayMinutes},")
                        append("\"status\":\"${train.status.label}\",")
                        append("\"delayReason\":\"${train.delayReason}\"")
                        append("}")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Live info for a specific train passing Nashik
            get("/trains/{trainNumber}") {
                val number = call.parameters["trainNumber"]
                val train = NashikStationData.liveStationTrains.find { it.trainNumber == number }
                if (train != null) {
                    val json = buildString {
                        append("{")
                        append("\"trainNumber\":\"${train.trainNumber}\",")
                        append("\"trainName\":\"${train.trainName}\",")
                        append("\"platform\":\"${train.platformNumber}\",")
                        append("\"scheduledArrival\":\"${train.scheduledArrival}\",")
                        append("\"predictedArrival\":\"${train.predictedArrivalEta}\",")
                        append("\"delayMinutes\":${train.delayMinutes},")
                        append("\"status\":\"${train.status.label}\",")
                        append("\"delayReason\":\"${train.delayReason}\"")
                        append("}")
                    }
                    call.respondText(json, ContentType.Application.Json)
                } else {
                    call.respondText(
                        """{"error":"Train not found passing Nashik Road"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound
                    )
                }
            }
        }

        // --- PHASE 2 TELEMETRY INGESTION & PIPELINE ROUTES ---
        route("/api/telemetry") {
            // High-throughput Telemetry Ingestion Endpoint
            post("/ingest") {
                val rawBody = call.receiveText()
                val now = System.currentTimeMillis()
                val snapshot = parseTelemetryJson(rawBody, now)

                if (snapshot == null) {
                    call.respondText(
                        """{"error":"Invalid JSON payload or missing required telemetry fields"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val validation = ingestionService.validate(snapshot, now)
                if (validation is ValidationResult.Invalid) {
                    call.respondText(
                        """{"error":"Telemetry rejected","reason":"${validation.reason}","trainNumber":"${snapshot.trainNumber}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.UnprocessableEntity
                    )
                    return@post
                }

                // Process through feature engineering -> ML predictor -> confidence -> alert engine
                val result = ingestionService.ingestAndProcess(snapshot, now)
                if (result != null) {
                    // Broadcast real-time updates over WebSocket and SSE
                    broadcastHub.broadcastTelemetry(result)
                    result.alerts.forEach { alert ->
                        broadcastHub.broadcastAlert(alert)
                    }

                    val responseJson = buildString {
                        append("{")
                        append("\"status\":\"ACCEPTED\",")
                        append("\"trainNumber\":\"${result.snapshot.trainNumber}\",")
                        append("\"predictedEta\":\"${result.prediction.predictedEtaHHmm}\",")
                        append("\"predictedDelayMinutes\":${result.prediction.predictedDelayMinutes},")
                        append("\"p10LowerMinutes\":${result.prediction.quantileLowerMinutes},")
                        append("\"p90UpperMinutes\":${result.prediction.quantileUpperMinutes},")
                        append("\"confidencePercent\":${result.confidence.scorePercent},")
                        append("\"confidenceLevel\":\"${result.confidence.level.label}\",")
                        append("\"modelArchitecture\":\"${result.prediction.modelArchitecture.displayName}\",")
                        append("\"modelVersion\":\"${result.prediction.modelVersion}\",")
                        append("\"explanations\":[${result.explanations.joinToString(",") { "\"$it\"" }}],")
                        append("\"alertsCount\":${result.alerts.size}")
                        append("}")
                    }
                    call.respondText(responseJson, ContentType.Application.Json)
                } else {
                    call.respondText(
                        """{"error":"Processing failed during pipeline execution"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // Query latest telemetry for a specific train
            get("/latest/{trainNumber}") {
                val trainNumber = call.parameters["trainNumber"] ?: ""
                val snapshot = ingestionService.getLatestTelemetry(trainNumber)
                if (snapshot != null) {
                    val json = buildString {
                        append("{")
                        append("\"trainNumber\":\"${snapshot.trainNumber}\",")
                        append("\"latitude\":${snapshot.latitude},")
                        append("\"longitude\":${snapshot.longitude},")
                        append("\"speedKmH\":${snapshot.speedKmH},")
                        append("\"heading\":${snapshot.headingDegrees},")
                        append("\"currentStopIndex\":${snapshot.currentStopIndex},")
                        append("\"currentDelayMinutes\":${snapshot.currentDelayMinutes},")
                        append("\"timestampEpochMillis\":${snapshot.timestampEpochMillis}")
                        append("}")
                    }
                    call.respondText(json, ContentType.Application.Json)
                } else {
                    call.respondText(
                        """{"error":"No telemetry recorded for train $trainNumber"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound
                    )
                }
            }

            // List active trains in cache
            get("/active-trains") {
                val trains = ingestionService.getAllActiveTrainNumbers()
                val json = "[${trains.joinToString(",") { "\"$it\"" }}]"
                call.respondText(json, ContentType.Application.Json)
            }

            // Dead-Letter Queue for inspection
            get("/dlq") {
                val dlq = ingestionService.getDeadLetterQueue()
                val json = buildString {
                    append("[")
                    dlq.forEachIndexed { i, inv ->
                        if (i > 0) append(",")
                        append("{\"trainNumber\":\"${inv.rejectedSnapshot.trainNumber}\",\"reason\":\"${inv.reason}\"}")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }
        }

        // --- MODEL MANAGEMENT & HOT-SWAPPING ROUTES ---
        route("/api/model") {
            // Get current active model
            get("/current") {
                val active = ingestionService.getActivePredictor()
                val json = buildString {
                    append("{")
                    append("\"architecture\":\"${active.modelArchitecture.name}\",")
                    append("\"displayName\":\"${active.modelArchitecture.displayName}\",")
                    append("\"version\":\"${active.modelVersion}\"")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Hot-swap the active predictor
            post("/switch") {
                val modelParam = call.request.queryParameters["model"]?.uppercase() ?: "QUANTILE"
                val newPredictor = when {
                    modelParam.contains("HEURISTIC") || modelParam.contains("BASELINE") -> HeuristicEtaPredictor()
                    else -> QuantileEtaPredictor()
                }
                ingestionService.switchPredictor(newPredictor)
                corridorSimulator.switchPredictor(newPredictor)
                val json = buildString {
                    append("{")
                    append("\"status\":\"SWITCHED\",")
                    append("\"newArchitecture\":\"${newPredictor.modelArchitecture.name}\",")
                    append("\"displayName\":\"${newPredictor.modelArchitecture.displayName}\",")
                    append("\"version\":\"${newPredictor.modelVersion}\"")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }
        }

        // --- NETWORK & TOPOLOGY ROUTES ---
        route("/api/network") {
            // Precedence arbitration between two trains on a single-line block
            get("/arbitrate") {
                val trainA = call.request.queryParameters["trainA"] ?: "22223"
                val trainB = call.request.queryParameters["trainB"] ?: "12424"
                val block = TrackBlockOccupancy(
                    blockId = "BLOCK_KSRA_IGP",
                    blockName = "Kasara - Igatpuri Thal Ghat Ghat Section",
                    isSingleTrack = true,
                    currentTrainNumber = trainA
                )
                val journeyA = NashikStationData.trainVandeBharat22223
                val journeyB = SampleRailwayData.allSampleTrains.find { it.trainNumber == trainB }
                    ?: SampleRailwayData.train12424

                val resolution = NetworkAwareAnalysisEngine.resolveCrossingPrecedence(
                    trainA = journeyA,
                    trainB = journeyB,
                    conflictBlock = block,
                    crossingStationCode = "KSRA"
                )
                val json = buildString {
                    append("{")
                    append("\"block\":\"${block.blockName}\",")
                    append("\"higherPriorityTrain\":\"${resolution.higherPriorityTrainNumber}\",")
                    append("\"lowerPriorityTrain\":\"${resolution.lowerPriorityTrainNumber}\",")
                    append("\"crossingStationCode\":\"${resolution.crossingStationCode}\",")
                    append("\"penaltyMinutes\":${resolution.lowerPriorityDelayPenaltyMinutes},")
                    append("\"reason\":\"${resolution.reason}\"")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }
        }

        // --- PHASE 4: CORRIDOR SIMULATOR & WHAT-IF CONTINGENCY DISPATCH ROUTES ---
        route("/api/simulator") {
            // Full multi-train corridor snapshot
            get("/corridor-state") {
                val now = System.currentTimeMillis()
                val snapshot = corridorSimulator.computeCorridorSnapshot(now)
                val json = buildString {
                    append("{")
                    append("\"timestampEpochMillis\":${snapshot.timestampEpochMillis},")
                    append("\"istTime\":\"${snapshot.istTime}\",")
                    append("\"systemAlert\":${snapshot.systemAlert?.let { "\"$it\"" } ?: "null"},")
                    append("\"activeContingenciesCount\":${snapshot.activeContingencies.size},")
                    append("\"activeTrains\":[")
                    snapshot.activeTrains.forEachIndexed { i, t ->
                        if (i > 0) append(",")
                        append("{")
                        append("\"trainNumber\":\"${t.trainNumber}\",")
                        append("\"trainName\":\"${t.trainName}\",")
                        append("\"trainType\":\"${t.trainType.name}\",")
                        append("\"speedKmH\":${t.currentSpeedKmH},")
                        append("\"latitude\":${t.latitude},")
                        append("\"longitude\":${t.longitude},")
                        append("\"currentSection\":\"${t.currentSection}\",")
                        append("\"nextStation\":\"${t.nextStationCode}\",")
                        append("\"distToNashikKm\":${t.distanceRemainingToNashikKm},")
                        append("\"scheduledAtNashik\":\"${t.scheduledArrivalAtNashik}\",")
                        append("\"currentDelay\":${t.currentDelayMinutes},")
                        append("\"predictedDelay\":${t.predictedDelayMinutes},")
                        append("\"predictedEtaAtNashik\":\"${t.predictedEtaAtNashik}\",")
                        append("\"platform\":\"${t.assignedPlatform}\",")
                        append("\"isHeld\":${t.isHeldForPrecedence},")
                        append("\"operationalNote\":\"${t.operationalNote}\"")
                        append("}")
                    }
                    append("],")
                    append("\"corridorBlocks\":[")
                    snapshot.corridorBlocks.forEachIndexed { i, b ->
                        if (i > 0) append(",")
                        append("{")
                        append("\"blockId\":\"${b.blockId}\",")
                        append("\"name\":\"${b.blockName}\",")
                        append("\"lengthKm\":${b.lengthKm},")
                        append("\"mps\":${b.mpsKmph},")
                        append("\"isGhat\":${b.isGhatSection},")
                        append("\"isSingle\":${b.isSingleLine},")
                        append("\"cautionActive\":${b.cautionOrderActive},")
                        append("\"speedRestriction\":${b.speedRestrictionKmph ?: "null"}")
                        append("}")
                    }
                    append("],")
                    append("\"platformOccupancy\":{")
                    snapshot.platformOccupancy.entries.forEachIndexed { i, (pf, train) ->
                        if (i > 0) append(",")
                        append("\"$pf\":${train?.let { "\"$it\"" } ?: "null"}")
                    }
                    append("}")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // Inject What-If Contingency
            post("/inject") {
                val trainNum = call.request.queryParameters["trainNumber"] ?: "12109"
                val blockId = call.request.queryParameters["blockId"] ?: "BLOCK_KSRA_IGP"
                val delayMins = call.request.queryParameters["delayMinutes"]?.toIntOrNull() ?: 15
                val title = call.request.queryParameters["title"] ?: "Thal Ghat Delay Contingency"
                val desc = call.request.queryParameters["description"] ?: "Delay injected by Station Master"
                val speed = call.request.queryParameters["speedLimit"]?.toIntOrNull()
                val isBlocked = call.request.queryParameters["blocked"]?.toBoolean() ?: false

                val event = ContingencyEvent(
                    id = "CONTINGENCY_${System.currentTimeMillis()}",
                    title = title,
                    description = desc,
                    affectedBlockId = blockId,
                    affectedTrainNumber = trainNum,
                    delayPenaltyMinutes = delayMins,
                    isTrackBlocked = isBlocked,
                    speedLimitKmph = speed
                )
                corridorSimulator.injectContingency(event)
                broadcastHub.broadcastAlert(
                    RailAlert(
                        alertId = event.id,
                        trainNumber = trainNum,
                        type = AlertType.DELAY_SPIKE,
                        severity = AlertSeverity.WARNING,
                        title = "What-If Contingency: $title",
                        message = "WHAT-IF: $title (+$delayMins min) on $blockId",
                        timestampEpochMillis = System.currentTimeMillis()
                    )
                )
                call.respondText(
                    """{"status":"INJECTED","id":"${event.id}","activeCount":${corridorSimulator.getActiveContingencies().size}}""",
                    ContentType.Application.Json
                )
            }

            // Reset What-If Contingencies
            post("/reset") {
                corridorSimulator.clearAllContingencies()
                call.respondText("""{"status":"RESET_TO_BASELINE","activeCount":0}""", ContentType.Application.Json)
            }
        }

        // --- PHASE 4: HISTORICAL PUNCTUALITY & SLACK ANALYTICS ---
        route("/api/analytics") {
            get("/punctuality") {
                val div = call.request.queryParameters["division"] ?: "Bhusawal (BSL)"
                val report = org.example.project.engine.HistoricalPunctualityEngine.generatePunctualityReport(div)
                val json = buildString {
                    append("{")
                    append("\"division\":\"${report.division}\",")
                    append("\"period\":\"${report.period}\",")
                    append("\"totalRunsRecorded\":${report.totalRunsRecorded},")
                    append("\"onTimeRuns\":${report.onTimeRuns},")
                    append("\"onTimePercentage\":${report.onTimePercentage},")
                    append("\"minorDelayPercentage\":${report.minorDelayPercentage},")
                    append("\"severeDelayPercentage\":${report.severeDelayPercentage},")
                    append("\"averageDelayMinutes\":${report.averageDelayMinutes},")
                    append("\"thalGhatAverageDelayMinutes\":${report.thalGhatAverageDelayMinutes},")
                    append("\"sectionSlackAbsorptionAvgMinutes\":${report.sectionSlackAbsorptionAvgMinutes},")
                    append("\"keyBottleneckSection\":\"${report.keyBottleneckSection}\"")
                    append("}")
                }
                call.respondText(json, ContentType.Application.Json)
            }
        }

        // --- REAL-TIME STREAMING: WEBSOCKET ---
        webSocket("/ws/live-stream") {
            broadcastHub.registerSession(this)
            try {
                val nowIst = IndianStandardTime.fromEpochMillis(System.currentTimeMillis())
                val currentModel = ingestionService.getActivePredictor()
                send(
                    Frame.Text(
                        """{"type":"CONNECTED","time":"${nowIst.fullFormatted}","activeModel":"${currentModel.modelArchitecture.displayName}"}"""
                    )
                )

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.contains("PING", ignoreCase = true)) {
                            send(Frame.Text("""{"type":"PONG","epochMillis":${System.currentTimeMillis()}}"""))
                        }
                    }
                }
            } finally {
                broadcastHub.unregisterSession(this)
            }
        }

        // --- REAL-TIME STREAMING: SERVER-SENT EVENTS (SSE) ---
        sse("/api/stream/events") {
            val currentModel = ingestionService.getActivePredictor()
            send(
                ServerSentEvent(
                    data = """{"type":"SSE_CONNECTED","activeModel":"${currentModel.modelArchitecture.displayName}"}"""
                )
            )
            broadcastHub.eventFlow.collect { eventPayload ->
                send(ServerSentEvent(data = eventPayload))
            }
        }
    }
}

/**
 * Lightweight JSON parser for telemetry snapshots.
 */
private fun parseTelemetryJson(json: String, defaultEpoch: Long): TrainTelemetrySnapshot? {
    fun extractString(key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)
    }
    fun extractNumber(key: String): Double? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9.-]+)")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    val trainNum = extractString("trainNumber") ?: return null
    val lat = extractNumber("latitude") ?: return null
    val lon = extractNumber("longitude") ?: return null
    val speed = extractNumber("speedKmH") ?: return null
    val heading = extractNumber("headingDegrees") ?: 0.0
    val stopIdx = extractNumber("currentStopIndex")?.toInt() ?: 0
    val delayMins = extractNumber("currentDelayMinutes")?.toInt() ?: 0
    val timestamp = extractNumber("timestampEpochMillis")?.toLong() ?: defaultEpoch

    return TrainTelemetrySnapshot(
        trainNumber = trainNum,
        latitude = lat,
        longitude = lon,
        speedKmH = speed,
        headingDegrees = heading,
        currentStopIndex = stopIdx,
        currentDelayMinutes = delayMins,
        timestampEpochMillis = timestamp
    )
}