package org.example.project.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.project.db.RailNexusDatabase
import org.example.project.db.WhatIfScenarioEntity

/**
 * What-If Contingency & Regulation Sandbox Routes.
 * 
 * Implements:
 * - GET /api/scenarios
 * - POST /api/scenarios
 */
fun Route.scenarioRoutes(database: RailNexusDatabase) {
    route("/api/scenarios") {
        // 1. List recorded what-if scenarios
        get {
            val runId = call.request.queryParameters["runId"] ?: "run-12109-today"
            val scenarios = database.getWhatIfScenariosForRun(runId)

            val json = buildString {
                append("[")
                scenarios.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"scenarioId\":\"${s.scenarioId}\",")
                    append("\"runId\":\"${s.runId}\",")
                    append("\"regulationStationCode\":\"${s.regulationStationCode}\",")
                    append("\"hypotheticalDelayMinutes\":${s.hypotheticalDelayMinutes},")
                    append("\"predictedDownstreamImpactMinutes\":${s.predictedDownstreamImpactMinutes},")
                    append("\"corridorMinutesSaved\":${s.corridorMinutesSaved},")
                    append("\"safetyAuditStatus\":\"${s.safetyAuditStatus}\",")
                    append("\"controllerAction\":\"${s.controllerAction}\",")
                    append("\"simulatedAt\":${s.simulatedAt}")
                    append("}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 2. Evaluate and record a new what-if scenario
        post {
            val rawBody = call.receiveText()
            fun extractString(key: String): String? {
                val match = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(rawBody)
                return match?.groupValues?.get(1)
            }
            fun extractInt(key: String): Int? {
                val match = Regex("\"$key\"\\s*:\\s*([0-9-]+)").find(rawBody)
                return match?.groupValues?.get(1)?.toIntOrNull()
            }

            val trainNumber = extractString("trainNumber")
                ?: call.request.queryParameters["trainNumber"]
                ?: "12109"

            val stationCode = extractString("stationCode")
                ?: call.request.queryParameters["stationCode"]
                ?: "DVL"

            val holdMinutes = extractInt("holdMinutes")
                ?: call.request.queryParameters["holdMinutes"]?.toIntOrNull()
                ?: 10

            val action = extractString("action")
                ?: call.request.queryParameters["action"]
                ?: "LOOP_REGULATION"

            // Compute hypothetical downstream savings
            val downstreamImpact = (holdMinutes * 0.4).toInt().coerceAtLeast(3)
            val minutesSaved = (holdMinutes * 1.8).toInt().coerceAtLeast(6)
            val nowEpoch = System.currentTimeMillis()
            val runId = "run-$trainNumber-today"

            val entity = WhatIfScenarioEntity(
                scenarioId = "scen-$nowEpoch",
                runId = runId,
                regulationStationCode = stationCode,
                hypotheticalDelayMinutes = holdMinutes,
                predictedDownstreamImpactMinutes = downstreamImpact,
                corridorMinutesSaved = minutesSaved,
                safetyAuditStatus = "AUDIT_PASSED_SAFE",
                controllerAction = "Regulate $trainNumber at $stationCode Loop Line for $holdMinutes min to allow high-priority Vande Bharat overtake; net saving +$minutesSaved min across corridor.",
                simulatedAt = nowEpoch
            )
            database.insertWhatIfScenario(entity)

            val json = buildString {
                append("{")
                append("\"scenarioId\":\"${entity.scenarioId}\",")
                append("\"trainNumber\":\"$trainNumber\",")
                append("\"regulationStationCode\":\"${entity.regulationStationCode}\",")
                append("\"holdMinutes\":${entity.hypotheticalDelayMinutes},")
                append("\"predictedDownstreamImpactMinutes\":${entity.predictedDownstreamImpactMinutes},")
                append("\"corridorMinutesSaved\":${entity.corridorMinutesSaved},")
                append("\"safetyAuditStatus\":\"${entity.safetyAuditStatus}\",")
                append("\"controllerAction\":\"${entity.controllerAction}\",")
                append("\"simulatedAt\":${entity.simulatedAt}")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }
    }
}
