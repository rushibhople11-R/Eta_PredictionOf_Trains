package org.example.project

import org.example.project.data.NashikStationData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NashikStationDataTest {

    @Test
    fun testNashikHubProfile() {
        val hub = NashikStationData.hubProfile
        assertEquals("Nashik", hub.city)
        assertEquals("NK", hub.primaryStation.code)
        assertEquals(4, hub.totalPlatforms)
        assertTrue(hub.transitServices.isNotEmpty())
        assertTrue(hub.ghatSectionAlert.contains("Thal Ghat"))
    }

    @Test
    fun testNashikTrainTimetable() {
        val trains = NashikStationData.liveStationTrains
        assertTrue(trains.size >= 10, "Expected at least 10 real-world trains passing Nashik Road")
        val vandeBharat = trains.find { it.trainNumber == "22223" }
        assertTrue(vandeBharat != null)
        assertEquals("Pf 2", vandeBharat.platformNumber)
    }
}
