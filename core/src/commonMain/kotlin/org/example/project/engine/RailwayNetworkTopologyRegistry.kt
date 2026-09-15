package org.example.project.engine

import org.example.project.data.NashikStationData
import org.example.project.model.*

/**
 * Railway Network Topology & Segment Registry.
 *
 * Models physical track corridors as directed graphs of StationNodes and RouteSegments.
 * Tracks live block occupancies across multiple trains and resolves track precedence.
 */
object RailwayNetworkTopologyRegistry {

    // Nashik Mainline Corridor segments (CSMT to Manmad)
    private val corridorSegments = mutableMapOf<String, RouteSegment>()

    // Active block occupancy map: SegmentId -> List of TrainNumbers
    private val blockOccupancyMap = mutableMapOf<String, MutableList<String>>()

    init {
        seedCorridorTopology()
    }

    private fun seedCorridorTopology() {
        val rawSegments = listOf(
            RouteSegment(
                segmentId = "SEG-CSMT-TNA",
                routeName = "Mumbai Suburban Fast Corridor",
                fromStationCode = "CSMT",
                toStationCode = "TNA",
                distanceKm = 34.0,
                speedLimitKmh = 105.0,
                isSingleTrack = false,
                sectionSlackMinutes = 3,
                historicalMedianRuntimeMinutes = 33.0
            ),
            RouteSegment(
                segmentId = "SEG-TNA-KYN",
                routeName = "Thane - Kalyan Quadruple Line",
                fromStationCode = "TNA",
                toStationCode = "KYN",
                distanceKm = 20.0,
                speedLimitKmh = 110.0,
                isSingleTrack = false,
                sectionSlackMinutes = 2,
                historicalMedianRuntimeMinutes = 18.0
            ),
            RouteSegment(
                segmentId = "SEG-KYN-KSRA",
                routeName = "Kalyan - Kasara Double Line",
                fromStationCode = "KYN",
                toStationCode = "KSRA",
                distanceKm = 67.0,
                speedLimitKmh = 130.0,
                isSingleTrack = false,
                sectionSlackMinutes = 4,
                historicalMedianRuntimeMinutes = 52.0
            ),
            RouteSegment(
                segmentId = "SEG-KSRA-IGP",
                routeName = "Thal Ghat Mountain Incline (1:37 Banking Section)",
                fromStationCode = "KSRA",
                toStationCode = "IGP",
                distanceKm = 16.0,
                speedLimitKmh = 60.0,
                isSingleTrack = false, // 3 lines in Ghat section (Up, Down, Middle)
                isGhatSection = true,
                gradientFactor = 1.37,
                sectionSlackMinutes = 10,
                historicalMedianRuntimeMinutes = 38.0
            ),
            RouteSegment(
                segmentId = "SEG-IGP-DVL",
                routeName = "Igatpuri - Devlali Plateau Double Line",
                fromStationCode = "IGP",
                toStationCode = "DVL",
                distanceKm = 42.0,
                speedLimitKmh = 130.0,
                isSingleTrack = false,
                sectionSlackMinutes = 4,
                historicalMedianRuntimeMinutes = 32.0
            ),
            RouteSegment(
                segmentId = "SEG-DVL-NK",
                routeName = "Devlali - Nashik Road Section",
                fromStationCode = "DVL",
                toStationCode = "NK",
                distanceKm = 9.0,
                speedLimitKmh = 110.0,
                isSingleTrack = false,
                sectionSlackMinutes = 4,
                historicalMedianRuntimeMinutes = 10.0
            ),
            RouteSegment(
                segmentId = "SEG-NK-ODH",
                routeName = "Nashik Road - Odha Single Track Token Block",
                fromStationCode = "NK",
                toStationCode = "ODH",
                distanceKm = 17.0,
                speedLimitKmh = 100.0,
                isSingleTrack = true, // Single track bottleneck
                sectionSlackMinutes = 2,
                historicalMedianRuntimeMinutes = 16.0
            ),
            RouteSegment(
                segmentId = "SEG-ODH-MMR",
                routeName = "Odha - Manmad Junction Approach",
                fromStationCode = "ODH",
                toStationCode = "MMR",
                distanceKm = 56.0,
                speedLimitKmh = 130.0,
                isSingleTrack = false,
                sectionSlackMinutes = 8,
                historicalMedianRuntimeMinutes = 44.0
            )
        )

        rawSegments.forEach { seg ->
            corridorSegments["${seg.fromStationCode}-${seg.toStationCode}"] = seg
            corridorSegments[seg.segmentId] = seg
        }

        // Pre-populate with typical active trains
        registerTrainOccupancy("12109", "SEG-DVL-NK")
        registerTrainOccupancy("22223", "SEG-KYN-KSRA")
    }

    fun getSegment(fromStationCode: String, toStationCode: String): RouteSegment {
        val key = "$fromStationCode-$toStationCode"
        return corridorSegments[key] ?: RouteSegment(
            segmentId = "SEG-$fromStationCode-$toStationCode",
            routeName = "Section $fromStationCode to $toStationCode",
            fromStationCode = fromStationCode,
            toStationCode = toStationCode,
            distanceKm = 25.0,
            speedLimitKmh = 110.0,
            isSingleTrack = false,
            sectionSlackMinutes = 2,
            historicalMedianRuntimeMinutes = 22.0
        )
    }

    fun getAllCorridorSegments(): List<RouteSegment> = corridorSegments.values.distinctBy { it.segmentId }

    fun registerTrainOccupancy(trainNumber: String, segmentId: String) {
        synchronized(blockOccupancyMap) {
            // Remove previous occupancies
            blockOccupancyMap.values.forEach { it.remove(trainNumber) }
            val list = blockOccupancyMap.getOrPut(segmentId) { mutableListOf() }
            if (!list.contains(trainNumber)) list.add(trainNumber)
        }
    }

    fun getActiveTrainsInBlock(segmentId: String): List<String> {
        synchronized(blockOccupancyMap) {
            return blockOccupancyMap[segmentId]?.toList() ?: emptyList()
        }
    }

    fun clearTrainOccupancy(trainNumber: String) {
        synchronized(blockOccupancyMap) {
            blockOccupancyMap.values.forEach { it.remove(trainNumber) }
        }
    }
}
