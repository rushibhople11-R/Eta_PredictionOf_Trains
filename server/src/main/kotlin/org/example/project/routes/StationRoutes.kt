package org.example.project.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.project.db.RailNexusDatabase

/**
 * Station Master & Directory REST Routes.
 * 
 * Implements:
 * - GET /api/stations (?q=search&division=Bhusawal)
 * - GET /api/stations/{code}
 */
fun Route.stationRoutes(database: RailNexusDatabase) {
    route("/api/stations") {
        // 1. Station Listing & Search
        get {
            val query = call.request.queryParameters["q"]?.trim()?.lowercase() ?: ""
            val divisionFilter = call.request.queryParameters["division"]?.trim()?.lowercase() ?: ""

            val stations = database.getAllStations()
            val filtered = stations.filter { s ->
                val matchesQuery = query.isEmpty() ||
                        s.stationCode.lowercase().contains(query) ||
                        s.stationName.lowercase().contains(query)
                val matchesDiv = divisionFilter.isEmpty() ||
                        s.division.lowercase().contains(divisionFilter)
                matchesQuery && matchesDiv
            }

            val json = buildString {
                append("[")
                filtered.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("{")
                    append("\"stationCode\":\"${s.stationCode}\",")
                    append("\"stationName\":\"${s.stationName}\",")
                    append("\"latitude\":${s.latitude},")
                    append("\"longitude\":${s.longitude},")
                    append("\"division\":\"${s.division}\",")
                    append("\"zone\":\"${s.zone}\",")
                    append("\"isJunction\":${s.isJunction},")
                    append("\"totalPlatforms\":${s.totalPlatforms}")
                    append("}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }

        // 2. Specific Station Detail
        get("/{code}") {
            val code = call.parameters["code"]?.trim()?.uppercase() ?: ""
            val station = database.getAllStations().find { it.stationCode.equals(code, ignoreCase = true) }
            if (station == null) {
                call.respondText(
                    """{"error":"Station not found with code: $code","statusCode":404}""",
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound
                )
                return@get
            }

            val json = buildString {
                append("{")
                append("\"stationCode\":\"${station.stationCode}\",")
                append("\"stationName\":\"${station.stationName}\",")
                append("\"latitude\":${station.latitude},")
                append("\"longitude\":${station.longitude},")
                append("\"division\":\"${station.division}\",")
                append("\"zone\":\"${station.zone}\",")
                append("\"isJunction\":${station.isJunction},")
                append("\"totalPlatforms\":${station.totalPlatforms}")
                append("}")
            }
            call.respondText(json, ContentType.Application.Json)
        }
    }
}
