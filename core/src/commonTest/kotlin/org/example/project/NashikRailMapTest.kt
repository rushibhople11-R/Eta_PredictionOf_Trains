package org.example.project

import org.example.project.data.NashikRailMapRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NashikRailMapTest {

    @Test
    fun testNashikStationsRegistry() {
        val stations = NashikRailMapRegistry.STATIONS
        assertTrue(stations.isNotEmpty(), "Stations list should not be empty")

        val nk = NashikRailMapRegistry.getStation("NK")
        assertNotNull(nk, "Nashik Road (NK) should exist")
        assertEquals("Nashik Road", nk.name)
        assertEquals(4, nk.platformsCount)
        assertTrue(nk.location.lat in 19.9..20.0, "Nashik Road latitude should be in expected bounds")
        assertTrue(nk.location.lng in 73.8..73.9, "Nashik Road longitude should be in expected bounds")

        val igp = NashikRailMapRegistry.getStation("IGP")
        assertNotNull(igp, "Igatpuri (IGP) should exist")
        assertTrue(igp.isJunction)

        val mmr = NashikRailMapRegistry.getStation("MMR")
        assertNotNull(mmr, "Manmad (MMR) should exist")
        assertTrue(mmr.isJunction)
    }

    @Test
    fun testNashikSectionsRegistry() {
        val sections = NashikRailMapRegistry.SECTIONS
        assertTrue(sections.isNotEmpty(), "Sections list should not be empty")

        val dvlToNk = NashikRailMapRegistry.getSection("SEC-DVL-NK")
        assertNotNull(dvlToNk, "Devlali-Nashik Road section should exist")
        assertEquals("DVL", dvlToNk.fromStationCode)
        assertEquals("NK", dvlToNk.toStationCode)
        assertEquals("AUTOMATIC_BLOCK", dvlToNk.signalingSystem)
        assertTrue(dvlToNk.lengthKm > 0)

        val nkToOdh = NashikRailMapRegistry.getSection("SEC-NK-ODH")
        assertNotNull(nkToOdh, "Nashik Road-Odha section should exist")
        assertEquals(30, nkToOdh.temporarySpeedRestrictionKmph)
    }

    @Test
    fun testNashikLiveTrains() {
        val trains = NashikRailMapRegistry.LIVE_TRAINS
        assertTrue(trains.isNotEmpty(), "Live trains list should not be empty")

        val panchavati = NashikRailMapRegistry.getTrain("12109")
        assertNotNull(panchavati, "Panchavati Express should exist in live tracking")
        assertEquals("NK", panchavati.nextStationCode)
        assertTrue(panchavati.speedKmph in 0..160)

        val vandeBharat = NashikRailMapRegistry.getTrain("22223")
        assertNotNull(vandeBharat, "Vande Bharat should exist")
        assertEquals(1, vandeBharat.priorityLevel)
        assertEquals(0, vandeBharat.delayMinutes)
    }
}
