package org.example.project.data

import org.example.project.model.*

object SampleRailwayData {

    // Common Stations
    val NDLS = Station("NDLS", "New Delhi", platformCount = 16, isJunction = true, division = "DLI")
    val CNB = Station("CNB", "Kanpur Central", platformCount = 10, isJunction = true, division = "PRYJ")
    val PRYJ = Station("PRYJ", "Prayagraj Jn", platformCount = 10, isJunction = true, division = "PRYJ")
    val DDU = Station("DDU", "Pt. Deen Dayal Upadhyaya Jn", platformCount = 8, isJunction = true, division = "DDU")
    val BSB = Station("BSB", "Varanasi Jn", platformCount = 9, isJunction = true, division = "BSB")
    val DNR = Station("DNR", "Danapur", platformCount = 5, isJunction = false, division = "DNR")
    val PNBE = Station("PNBE", "Patna Jn", platformCount = 10, isJunction = true, division = "DNR")
    val GAYA = Station("GAYA", "Gaya Jn", platformCount = 9, isJunction = true, division = "DDU")
    val DHN = Station("DHN", "Dhanbad Jn", platformCount = 8, isJunction = true, division = "DHN")
    val HWH = Station("HWH", "Howrah Jn", platformCount = 23, isJunction = true, division = "HWH")

    // 12424 Dibrugarh Rajdhani Express
    val train12424 = TrainJourney(
        trainNumber = "12424",
        trainName = "Dibrugarh Rajdhani Express",
        trainType = TrainType.RAJDHANI,
        source = NDLS,
        destination = PNBE,
        stops = listOf(
            ScheduleStop(
                station = NDLS,
                scheduledArrival = "16:20",
                scheduledDeparture = "16:20",
                distanceKm = 0,
                scheduledDwellMinutes = 0,
                sectionSlackMinutes = 0
            ),
            ScheduleStop(
                station = CNB,
                scheduledArrival = "21:02",
                scheduledDeparture = "21:07",
                distanceKm = 440,
                scheduledDwellMinutes = 5,
                sectionSlackMinutes = 15,
                speedLimitKmph = 130
            ),
            ScheduleStop(
                station = PRYJ,
                scheduledArrival = "23:08",
                scheduledDeparture = "23:10",
                distanceKm = 635,
                scheduledDwellMinutes = 2,
                sectionSlackMinutes = 10,
                speedLimitKmph = 130,
                junctionCongestionFactor = 1.25
            ),
            ScheduleStop(
                station = DDU,
                scheduledArrival = "01:23",
                scheduledDeparture = "01:33",
                distanceKm = 787,
                scheduledDwellMinutes = 10,
                sectionSlackMinutes = 20, // High slack before DDU yard
                speedLimitKmph = 130,
                isSingleTrackSegment = false,
                junctionCongestionFactor = 1.3
            ),
            ScheduleStop(
                station = DNR,
                scheduledArrival = "03:50",
                scheduledDeparture = "03:52",
                distanceKm = 990,
                scheduledDwellMinutes = 2,
                sectionSlackMinutes = 5,
                isSingleTrackSegment = false
            ),
            ScheduleStop(
                station = PNBE,
                scheduledArrival = "04:15",
                scheduledDeparture = "04:15",
                distanceKm = 1000,
                scheduledDwellMinutes = 0,
                sectionSlackMinutes = 25 // Terminal buffer
            )
        )
    )

    // 22436 Vande Bharat Express (New Delhi -> Varanasi)
    val train22436 = TrainJourney(
        trainNumber = "22436",
        trainName = "Vande Bharat Express",
        trainType = TrainType.VANDE_BHARAT,
        source = NDLS,
        destination = BSB,
        stops = listOf(
            ScheduleStop(
                station = NDLS,
                scheduledArrival = "06:00",
                scheduledDeparture = "06:00",
                distanceKm = 0,
                scheduledDwellMinutes = 0,
                sectionSlackMinutes = 0
            ),
            ScheduleStop(
                station = CNB,
                scheduledArrival = "10:08",
                scheduledDeparture = "10:10",
                distanceKm = 440,
                scheduledDwellMinutes = 2,
                sectionSlackMinutes = 12,
                speedLimitKmph = 130
            ),
            ScheduleStop(
                station = PRYJ,
                scheduledArrival = "12:08",
                scheduledDeparture = "12:10",
                distanceKm = 635,
                scheduledDwellMinutes = 2,
                sectionSlackMinutes = 8,
                speedLimitKmph = 130,
                junctionCongestionFactor = 1.15
            ),
            ScheduleStop(
                station = BSB,
                scheduledArrival = "14:00",
                scheduledDeparture = "14:00",
                distanceKm = 759,
                scheduledDwellMinutes = 0,
                sectionSlackMinutes = 20 // Terminal buffer
            )
        )
    )

    // 12302 Howrah Rajdhani Express
    val train12302 = TrainJourney(
        trainNumber = "12302",
        trainName = "Howrah Rajdhani Express",
        trainType = TrainType.RAJDHANI,
        source = NDLS,
        destination = HWH,
        stops = listOf(
            ScheduleStop(
                station = NDLS,
                scheduledArrival = "16:50",
                scheduledDeparture = "16:50",
                distanceKm = 0,
                scheduledDwellMinutes = 0
            ),
            ScheduleStop(
                station = CNB,
                scheduledArrival = "21:32",
                scheduledDeparture = "21:37",
                distanceKm = 440,
                scheduledDwellMinutes = 5,
                sectionSlackMinutes = 15
            ),
            ScheduleStop(
                station = DDU,
                scheduledArrival = "01:45",
                scheduledDeparture = "01:55",
                distanceKm = 787,
                scheduledDwellMinutes = 10,
                sectionSlackMinutes = 18,
                junctionCongestionFactor = 1.25
            ),
            ScheduleStop(
                station = GAYA,
                scheduledArrival = "03:58",
                scheduledDeparture = "04:01",
                distanceKm = 992,
                scheduledDwellMinutes = 3,
                sectionSlackMinutes = 10
            ),
            ScheduleStop(
                station = DHN,
                scheduledArrival = "06:18",
                scheduledDeparture = "06:23",
                distanceKm = 1193,
                scheduledDwellMinutes = 5,
                sectionSlackMinutes = 12
            ),
            ScheduleStop(
                station = HWH,
                scheduledArrival = "09:55",
                scheduledDeparture = "09:55",
                distanceKm = 1451,
                scheduledDwellMinutes = 0,
                sectionSlackMinutes = 30 // High terminal recovery slack
            )
        )
    )

    val allSampleTrains = NashikStationData.allNashikTrains + listOf(train12424, train22436, train12302)

    val sampleConnectingServices = NashikStationData.nashikConnectingServices
}
