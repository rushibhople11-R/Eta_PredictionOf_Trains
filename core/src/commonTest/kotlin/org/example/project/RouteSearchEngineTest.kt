package org.example.project

import org.example.project.data.SamplePnrData
import org.example.project.data.SampleRailwayData
import org.example.project.model.BookingStatus
import org.example.project.model.RouteSearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouteSearchEngineTest {

    @Test
    fun testFindTrainsBetweenNDLSAndBSB() {
        val trains = SampleRailwayData.allSampleTrains
        val results = RouteSearchEngine.findTrainsBetweenStations(
            trains = trains,
            fromStationCode = "NDLS",
            toStationCode = "BSB"
        )

        // 22436 Vande Bharat runs NDLS -> BSB
        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("22436", result.train.trainNumber)
        assertEquals("06:00", result.departureTime)
        assertEquals("14:00", result.arrivalTime)
        assertEquals("8h 0m", result.durationFormatted)
    }

    @Test
    fun testFindTrainsBetweenNDLSAndCNB() {
        val trains = SampleRailwayData.allSampleTrains
        // NDLS to CNB is on all 3 train routes
        val results = RouteSearchEngine.findTrainsBetweenStations(
            trains = trains,
            fromStationCode = "NDLS",
            toStationCode = "CNB"
        )

        assertEquals(3, results.size)
    }

    @Test
    fun testFindTrainsBetweenCSMTAndNashikRoad() {
        val trains = SampleRailwayData.allSampleTrains
        val results = RouteSearchEngine.findTrainsBetweenStations(
            trains = trains,
            fromStationCode = "CSMT",
            toStationCode = "NK"
        )

        // Multiple iconic trains connect Mumbai CSMT and Nashik Road
        assertTrue(results.size >= 5, "Expected at least 5 trains connecting CSMT to Nashik Road (NK)")
        val trainNumbers = results.map { it.train.trainNumber }
        assertTrue(trainNumbers.contains("22223"), "Should contain Vande Bharat 22223")
        assertTrue(trainNumbers.contains("12109"), "Should contain Panchavati Express 12109")
        assertTrue(trainNumbers.contains("12137"), "Should contain Punjab Mail 12137")
        assertTrue(trainNumbers.contains("12859"), "Should contain Gitanjali Express 12859")
    }

    @Test
    fun testNashikPnrLookup() {
        val pnr = SamplePnrData.findPnr("4529184029")
        assertNotNull(pnr)
        assertEquals("22223", pnr.trainNumber)
        assertEquals("CSMT", pnr.sourceStation.code)
        assertEquals("NK", pnr.destinationStation.code)
        assertEquals(2, pnr.passengers.size)
    }

    @Test
    fun testPnrLookup() {
        val pnr = SamplePnrData.findPnr("2849103842")
        assertNotNull(pnr)
        assertEquals("12424", pnr.trainNumber)
        assertEquals("NDLS", pnr.sourceStation.code)
        assertEquals("PNBE", pnr.destinationStation.code)
        assertEquals(2, pnr.passengers.size)
        assertEquals(BookingStatus.CONFIRMED, pnr.passengers[0].status)
    }
}
