package org.example.project

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NashikGoogleMapRouteTest {

    @Test
    fun testNashikMapHtmlEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/api/map/nashik")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), response.contentType())
        val html = response.bodyAsText()
        assertTrue(html.contains("RAILअंदाज"), "HTML should contain RAILअंदाज branding")
        assertTrue(html.contains("Nashik"), "HTML should mention Nashik")
        assertTrue(html.contains("Google Maps"), "HTML should reference Google Maps")
        assertTrue(html.contains("DEMO_MAP_ID"), "HTML should set DEMO_MAP_ID for AdvancedMarkerElement")
    }

    @Test
    fun testNashikMapDataEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/api/map/nashik/data")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
        val json = response.bodyAsText()
        assertTrue(json.contains("\"stations\""), "JSON should contain stations array")
        assertTrue(json.contains("\"sections\""), "JSON should contain sections array")
        assertTrue(json.contains("\"trains\""), "JSON should contain trains array")
        assertTrue(json.contains("Nashik Road"), "JSON should have Nashik Road station")
        assertTrue(json.contains("Panchavati Express"), "JSON should include Panchavati Express")
    }
}
