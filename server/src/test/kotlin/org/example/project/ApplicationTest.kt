package org.example.project

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRootHealthEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("RailNexus API"), "Body should mention RailNexus API")
        assertTrue(body.contains("RUNNING"), "Body should indicate RUNNING status")
        assertTrue(body.contains("Nashik (NK)"), "Body should indicate Nashik hub")
        assertTrue(body.contains("istTime"), "Body should contain live IST time")
    }

    @Test
    fun testNashikHubProfile() = testApplication {
        application {
            module()
        }
        val response = client.get("/api/city/nashik/hub")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Nashik Road"), "Profile should contain Nashik Road")
        assertTrue(body.contains("Thal Ghat"), "Profile should report Thal Ghat alerts")
    }

    @Test
    fun testModelCurrentAndHotSwap() = testApplication {
        application {
            module()
        }
        // 1. Check initial active model (default is Quantile ML)
        val currentResp = client.get("/api/model/current")
        assertEquals(HttpStatusCode.OK, currentResp.status)
        assertTrue(currentResp.bodyAsText().contains("QUANTILE_GRADIENT_BOOST"))

        // 2. Hot-swap active model to Heuristic baseline
        val switchResp = client.post("/api/model/switch?model=HEURISTIC")
        assertEquals(HttpStatusCode.OK, switchResp.status)
        assertTrue(switchResp.bodyAsText().contains("HEURISTIC_BASELINE"))

        // 3. Verify current model is now heuristic baseline
        val updatedResp = client.get("/api/model/current")
        assertEquals(HttpStatusCode.OK, updatedResp.status)
        assertTrue(updatedResp.bodyAsText().contains("HEURISTIC_BASELINE"))
    }

    @Test
    fun testTelemetryIngestAcceptance() = testApplication {
        application {
            module()
        }
        val payload = """
            {
                "trainNumber": "22223",
                "latitude": 19.85,
                "longitude": 73.65,
                "speedKmH": 95.0,
                "headingDegrees": 45.0,
                "currentStopIndex": 1,
                "currentDelayMinutes": 4,
                "timestampEpochMillis": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val response = client.post("/api/telemetry/ingest") {
            setBody(payload)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\":\"ACCEPTED\""))
        assertTrue(body.contains("\"trainNumber\":\"22223\""))
        assertTrue(body.contains("confidencePercent"))
        assertTrue(body.contains("predictedEta"))
    }

    @Test
    fun testTelemetryIngestKinematicRejection() = testApplication {
        application {
            module()
        }
        // Speed 260 km/h violates Indian Railways 180 km/h kinematic limit
        val payload = """
            {
                "trainNumber": "22223",
                "latitude": 19.85,
                "longitude": 73.65,
                "speedKmH": 260.0,
                "currentStopIndex": 1,
                "currentDelayMinutes": 0,
                "timestampEpochMillis": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val response = client.post("/api/telemetry/ingest") {
            setBody(payload)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("violates kinematic limits"))
    }

    @Test
    fun testNetworkPrecedenceArbitration() = testApplication {
        application {
            module()
        }
        val response = client.get("/api/network/arbitrate?trainA=22223&trainB=12424")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Kasara - Igatpuri"))
        assertTrue(body.contains("higherPriorityTrain"))
    }
}