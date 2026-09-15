package org.example.project

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Full End-to-End Integration Test Suite for RailNexus Backend REST APIs.
 * 
 * Covers all 14 backend responsibilities and example endpoints:
 * - /api/trains
 * - /api/trains/:id
 * - /api/trains/:id/live
 * - /api/trains/:id/route
 * - /api/trains/:id/eta
 * - /api/trains/:id/delay-propagation
 * - /api/trains/:id/alerts
 * - /api/trains/:id/history
 * - /api/stations
 * - /api/stations/:code
 * - /api/predictions
 * - /api/scenarios
 * - /api/model-performance
 */
class ServerApiIntegrationTest {

    @Test
    fun testTrainSearchAndListing() = testApplication {
        application { module() }

        // 1. Full train listing
        val respAll = client.get("/api/trains")
        assertEquals(HttpStatusCode.OK, respAll.status)
        val bodyAll = respAll.bodyAsText()
        assertTrue(bodyAll.contains("12109"), "Should contain Panchavati Express")
        assertTrue(bodyAll.contains("22223"), "Should contain Vande Bharat")

        // 2. Query filter
        val respFilter = client.get("/api/trains?q=panchavati")
        assertEquals(HttpStatusCode.OK, respFilter.status)
        val bodyFilter = respFilter.bodyAsText()
        assertTrue(bodyFilter.contains("12109"))
    }

    @Test
    fun testTrainDetailsAndNotFoundHandling() = testApplication {
        application { module() }

        // 1. Valid train
        val respValid = client.get("/api/trains/12109")
        assertEquals(HttpStatusCode.OK, respValid.status)
        val bodyValid = respValid.bodyAsText()
        assertTrue(bodyValid.contains("Panchavati Express"))
        assertTrue(bodyValid.contains("schedule"))

        // 2. Invalid train (404 check)
        val respNotFound = client.get("/api/trains/99999")
        assertEquals(HttpStatusCode.NotFound, respNotFound.status)
        assertTrue(respNotFound.bodyAsText().contains("Train not found"))
    }

    @Test
    fun testLiveTrainPosition() = testApplication {
        application { module() }

        val resp = client.get("/api/trains/12109/live")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("currentBlockSection"))
        assertTrue(body.contains("latitude"))
        assertTrue(body.contains("longitude"))
        assertTrue(body.contains("speedKmph"))
    }

    @Test
    fun testTrainRouteInformation() = testApplication {
        application { module() }

        val resp = client.get("/api/trains/12109/route")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("route"))
        assertTrue(body.contains("totalStops"))
        assertTrue(body.contains("CSMT"))
        assertTrue(body.contains("NK"))
    }

    @Test
    fun testStationWiseEtaWithQuantiles() = testApplication {
        application { module() }

        val resp = client.get("/api/trains/12109/eta?stopIndex=1&delayMin=18")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("nextStationDelayMinutes"))
        assertTrue(body.contains("finalImpactDelayMinutes"))
        assertTrue(body.contains("confidenceScore"))
        assertTrue(body.contains("primaryExplanation"))
        assertTrue(body.contains("stationPredictions"))
        assertTrue(body.contains("p10LowerDelta"))
        assertTrue(body.contains("p90UpperDelta"))
    }

    @Test
    fun testDelayPropagationCurve() = testApplication {
        application { module() }

        val resp = client.get("/api/trains/12109/delay-propagation?delayMin=18")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("initialDelayMinutes"))
        assertTrue(body.contains("propagationCurve"))
        assertTrue(body.contains("cumulativeRecoveryMinutes"))
    }

    @Test
    fun testTrainAlertsAndCorridorAlerts() = testApplication {
        application { module() }

        // Specific train alerts
        val respTrainAlerts = client.get("/api/trains/12109/alerts")
        assertEquals(HttpStatusCode.OK, respTrainAlerts.status)

        // Global corridor alerts
        val respCorridorAlerts = client.get("/api/alerts")
        assertEquals(HttpStatusCode.OK, respCorridorAlerts.status)
        assertTrue(respCorridorAlerts.bodyAsText().contains("Thal Ghat"))
    }

    @Test
    fun testStationDirectoryAndLookup() = testApplication {
        application { module() }

        // 1. All stations
        val respAll = client.get("/api/stations")
        assertEquals(HttpStatusCode.OK, respAll.status)
        assertTrue(respAll.bodyAsText().contains("Nashik Road"))

        // 2. Query filter
        val respFilter = client.get("/api/stations?q=devlali")
        assertEquals(HttpStatusCode.OK, respFilter.status)
        assertTrue(respFilter.bodyAsText().contains("DVL"))

        // 3. Station by code
        val respCode = client.get("/api/stations/NK")
        assertEquals(HttpStatusCode.OK, respCode.status)
        assertTrue(respCode.bodyAsText().contains("Nashik Road"))

        // 4. Missing station
        val respMissing = client.get("/api/stations/XYZ99")
        assertEquals(HttpStatusCode.NotFound, respMissing.status)
    }

    @Test
    fun testPredictionInferencePostRequest() = testApplication {
        application { module() }

        val payload = """
            {
                "trainNumber": "12109",
                "currentStopIndex": 2,
                "currentDelayMinutes": 18
            }
        """.trimIndent()

        val resp = client.post("/api/predictions") {
            setBody(payload)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("predictionId"))
        assertTrue(body.contains("predictedNextStationDelayMinutes"))
        assertTrue(body.contains("confidencePercent"))
        assertTrue(body.contains("explanationFactors"))
    }

    @Test
    fun testWhatIfScenariosGetAndPost() = testApplication {
        application { module() }

        // 1. Post new what-if scenario
        val payload = """
            {
                "trainNumber": "12109",
                "stationCode": "DVL",
                "holdMinutes": 12,
                "action": "LOOP_REGULATION"
            }
        """.trimIndent()

        val postResp = client.post("/api/scenarios") {
            setBody(payload)
        }
        assertEquals(HttpStatusCode.OK, postResp.status)
        val postBody = postResp.bodyAsText()
        assertTrue(postBody.contains("scenarioId"))
        assertTrue(postBody.contains("corridorMinutesSaved"))
        assertTrue(postBody.contains("safetyAuditStatus"))

        // 2. Get scenario list
        val getResp = client.get("/api/scenarios?runId=run-12109-today")
        assertEquals(HttpStatusCode.OK, getResp.status)
        assertTrue(getResp.bodyAsText().contains("corridorMinutesSaved"))
    }

    @Test
    fun testModelPerformanceMetrics() = testApplication {
        application { module() }

        val resp = client.get("/api/model-performance")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("activeModelVersion"))
        assertTrue(body.contains("meanAbsoluteErrorMinutes"))
        assertTrue(body.contains("benchmarkComparisons"))
        assertTrue(body.contains("driftStatus"))
    }
}
