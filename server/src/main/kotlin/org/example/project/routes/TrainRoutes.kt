package org.example.project.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.project.data.NashikStationData
import org.example.project.db.RailNexusDatabase
import org.example.project.engine.ContinuousRecalibrationEngine
import org.example.project.engine.DelayPropagationEngine
import org.example.project.engine.NetworkAwareForecastingEngine
import org.example.project.model.*
import org.example.project.service.LiveStreamBroadcastHub

/**
 * Train Management, Route, Live Telemetry & ETA Cascade Routes.
 * 
 * Implements:
 * - GET /api/trains (?q=search)
 * - GET /api/trains/{id}
 * - GET /api/trains/{id}/live
 * - GET /api/trains/{id}/route
 * - GET /api/trains/{id}/eta (?stopIndex=..&delayMin=..)
 * - GET /api/trains/{id}/delay-propagation
 * - GET /api/trains/{id}/network-forecast
 * - GET /api/trains/{id}/recalibration
 * - POST /api/trains/{id}/recalibrate
 * - GET /api/trains/{id}/alerts
 * - GET /api/trains/{id}/history
 */
fun Route.trainRoutes(
    database: RailNexusDatabase,
    broadcastHub: LiveStreamBroadcastHub? = null
) {
    route("/api/trains") {
        // 1. Train Search & Directory Listing
        get {
            val query = call.request.queryParameters["q"]?.trim()?.lowercase() ?: ""
            val allTrains = database.getAllTrains()
            val filtered = if (query.isEmpty()) {
                allTrains
            } else {
                allTrains.filter {
                    it.trainNumber.lowercase().contains(query) ||
                    it.trainName.lowercase().contains(query) ||
                    it.sourceStationCode.lowercase().contains(query) ||
                    it.destinationStationCode.lowercase().contains(query)
                }
            }

            val json = buildString {
                append("[")
                filtered.forEachIndexed { index, t ->
                    if (index > 0) append(",")
                    append("{")
                    append("\"trainNumber\":\"${t.trainNumber}\",")
                    append("\"trainName\":\"${t.trainName}\",")
                    append("\"trainType\":\"${t.trainType}\",")
                    append("\"source\":\"${t.sourceStationCode}\",")
                    append("\"destination\":\"${t.destinationStationCode}\",")
                    append("\"scheduledDeparture\":\"${t.scheduledDeparture}\",")
                    append("\"scheduledArrival\":\"${t.scheduledArrival}\",")
                    append("\"distanceKm\":${t.totalDistanceKm},")
                    append("\"priorityScore\":${t.priorityScore}")
                    append("}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 2. Train Details & Timetable
        get("/{id}") {
            val trainId = call.parameters["id"] ?: ""
            val train = database.getTrain(trainId)
            if (train == null) {
                call.respondText(
                    """{"error":"Train not found with number: $trainId","statusCode":404}""",
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound
                )
                return@get
            }

            val schedule = database.getScheduleForTrain(trainId)
            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${train.trainNumber}\",")
                append("\"trainName\":\"${train.trainName}\",")
                append("\"trainType\":\"${train.trainType}\",")
                append("\"source\":\"${train.sourceStationCode}\",")
                append("\"destination\":\"${train.destinationStationCode}\",")
                append("\"scheduledDeparture\":\"${train.scheduledDeparture}\",")
                append("\"scheduledArrival\":\"${train.scheduledArrival}\",")
                append("\"distanceKm\":${train.totalDistanceKm},")
                append("\"priorityScore\":${train.priorityScore},")
                append("\"schedule\":[")
                schedule.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"sequence\":${s.stopSequence},")
                    append("\"stationCode\":\"${s.stationCode}\",")
                    append("\"scheduledArrival\":\"${s.scheduledArrival}\",")
                    append("\"scheduledDeparture\":\"${s.scheduledDeparture}\",")
                    append("\"distanceKm\":${s.distanceFromSourceKm},")
                    append("\"slackMinutes\":${s.sectionSlackMinutes},")
                    append("\"platform\":${s.platformNumber}")
                    append("}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 3. Live Train Position & Operational State
        get("/{id}/live") {
            val trainId = call.parameters["id"] ?: ""
            val runId = "run-$trainId-today"
            val position = database.getLatestPosition(runId)
            val run = database.getTrainRun(runId)

            val speed = position?.speedKmph ?: 102.0
            val block = position?.currentSegmentBlock ?: "DVL-NK Block Section 1"
            val lat = position?.latitude ?: 19.9320
            val lon = position?.longitude ?: 73.8390
            val delayMinutes = run?.currentDelayMinutes ?: 18
            val status = run?.operationalStatus ?: "IN_TRANSIT"

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"$trainId\",")
                append("\"runId\":\"$runId\",")
                append("\"operationalStatus\":\"$status\",")
                append("\"currentBlockSection\":\"$block\",")
                append("\"latitude\":$lat,")
                append("\"longitude\":$lon,")
                append("\"speedKmph\":$speed,")
                append("\"observedDelayMinutes\":$delayMinutes,")
                append("\"lastClearedStation\":\"DVL\",")
                append("\"approachingStation\":\"NK\",")
                append("\"telemetryTimestamp\":${position?.telemetryTimestamp ?: System.currentTimeMillis()}")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 4. Route Information (Stops, Distances, Coordinates)
        get("/{id}/route") {
            val trainId = call.parameters["id"] ?: ""
            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${journey.trainNumber}\",")
                append("\"trainName\":\"${journey.trainName}\",")
                append("\"totalStops\":${journey.stops.size},")
                append("\"route\":[")
                journey.stops.forEachIndexed { i, stop ->
                    val stn = database.getAllStations().find { it.stationCode == stop.station.code }
                    val lat = stn?.latitude ?: 19.9548
                    val lon = stn?.longitude ?: 73.8437
                    if (i > 0) append(",")
                    append("{")
                    append("\"stopIndex\":$i,")
                    append("\"stationCode\":\"${stop.station.code}\",")
                    append("\"stationName\":\"${stop.station.name}\",")
                    append("\"latitude\":$lat,")
                    append("\"longitude\":$lon,")
                    append("\"scheduledArrival\":\"${stop.scheduledArrival}\",")
                    append("\"scheduledDeparture\":\"${stop.scheduledDeparture}\",")
                    append("\"distanceFromOriginKm\":${stop.distanceKm},")
                    append("\"platform\":${if (stop.station.code == "NK") 2 else 1}")
                    append("}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 5. Station-wise Predicted ETA Matrix with Quantile Spread
        get("/{id}/eta") {
            val trainId = call.parameters["id"] ?: ""
            val stopIndex = call.request.queryParameters["stopIndex"]?.toIntOrNull() ?: 1
            val delayMin = call.request.queryParameters["delayMin"]?.toIntOrNull() ?: 18

            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            // Call Core Delay Propagation Engine (Separation of ML logic)
            val predictions = DelayPropagationEngine.predictJourney(
                journey = journey,
                currentStopIndex = stopIndex.coerceIn(0, journey.stops.size - 1),
                currentDelayMinutes = delayMin
            )

            val nextStopIdx = (stopIndex + 1).coerceAtMost(journey.stops.size - 1)
            val nextPred = predictions.getOrNull(nextStopIdx)
            val finalPred = predictions.lastOrNull()

            val confidenceScore = (95 - (delayMin * 0.4).toInt()).coerceIn(60, 96)
            val confidenceLevel = if (confidenceScore >= 85) "HIGH" else "MEDIUM"

            val primaryExplanation = if (delayMin > 15) {
                "Current delay increased and downstream congestion is detected."
            } else {
                "Normal corridor running with steady sectional speed."
            }

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${journey.trainNumber}\",")
                append("\"currentStopIndex\":$stopIndex,")
                append("\"currentDelayMinutes\":$delayMin,")
                append("\"nextStationDelayMinutes\":${nextPred?.delayMinutes ?: (delayMin + 3)},")
                append("\"finalImpactDelayMinutes\":${finalPred?.delayMinutes ?: (delayMin + 9)},")
                append("\"confidenceScore\":$confidenceScore,")
                append("\"confidenceLevel\":\"$confidenceLevel\",")
                append("\"primaryExplanation\":\"$primaryExplanation\",")
                append("\"stationPredictions\":[")
                predictions.forEachIndexed { i, p ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"stopIndex\":$i,")
                    append("\"stationCode\":\"${p.stop.station.code}\",")
                    append("\"stationName\":\"${p.stop.station.name}\",")
                    append("\"scheduledArrival\":\"${p.stop.scheduledArrival}\",")
                    append("\"predictedEta\":\"${p.predictedEta}\",")
                    append("\"predictedDelayMinutes\":${p.delayMinutes},")
                    append("\"p10LowerDelta\":${(p.delayMinutes - 2).coerceAtLeast(0)},")
                    append("\"p90UpperDelta\":${p.delayMinutes + 2},")
                    append("\"status\":\"${p.cascadeRisk.label}\"")
                    append("}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 6. Delay Propagation Analysis
        get("/{id}/delay-propagation") {
            val trainId = call.parameters["id"] ?: ""
            val delayMin = call.request.queryParameters["delayMin"]?.toIntOrNull() ?: 18
            val stopIdx = call.request.queryParameters["stopIndex"]?.toIntOrNull() ?: 1
            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            val report = DelayPropagationEngine.analyzePropagation(
                journey = journey,
                currentStopIndex = stopIdx,
                observedDelayMinutes = delayMin
            )

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${report.trainNumber}\",")
                append("\"currentStationCode\":\"${report.currentStationCode}\",")
                append("\"currentStationIndex\":${report.currentStationIndex},")
                append("\"observedDelayMinutes\":${report.observedDelayMinutes},")
                append("\"initialDelayMinutes\":${report.observedDelayMinutes},")
                append("\"severity\":\"${report.severity.name}\",")
                append("\"severityLabel\":\"${report.severity.label}\",")
                append("\"confidencePercent\":${report.confidencePercent},")
                append("\"expectedAffectedStationsCount\":${report.expectedAffectedStationsCount},")
                append("\"maxProjectedDelayMinutes\":${report.maxProjectedDelayMinutes},")
                append("\"finalImpactDelayMinutes\":${report.finalImpactDelayMinutes},")
                append("\"totalBufferAbsorption\":${report.totalSlackRecoveryMinutes},")
                append("\"progressionSummary\":\"${report.progressionSummary}\",")
                append("\"stationProgressions\":[")
                report.stationProgressions.forEachIndexed { i, p ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"sequence\":${p.sequence},")
                    append("\"stationCode\":\"${p.stationCode}\",")
                    append("\"stationName\":\"${p.stationName}\",")
                    append("\"observationType\":\"${p.observationType.name}\",")
                    append("\"isObserved\":${p.observationType == org.example.project.model.DelayObservationType.OBSERVED_ACTUAL},")
                    append("\"scheduledArrival\":\"${p.scheduledArrival}\",")
                    append("\"scheduledDeparture\":\"${p.scheduledDeparture}\",")
                    append("\"predictedEta\":\"${p.predictedEta}\",")
                    append("\"delayMinutes\":${p.delayMinutes},")
                    append("\"deltaFromPreviousMinutes\":${p.deltaFromPreviousMinutes},")
                    append("\"slackRecoveryMinutes\":${p.slackRecoveryMinutes},")
                    append("\"congestionPenaltyMinutes\":${p.congestionPenaltyMinutes},")
                    append("\"dwellInflationMinutes\":${p.dwellInflationMinutes},")
                    append("\"cascadeRisk\":\"${p.cascadeRisk.name}\",")
                    append("\"explanation\":\"${p.explanation}\"")
                    append("}")
                }
                append("],")
                append("\"propagationCurve\":[")
                report.stationProgressions.forEachIndexed { i, p ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"stationCode\":\"${p.stationCode}\",")
                    append("\"delayMinutes\":${p.delayMinutes},")
                    append("\"cumulativeRecoveryMinutes\":${(report.observedDelayMinutes - p.delayMinutes).coerceAtLeast(0)}")
                    append("}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 7. Network-Aware Chained Segment & Dwell Forecast
        get("/{id}/network-forecast") {
            val trainId = call.parameters["id"] ?: ""
            val delayMin = call.request.queryParameters["delayMin"]?.toIntOrNull() ?: 18
            val stopIdx = call.request.queryParameters["stopIndex"]?.toIntOrNull() ?: 1
            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            val report = NetworkAwareForecastingEngine.forecastJourney(
                journey = journey,
                currentStopIndex = stopIdx,
                observedDelayMinutes = delayMin
            )

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${report.trainNumber}\",")
                append("\"routeId\":\"${report.routeId}\",")
                append("\"currentStationIndex\":${report.currentStationIndex},")
                append("\"currentStationCode\":\"${report.currentStationCode}\",")
                append("\"initialObservedDelayMinutes\":${report.initialObservedDelayMinutes},")
                append("\"accumulatedDelayMinutes\":${report.accumulatedDelayMinutes},")
                append("\"totalJourneyDistanceKm\":${report.totalJourneyDistanceKm},")
                append("\"totalRuntimeMinutes\":${report.totalRuntimeMinutes},")
                append("\"networkAdvantageExplanation\":\"${report.networkAdvantageExplanation}\",")
                append("\"stationForecasts\":[")
                report.stationForecasts.forEachIndexed { i, sf ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"sequence\":${sf.stationNode.sequence},")
                    append("\"stationCode\":\"${sf.stationNode.stationCode}\",")
                    append("\"stationName\":\"${sf.stationNode.stationName}\",")
                    append("\"isJunction\":${sf.stationNode.isJunction},")
                    append("\"scheduledArrivalEpoch\":${sf.scheduledArrivalEpoch},")
                    append("\"predictedArrivalEpoch\":${sf.predictedArrivalEpoch},")
                    append("\"predictedArrivalHHmm\":\"${sf.predictedArrivalHHmm}\",")
                    append("\"predictedDepartureHHmm\":\"${sf.predictedDepartureHHmm}\",")
                    append("\"arrivalDelayMinutes\":${sf.arrivalDelayMinutes},")
                    append("\"departureDelayMinutes\":${sf.departureDelayMinutes},")
                    append("\"dwellDurationMinutes\":${sf.dwellDurationMinutes},")
                    append("\"dwellInflationMinutes\":${sf.dwellInflationMinutes},")
                    append("\"dwellExplanation\":\"${sf.dwellExplanation}\"")
                    append("}")
                }
                append("],")
                append("\"segmentTransits\":[")
                report.segmentTransits.forEachIndexed { i, st ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"segmentId\":\"${st.segment.segmentId}\",")
                    append("\"routeName\":\"${st.segment.routeName}\",")
                    append("\"fromStationCode\":\"${st.segment.fromStationCode}\",")
                    append("\"toStationCode\":\"${st.segment.toStationCode}\",")
                    append("\"distanceKm\":${st.segment.distanceKm},")
                    append("\"speedLimitKmh\":${st.segment.speedLimitKmh},")
                    append("\"isSingleTrack\":${st.segment.isSingleTrack},")
                    append("\"isGhatSection\":${st.segment.isGhatSection},")
                    append("\"kinematicRuntimeMinutes\":${st.kinematicRuntimeMinutes},")
                    append("\"effectiveRuntimeMinutes\":${st.effectiveRuntimeMinutes},")
                    append("\"congestionPenaltyMinutes\":${st.congestionPenaltyMinutes},")
                    append("\"slackAbsorptionMinutes\":${st.slackAbsorptionMinutes},")
                    append("\"conflictTrain\":\"${st.precedenceConflictWithTrain ?: ""}\",")
                    append("\"transitExplanation\":\"${st.transitExplanation}\"")
                    append("}")
                }
                append("],")
                append("\"multiTrainConflicts\":[")
                report.multiTrainConflicts.forEachIndexed { i, c ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"higherPriorityTrain\":\"${c.higherPriorityTrainNumber}\",")
                    append("\"lowerPriorityTrain\":\"${c.lowerPriorityTrainNumber}\",")
                    append("\"crossingStation\":\"${c.crossingStationCode}\",")
                    append("\"penaltyMinutes\":${c.lowerPriorityDelayPenaltyMinutes},")
                    append("\"reason\":\"${c.reason}\"")
                    append("}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 8. Continuous Recalibration Report (Latest State)
        get("/{id}/recalibration") {
            val trainId = call.parameters["id"] ?: ""
            val delayMin = call.request.queryParameters["delayMin"]?.toIntOrNull() ?: 14
            val speedKmph = call.request.queryParameters["speedKmph"]?.toDoubleOrNull() ?: 78.0
            val stationCode = call.request.queryParameters["stationCode"] ?: "DVL"

            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            val update = LiveOperationalUpdate(
                updateId = "UPD-${System.currentTimeMillis()}",
                trainNumber = journey.trainNumber,
                stationCode = stationCode,
                speedKmph = speedKmph,
                observedDelayMinutes = delayMin,
                timestampEpoch = System.currentTimeMillis()
            )

            val report = ContinuousRecalibrationEngine.processLiveUpdate(update, journey)

            val json = buildString {
                append("{")
                append("\"trainNumber\":\"${report.trainNumber}\",")
                append("\"currentStationCode\":\"${report.currentStationCode}\",")
                append("\"nextStationCode\":\"${report.nextStationCode}\",")
                append("\"currentEtaHHmm\":\"${report.currentEtaHHmm}\",")
                append("\"lastUpdatedHHmm\":\"${report.lastUpdatedHHmm}\",")
                append("\"previousEtaHHmm\":\"${report.previousEtaHHmm}\",")
                append("\"changeMinutes\":${report.changeMinutes},")
                append("\"changeReason\":\"${report.changeReason.replace("\"", "\\\"")}\",")
                append("\"confidencePercent\":${report.confidencePercent},")
                append("\"previousConfidencePercent\":${report.previousConfidencePercent},")
                append("\"observedDelayMinutes\":${report.observedDelayMinutes},")
                append("\"predictedDelayMinutes\":${report.predictedDelayMinutes},")
                append("\"triggeredAlerts\":[")
                report.triggeredAlerts.forEachIndexed { i, a ->
                    if (i > 0) append(",")
                    append("{\"id\":\"${a.alertId}\",\"title\":\"${a.title}\",\"severity\":\"${a.severity}\",\"message\":\"${a.message.replace("\"", "\\\"")}\"}")
                }
                append("],")
                append("\"stepAuditLogs\":[")
                report.stepAuditLogs.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("{\"step\":${s.stepNumber},\"name\":\"${s.stepName}\",\"status\":\"${s.status}\",\"detail\":\"${s.detail.replace("\"", "\\\"")}\",\"durationMs\":${s.durationMs}}")
                }
                append("]")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 9. Ingest Live Telemetry and Trigger Recalibration Pipeline + WebSocket Push
        post("/{id}/recalibrate") {
            val trainId = call.parameters["id"] ?: ""
            val delayMin = call.request.queryParameters["delayMin"]?.toIntOrNull() ?: 18
            val speedKmph = call.request.queryParameters["speedKmph"]?.toDoubleOrNull() ?: 62.0
            val stationCode = call.request.queryParameters["stationCode"] ?: "DVL"

            val journey = NashikStationData.allNashikTrains.find { it.trainNumber == trainId }
                ?: NashikStationData.trainPanchavati12109

            val update = LiveOperationalUpdate(
                updateId = "UPD-LIVE-${System.currentTimeMillis()}",
                trainNumber = journey.trainNumber,
                stationCode = stationCode,
                speedKmph = speedKmph,
                observedDelayMinutes = delayMin,
                timestampEpoch = System.currentTimeMillis()
            )

            val report = ContinuousRecalibrationEngine.processLiveUpdate(update, journey)

            // Broadcast real-time over WebSockets & SSE
            broadcastHub?.broadcastRecalibration(report)

            val json = buildString {
                append("{")
                append("\"status\":\"SUCCESS\",")
                append("\"recalibration\":{")
                append("\"trainNumber\":\"${report.trainNumber}\",")
                append("\"currentEtaHHmm\":\"${report.currentEtaHHmm}\",")
                append("\"lastUpdatedHHmm\":\"${report.lastUpdatedHHmm}\",")
                append("\"previousEtaHHmm\":\"${report.previousEtaHHmm}\",")
                append("\"changeMinutes\":${report.changeMinutes},")
                append("\"changeReason\":\"${report.changeReason.replace("\"", "\\\"")}\",")
                append("\"confidencePercent\":${report.confidencePercent}")
                append("}}")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 10. Train Alerts
        get("/{id}/alerts") {
            val trainId = call.parameters["id"] ?: ""
            val allAlerts = database.getActiveAlerts()
            val filtered = allAlerts.filter { it.runId == "run-$trainId-today" || it.runId == null }

            val json = buildString {
                append("[")
                filtered.forEachIndexed { i, a ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"id\":${a.alertId},")
                    append("\"station\":\"${a.stationCode ?: "CORRIDOR"}\",")
                    append("\"type\":\"${a.alertType}\",")
                    append("\"severity\":\"${a.severity}\",")
                    append("\"message\":\"${a.message}\"")
                    append("}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 8. Historical Actual Movements for Train
        get("/{id}/history") {
            val trainId = call.parameters["id"] ?: ""
            val runId = "run-$trainId-today"
            val movements = database.getMovementsForRun(runId)

            val json = buildString {
                append("[")
                movements.forEachIndexed { i, m ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"movementId\":${m.movementId},")
                    append("\"stationCode\":\"${m.stationCode}\",")
                    append("\"actualArrival\":\"${m.actualArrival}\",")
                    append("\"actualDeparture\":\"${m.actualDeparture}\",")
                    append("\"observedDelayMinutes\":${m.observedDelayMinutes},")
                    append("\"platformUsed\":${m.actualPlatformUsed},")
                    append("\"recordedAtEpoch\":${m.recordedAtEpoch}")
                    append("}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }
    }
}
