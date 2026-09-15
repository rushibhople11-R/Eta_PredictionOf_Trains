package org.example.project.data

import org.example.project.model.*

object NashikStationData {

    // Key Stations
    // Key Stations and Corridor Junctions
    val NK = Station("NK", "Nashik Road", platformCount = 4, isJunction = true, division = "BSL")
    val DVL = Station("DVL", "Devlali", platformCount = 3, isJunction = false, division = "BSL")
    val ODH = Station("ODH", "Odha", platformCount = 2, isJunction = false, division = "BSL")
    val LS = Station("LS", "Lasalgaon", platformCount = 2, isJunction = false, division = "BSL")
    val IGP = Station("IGP", "Igatpuri (Thal Ghat Summit)", platformCount = 4, isJunction = true, division = "BB")
    val KSRA = Station("KSRA", "Kasara (Thal Ghat Base)", platformCount = 4, isJunction = true, division = "BB")
    val KYN = Station("KYN", "Kalyan Jn", platformCount = 8, isJunction = true, division = "BB")
    val TNA = Station("TNA", "Thane", platformCount = 8, isJunction = true, division = "BB")
    val DR = Station("DR", "Dadar Central", platformCount = 8, isJunction = true, division = "BB")
    val CSMT = Station("CSMT", "Mumbai CSMT", platformCount = 18, isJunction = true, division = "BB")
    val LTT = Station("LTT", "Mumbai LTT", platformCount = 5, isJunction = true, division = "BB")
    val MMR = Station("MMR", "Manmad Jn", platformCount = 6, isJunction = true, division = "BSL")
    val CSN = Station("CSN", "Chalisgaon Jn", platformCount = 4, isJunction = true, division = "BSL")
    val JL = Station("JL", "Jalgaon Jn", platformCount = 5, isJunction = true, division = "BSL")
    val BSL = Station("BSL", "Bhusawal Jn", platformCount = 8, isJunction = true, division = "BSL")
    val SNSI = Station("SNSI", "Sainagar Shirdi", platformCount = 3, isJunction = false, division = "SUR")
    val HWH = Station("HWH", "Howrah Jn", platformCount = 23, isJunction = true, division = "HWH")
    val FZR = Station("FZR", "Firozpur Cantt", platformCount = 5, isJunction = true, division = "FZR")
    val J = Station("J", "Jalna", platformCount = 3, isJunction = false, division = "NED")
    val NZM = Station("NZM", "Hazrat Nizamuddin", platformCount = 9, isJunction = true, division = "DLI")
    val ERS = Station("ERS", "Ernakulam Jn", platformCount = 6, isJunction = true, division = "TVC")
    val PPTA = Station("PPTA", "Patliputra", platformCount = 5, isJunction = true, division = "DNR")
    val G = Station("G", "Gondia Jn", platformCount = 6, isJunction = true, division = "NGP")
    val AMI = Station("AMI", "Amravati", platformCount = 3, isJunction = false, division = "BSL")
    val SC = Station("SC", "Secunderabad", platformCount = 10, isJunction = true, division = "SC")
    val ASR = Station("ASR", "Amritsar Jn", platformCount = 6, isJunction = true, division = "FZR")
    val BSBS = Station("BSBS", "Banaras", platformCount = 8, isJunction = true, division = "BSB")

    val allNashikCorridorStations = listOf(
        NK, DVL, ODH, LS, IGP, KSRA, MMR, KYN, TNA, DR, CSMT, LTT, CSN, JL, BSL, SNSI
    )

    val hubProfile = CityHubProfile(
        city = "Nashik",
        state = "Maharashtra, India",
        primaryStation = NK,
        zone = "Central Railway (CR)",
        division = "Bhusawal (BSL)",
        totalPlatforms = 4,
        ghatSectionAlert = "Thal Ghat (Kasara - Igatpuri) banking operations active. Mandatory safety brake tests underway.",
        ghatDelayVarianceMin = 8,
        transitServices = listOf(
            CityTransitLink(
                title = "Trimbakeshwar Jyotirlinga Pilgrim Special",
                destination = "Trimbakeshwar Temple (38 km)",
                transitType = "MSRTC Direct Bus",
                pickupLocation = "Station Bay 1 (Outside Pf 1)",
                frequency = "Every 20 mins"
            ),
            CityTransitLink(
                title = "Ozar Airport (ISKCON / HAL Feeder)",
                destination = "Nashik Airport - Ozar (24 km)",
                transitType = "Prepaid EV Cab",
                pickupLocation = "EV Taxi Counter, Circulating Area",
                frequency = "On demand / Flight timings"
            ),
            CityTransitLink(
                title = "Panchavati & Godavari Ghat Shuttle",
                destination = "Ramkund / Panchavati Heritage (11 km)",
                transitType = "City Bus Service",
                pickupLocation = "Nashik Road Bus Depot",
                frequency = "Every 10 mins"
            ),
            CityTransitLink(
                title = "CBS (Central Bus Stand) Express Bus",
                destination = "Nashik CBS / Old City (9 km)",
                transitType = "MSRTC AC City Link",
                pickupLocation = "Main Station Road Exit",
                frequency = "Every 15 mins"
            )
        )
    )

    // Real-world train timetable at Nashik Road (NK)
    val liveStationTrains = listOf(
        StationTrainEntry(
            trainNumber = "22223",
            trainName = "CSMT - Sainagar Shirdi Vande Bharat",
            trainType = TrainType.VANDE_BHARAT,
            origin = CSMT,
            destination = SNSI,
            scheduledArrival = "09:30",
            scheduledDeparture = "09:32",
            predictedArrivalEta = "09:34",
            predictedDepartureEta = "09:36",
            platformNumber = "Pf 2",
            delayMinutes = 4,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Smooth Thal ghat banking clearance",
            intermediateVia = "Kalyan • Igatpuri • Nashik Road"
        ),
        StationTrainEntry(
            trainNumber = "12859",
            trainName = "Gitanjali Superfast Express",
            trainType = TrainType.SUPERFAST,
            origin = CSMT,
            destination = HWH,
            scheduledArrival = "09:43",
            scheduledDeparture = "09:45",
            predictedArrivalEta = "09:55",
            predictedDepartureEta = "09:57",
            platformNumber = "Pf 1",
            delayMinutes = 12,
            status = TrainStationStatus.DELAYED,
            delayReason = "Banker engine detach queue at Igatpuri",
            intermediateVia = "Kalyan • Igatpuri • Bhusawal • Nagpur"
        ),
        StationTrainEntry(
            trainNumber = "12071",
            trainName = "CSMT - Jalna Jan Shatabdi Express",
            trainType = TrainType.SHATABDI,
            origin = CSMT,
            destination = J,
            scheduledArrival = "15:52",
            scheduledDeparture = "15:55",
            predictedArrivalEta = "15:53",
            predictedDepartureEta = "15:56",
            platformNumber = "Pf 1",
            delayMinutes = 1,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Maintaining section speed",
            intermediateVia = "Dadar • Kalyan • Igatpuri • Manmad"
        ),
        StationTrainEntry(
            trainNumber = "12617",
            trainName = "Mangala Lakshadweep SF Express",
            trainType = TrainType.SUPERFAST,
            origin = ERS,
            destination = NZM,
            scheduledArrival = "16:30",
            scheduledDeparture = "16:35",
            predictedArrivalEta = "16:52",
            predictedDepartureEta = "16:57",
            platformNumber = "Pf 2",
            delayMinutes = 22,
            status = TrainStationStatus.DELAYED,
            delayReason = "Thal Ghat speed restriction compliance",
            intermediateVia = "Panvel • Kalyan • Igatpuri • Bhusawal"
        ),
        StationTrainEntry(
            trainNumber = "12860",
            trainName = "Gitanjali Express (Towards Mumbai)",
            trainType = TrainType.SUPERFAST,
            origin = HWH,
            destination = CSMT,
            scheduledArrival = "17:05",
            scheduledDeparture = "17:10",
            predictedArrivalEta = "17:08",
            predictedDepartureEta = "17:13",
            platformNumber = "Pf 3",
            delayMinutes = 3,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Recovered 15 min on Bhusawal-Manmad slack",
            intermediateVia = "Bhusawal • Manmad • Nashik • Kalyan"
        ),
        StationTrainEntry(
            trainNumber = "12105",
            trainName = "Vidarbha Superfast Express",
            trainType = TrainType.SUPERFAST,
            origin = CSMT,
            destination = G,
            scheduledArrival = "22:12",
            scheduledDeparture = "22:15",
            predictedArrivalEta = "22:20",
            predictedDepartureEta = "22:23",
            platformNumber = "Pf 2",
            delayMinutes = 8,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Minor platform clearance wait",
            intermediateVia = "Dadar • Thane • Kalyan • Igatpuri"
        ),
        StationTrainEntry(
            trainNumber = "12137",
            trainName = "Punjab Mail",
            trainType = TrainType.SUPERFAST,
            origin = CSMT,
            destination = FZR,
            scheduledArrival = "22:47",
            scheduledDeparture = "22:50",
            predictedArrivalEta = "23:05",
            predictedDepartureEta = "23:08",
            platformNumber = "Pf 2",
            delayMinutes = 18,
            status = TrainStationStatus.DELAYED,
            delayReason = "Goods train crossing before Kasara",
            intermediateVia = "Kalyan • Igatpuri • Bhusawal • Gwalior"
        ),
        StationTrainEntry(
            trainNumber = "12111",
            trainName = "CSMT - Amravati Superfast Express",
            trainType = TrainType.SUPERFAST,
            origin = CSMT,
            destination = AMI,
            scheduledArrival = "23:25",
            scheduledDeparture = "23:28",
            predictedArrivalEta = "23:26",
            predictedDepartureEta = "23:29",
            platformNumber = "Pf 2",
            delayMinutes = 1,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Punctual run",
            intermediateVia = "Thane • Kalyan • Igatpuri • Badnera"
        ),
        StationTrainEntry(
            trainNumber = "17057",
            trainName = "Devagiri Express",
            trainType = TrainType.MAIL_EXPRESS,
            origin = CSMT,
            destination = SC,
            scheduledArrival = "00:25",
            scheduledDeparture = "00:30",
            predictedArrivalEta = "00:55",
            predictedDepartureEta = "01:00",
            platformNumber = "Pf 1",
            delayMinutes = 30,
            status = TrainStationStatus.HEAVY_DELAY,
            delayReason = "Preceding suburban block near Kalyan",
            intermediateVia = "Kalyan • Igatpuri • Manmad • Aurangabad"
        ),
        StationTrainEntry(
            trainNumber = "12167",
            trainName = "Mumbai LTT - Banaras Superfast Express",
            trainType = TrainType.SUPERFAST,
            origin = LTT,
            destination = BSBS,
            scheduledArrival = "01:25",
            scheduledDeparture = "01:30",
            predictedArrivalEta = "01:32",
            predictedDepartureEta = "01:37",
            platformNumber = "Pf 1",
            delayMinutes = 7,
            status = TrainStationStatus.ON_TIME,
            delayReason = "On-schedule bank detachment at Igatpuri",
            intermediateVia = "Thane • Kalyan • Igatpuri • Jabalpur"
        ),
        StationTrainEntry(
            trainNumber = "12141",
            trainName = "Mumbai LTT - Patliputra SF Express",
            trainType = TrainType.SUPERFAST,
            origin = LTT,
            destination = PPTA,
            scheduledArrival = "02:20",
            scheduledDeparture = "02:25",
            predictedArrivalEta = "02:45",
            predictedDepartureEta = "02:50",
            platformNumber = "Pf 1",
            delayMinutes = 25,
            status = TrainStationStatus.DELAYED,
            delayReason = "Congestion on Kasara uphill grade",
            intermediateVia = "Kalyan • Igatpuri • Bhusawal • Pt DDU"
        ),
        StationTrainEntry(
            trainNumber = "11057",
            trainName = "Amritsar Express (via Khandwa)",
            trainType = TrainType.MAIL_EXPRESS,
            origin = CSMT,
            destination = ASR,
            scheduledArrival = "02:40",
            scheduledDeparture = "02:45",
            predictedArrivalEta = "02:40",
            predictedDepartureEta = "02:45",
            platformNumber = "Pf 2",
            delayMinutes = 0,
            status = TrainStationStatus.ON_TIME,
            delayReason = "Perfect section runtime",
            intermediateVia = "Dadar • Kalyan • Igatpuri • Bhopal"
        )
    )

    // Full route objects for trains exclusively passing Nashik Road (NK)
    val trainVandeBharat22223 = TrainJourney(
        trainNumber = "22223",
        trainName = "CSMT - Sainagar Shirdi Vande Bharat",
        trainType = TrainType.VANDE_BHARAT,
        source = CSMT,
        destination = SNSI,
        stops = listOf(
            ScheduleStop(CSMT, "06:20", "06:20", 0, 0),
            ScheduleStop(TNA, "06:53", "06:55", 34, 2, sectionSlackMinutes = 3),
            ScheduleStop(KYN, "07:13", "07:15", 54, 2, sectionSlackMinutes = 4),
            ScheduleStop(IGP, "08:43", "08:45", 137, 2, sectionSlackMinutes = 10, isSingleTrackSegment = false),
            ScheduleStop(DVL, "09:18", "09:20", 179, 2, sectionSlackMinutes = 4),
            ScheduleStop(NK, "09:30", "09:32", 188, 2, sectionSlackMinutes = 8),
            ScheduleStop(MMR, "10:35", "10:37", 261, 2, sectionSlackMinutes = 12),
            ScheduleStop(SNSI, "11:30", "11:30", 343, 0, sectionSlackMinutes = 15)
        )
    )

    // 12109 Panchavati Superfast Express - The iconic Nashik commuter lifeline
    val trainPanchavati12109 = TrainJourney(
        trainNumber = "12109",
        trainName = "Panchavati Superfast Express",
        trainType = TrainType.SUPERFAST,
        source = CSMT,
        destination = MMR,
        stops = listOf(
            ScheduleStop(CSMT, "18:15", "18:15", 0, 0),
            ScheduleStop(DR, "18:28", "18:30", 9, 2, sectionSlackMinutes = 2),
            ScheduleStop(KYN, "19:08", "19:10", 54, 2, sectionSlackMinutes = 4),
            ScheduleStop(KSRA, "20:10", "20:12", 121, 2, sectionSlackMinutes = 5),
            ScheduleStop(IGP, "20:45", "20:50", 137, 5, sectionSlackMinutes = 12),
            ScheduleStop(DVL, "21:20", "21:22", 179, 2, sectionSlackMinutes = 3),
            ScheduleStop(NK, "21:32", "21:35", 188, 3, sectionSlackMinutes = 6),
            ScheduleStop(LS, "22:15", "22:17", 236, 2, sectionSlackMinutes = 4),
            ScheduleStop(MMR, "22:50", "22:50", 261, 0, sectionSlackMinutes = 8)
        )
    )

    // 12137 Punjab Mail
    val trainPunjabMail12137 = TrainJourney(
        trainNumber = "12137",
        trainName = "Punjab Mail (via Nashik Road)",
        trainType = TrainType.SUPERFAST,
        source = CSMT,
        destination = FZR,
        stops = listOf(
            ScheduleStop(CSMT, "19:35", "19:35", 0, 0),
            ScheduleStop(DR, "19:48", "19:50", 9, 2, sectionSlackMinutes = 2),
            ScheduleStop(TNA, "20:08", "20:10", 34, 2, sectionSlackMinutes = 3),
            ScheduleStop(KYN, "20:32", "20:35", 54, 3, sectionSlackMinutes = 4),
            ScheduleStop(KSRA, "21:40", "21:43", 121, 3, sectionSlackMinutes = 5),
            ScheduleStop(IGP, "22:10", "22:15", 137, 5, sectionSlackMinutes = 14),
            ScheduleStop(DVL, "22:36", "22:38", 179, 2, sectionSlackMinutes = 3),
            ScheduleStop(NK, "22:47", "22:50", 188, 3, sectionSlackMinutes = 8),
            ScheduleStop(MMR, "23:45", "23:48", 261, 3, sectionSlackMinutes = 10),
            ScheduleStop(CSN, "00:30", "00:32", 328, 2, sectionSlackMinutes = 8),
            ScheduleStop(JL, "01:20", "01:22", 421, 2, sectionSlackMinutes = 8),
            ScheduleStop(BSL, "02:05", "02:10", 445, 5, sectionSlackMinutes = 15)
        )
    )

    // 12859 Gitanjali Superfast Express
    val trainGitanjali12859 = TrainJourney(
        trainNumber = "12859",
        trainName = "Gitanjali Superfast Express",
        trainType = TrainType.SUPERFAST,
        source = CSMT,
        destination = HWH,
        stops = listOf(
            ScheduleStop(CSMT, "06:00", "06:00", 0, 0),
            ScheduleStop(DR, "06:13", "06:15", 9, 2, sectionSlackMinutes = 2),
            ScheduleStop(KYN, "06:52", "06:55", 54, 3, sectionSlackMinutes = 4),
            ScheduleStop(KSRA, "07:55", "07:58", 121, 3, sectionSlackMinutes = 5),
            ScheduleStop(IGP, "08:35", "08:40", 137, 5, sectionSlackMinutes = 12),
            ScheduleStop(NK, "09:43", "09:45", 188, 2, sectionSlackMinutes = 8),
            ScheduleStop(MMR, "10:48", "10:50", 261, 2, sectionSlackMinutes = 10),
            ScheduleStop(JL, "11:58", "12:00", 421, 2, sectionSlackMinutes = 8),
            ScheduleStop(BSL, "12:40", "12:45", 445, 5, sectionSlackMinutes = 15)
        )
    )

    // 12617 Mangala Lakshadweep Superfast Express
    val trainMangala12617 = TrainJourney(
        trainNumber = "12617",
        trainName = "Mangala Lakshadweep SF Express",
        trainType = TrainType.SUPERFAST,
        source = ERS,
        destination = NZM,
        stops = listOf(
            ScheduleStop(KYN, "13:20", "13:25", 0, 5, sectionSlackMinutes = 5),
            ScheduleStop(KSRA, "14:25", "14:28", 67, 3, sectionSlackMinutes = 5),
            ScheduleStop(IGP, "15:05", "15:10", 83, 5, sectionSlackMinutes = 14),
            ScheduleStop(NK, "16:30", "16:35", 134, 5, sectionSlackMinutes = 10),
            ScheduleStop(MMR, "17:40", "17:43", 207, 3, sectionSlackMinutes = 8),
            ScheduleStop(BSL, "19:45", "19:50", 391, 5, sectionSlackMinutes = 15)
        )
    )

    // 12071 CSMT - Jalna Jan Shatabdi Express
    val trainJanShatabdi12071 = TrainJourney(
        trainNumber = "12071",
        trainName = "CSMT - Jalna Jan Shatabdi",
        trainType = TrainType.SHATABDI,
        source = CSMT,
        destination = J,
        stops = listOf(
            ScheduleStop(CSMT, "12:10", "12:10", 0, 0),
            ScheduleStop(DR, "12:23", "12:25", 9, 2, sectionSlackMinutes = 2),
            ScheduleStop(TNA, "12:43", "12:45", 34, 2, sectionSlackMinutes = 3),
            ScheduleStop(KYN, "13:05", "13:07", 54, 2, sectionSlackMinutes = 4),
            ScheduleStop(IGP, "14:48", "14:50", 137, 2, sectionSlackMinutes = 10),
            ScheduleStop(DVL, "15:40", "15:42", 179, 2, sectionSlackMinutes = 3),
            ScheduleStop(NK, "15:52", "15:55", 188, 3, sectionSlackMinutes = 6),
            ScheduleStop(MMR, "17:00", "17:03", 261, 3, sectionSlackMinutes = 10)
        )
    )

    // 12105 Vidarbha Superfast Express
    val trainVidarbha12105 = TrainJourney(
        trainNumber = "12105",
        trainName = "Vidarbha Superfast Express",
        trainType = TrainType.SUPERFAST,
        source = CSMT,
        destination = G,
        stops = listOf(
            ScheduleStop(CSMT, "19:05", "19:05", 0, 0),
            ScheduleStop(DR, "19:18", "19:20", 9, 2, sectionSlackMinutes = 2),
            ScheduleStop(TNA, "19:38", "19:40", 34, 2, sectionSlackMinutes = 3),
            ScheduleStop(KYN, "20:02", "20:05", 54, 3, sectionSlackMinutes = 4),
            ScheduleStop(IGP, "21:35", "21:40", 137, 5, sectionSlackMinutes = 12),
            ScheduleStop(NK, "22:12", "22:15", 188, 3, sectionSlackMinutes = 8),
            ScheduleStop(MMR, "23:05", "23:08", 261, 3, sectionSlackMinutes = 10),
            ScheduleStop(CSN, "23:50", "23:52", 328, 2, sectionSlackMinutes = 8),
            ScheduleStop(JL, "00:40", "00:42", 421, 2, sectionSlackMinutes = 8),
            ScheduleStop(BSL, "01:25", "01:30", 445, 5, sectionSlackMinutes = 15)
        )
    )

    // All active trains passing Nashik Road (NK)
    val allNashikTrains = listOf(
        trainVandeBharat22223,
        trainPanchavati12109,
        trainPunjabMail12137,
        trainGitanjali12859,
        trainMangala12617,
        trainJanShatabdi12071,
        trainVidarbha12105
    )

    // Real-world Connecting Services at Nashik Road and adjacent corridor junctions
    val nashikConnectingServices = listOf(
        ConnectingService("MSRTC-TRIM", "Trimbakeshwar Temple Pilgrim Bus", ServiceType.BUS, "NK", "10:00", "Bay 1 (Pf 1 Exit)"),
        ConnectingService("EV-OZAR", "Ozar Nashik Airport Prepaid Cab", ServiceType.CAB, "NK", "09:50", "Circulating Area Counter"),
        ConnectingService("CITY-RAMKUND", "Panchavati Heritage City Bus", ServiceType.BUS, "NK", "09:45", "Nashik Road Depot"),
        ConnectingService("CITY-CBS", "CBS Central Bus Stand AC Shuttle", ServiceType.BUS, "NK", "10:15", "Main Station Exit"),
        ConnectingService("CAB-MIDC", "Satpur & Ambad MIDC Express Taxi", ServiceType.CAB, "NK", "10:05", "Exit Gate 2"),
        ConnectingService("12110", "Panchavati Express (Towards CSMT)", ServiceType.TRAIN, "NK", "07:02", "Pf 3"),
        ConnectingService("11071", "Kamayani Express (Towards Varanasi)", ServiceType.TRAIN, "NK", "17:15", "Pf 1"),
        ConnectingService("17617", "Tapovan Express (Towards Nanded)", ServiceType.TRAIN, "NK", "10:15", "Pf 2"),
        ConnectingService("KASARA-SHUTTLE", "Igatpuri - Kasara Ghat Shuttle", ServiceType.TRAIN, "IGP", "09:15", "Pf 4"),
        ConnectingService("CAB-BHANDARDARA", "Bhandardara Lake & Kalsubai Taxi", ServiceType.CAB, "IGP", "09:00", "Station Stand"),
        ConnectingService("51501", "Manmad - Shirdi Fast Passenger", ServiceType.TRAIN, "MMR", "11:15", "Pf 5"),
        ConnectingService("17687", "Marathwada Express (to Aurangabad)", ServiceType.TRAIN, "MMR", "11:30", "Pf 4"),
        ConnectingService("12123", "Deccan Queen Express (to Pune)", ServiceType.TRAIN, "KYN", "08:15", "Pf 5"),
        ConnectingService("10103", "Mandovi Express (to Madgaon/Goa)", ServiceType.TRAIN, "KYN", "08:35", "Pf 6")
    )
}
