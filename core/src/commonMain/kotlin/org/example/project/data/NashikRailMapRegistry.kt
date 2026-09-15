package org.example.project.data

/**
 * Geographic GPS point for Railway GIS mapping.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double
)

/**
 * Railway Station node on the Nashik City corridor.
 */
data class NashikStationGeoNode(
    val code: String,
    val name: String,
    val marathiName: String,
    val location: GeoPoint,
    val elevationMeters: Int,
    val platformsCount: Int,
    val isJunction: Boolean,
    val stationCategory: String, // "NSG-2" (Nashik Road), "NSG-4" (Devlali), "NSG-3" (Manmad), etc.
    val tracksCount: Int,
    val description: String,
    val features: List<String>
)

/**
 * Railway Section (Block section) connecting two stations in the Nashik division.
 */
data class NashikRailSection(
    val sectionId: String,
    val fromStationCode: String,
    val toStationCode: String,
    val name: String,
    val lengthKm: Double,
    val trackType: String, // "Double Electrified (25kV AC)", "Double + Loop Line"
    val signalingSystem: String, // "AUTOMATIC_BLOCK", "ABSOLUTE_BLOCK"
    val maxPermissibleSpeedKmph: Int,
    val temporarySpeedRestrictionKmph: Int? = null,
    val lineSaturationPercent: Int,
    val currentSignalAspect: String, // "GREEN (Proceed)", "DOUBLE_YELLOW (Attention)", "YELLOW (Caution)", "RED (Danger)"
    val status: String, // "CLEAR", "CAUTION", "OCCUPIED"
    val pathPoints: List<GeoPoint>
)

/**
 * Live train marker positioned in real-time on the Nashik rail map.
 */
data class NashikLiveTrainMarker(
    val trainNumber: String,
    val trainName: String,
    val trainType: String, // "Superfast Express", "Vande Bharat", "Mail / Express", "Freight (BOXN)", "Freight (BTPN)"
    val locomotive: String,
    val currentPosition: GeoPoint,
    val headingDegrees: Double,
    val speedKmph: Int,
    val delayMinutes: Int,
    val currentSectionId: String,
    val nextStationCode: String,
    val estimatedArrivalAtNextStation: String,
    val priorityLevel: Int, // 1 = Vande Bharat, 2 = Mail/SF, 3 = Freight
    val statusText: String
)

/**
 * Registry of Nashik City and Corridor Rail GIS Network.
 */
object NashikRailMapRegistry {

    val STATIONS: List<NashikStationGeoNode> = listOf(
        NashikStationGeoNode(
            code = "IGP",
            name = "Igatpuri",
            marathiName = "इगतपुरी",
            location = GeoPoint(lat = 19.6983, lng = 73.5654),
            elevationMeters = 600,
            platformsCount = 4,
            isJunction = true,
            stationCategory = "NSG-3",
            tracksCount = 6,
            description = "Thal Ghat summit station with extensive locomotive reversal and banking loco sidings.",
            features = listOf("Banking Loco Attachment", "Brake Testing Siding", "Crew Lobby")
        ),
        NashikStationGeoNode(
            code = "GO",
            name = "Ghoti",
            marathiName = "घोbranch/घोटी",
            location = GeoPoint(lat = 19.7214, lng = 73.6543),
            elevationMeters = 590,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-6",
            tracksCount = 3,
            description = "Rural wayside station between Igatpuri and Devlali.",
            features = listOf("Passing Loop", "Block Section Signal Hut")
        ),
        NashikStationGeoNode(
            code = "ASO",
            name = "Asavali",
            marathiName = "आसावली",
            location = GeoPoint(lat = 19.7820, lng = 73.7120),
            elevationMeters = 582,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-6",
            tracksCount = 3,
            description = "Semi-urban crossing station approaching Nashik district.",
            features = listOf("Intermediate Block Signal", "Goods Siding")
        ),
        NashikStationGeoNode(
            code = "LT",
            name = "Lahavit",
            marathiName = "लहावीत",
            location = GeoPoint(lat = 19.8250, lng = 73.7460),
            elevationMeters = 575,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-6",
            tracksCount = 3,
            description = "Wayside station with automatic signal overlap.",
            features = listOf("Automatic Signal", "Traction Sub-Station")
        ),
        NashikStationGeoNode(
            code = "DVL",
            name = "Devlali",
            marathiName = "देवळाली",
            location = GeoPoint(lat = 19.9242, lng = 73.8310),
            elevationMeters = 570,
            platformsCount = 3,
            isJunction = false,
            stationCategory = "NSG-4",
            tracksCount = 5,
            description = "Historic military cantonment station with Army School of Artillery sidings and Kisan Rail loading docks.",
            features = listOf("Military Siding", "Kisan Rail Hub", "Loop Line Regulation Ready")
        ),
        NashikStationGeoNode(
            code = "NK",
            name = "Nashik Road",
            marathiName = "नाशिक रोड",
            location = GeoPoint(lat = 19.9575, lng = 73.8340),
            elevationMeters = 565,
            platformsCount = 4,
            isJunction = false,
            stationCategory = "NSG-2",
            tracksCount = 7,
            description = "Principal mega-station serving Nashik Metropolitan City. 4 high-level platforms, automatic block interlocking, 3 foot-overbridges.",
            features = listOf("Kumbh Mela High-Capacity Concourse", "Automatic Signalling", "4 High-Level Platforms", "Pit Line / Stabling")
        ),
        NashikStationGeoNode(
            code = "ODH",
            name = "Odha",
            marathiName = "ओढा",
            location = GeoPoint(lat = 20.0210, lng = 73.9110),
            elevationMeters = 560,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-5",
            tracksCount = 4,
            description = "Godavari River Bridge approach zone. Bottleneck sector with bridge regulation signaling.",
            features = listOf("Godavari Major Bridge 214/1", "Single-span Caution Zone", "Speed Restriction 30 km/h")
        ),
        NashikStationGeoNode(
            code = "KW",
            name = "Kherwadi",
            marathiName = "खेरवाडी",
            location = GeoPoint(lat = 20.0780, lng = 73.9780),
            elevationMeters = 555,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-6",
            tracksCount = 3,
            description = "Agricultural crossing station between Odha and Niphad.",
            features = listOf("Loop Line", "Auto Block Distant Signal")
        ),
        NashikStationGeoNode(
            code = "KBSN",
            name = "Kasbe Sukene",
            marathiName = "कसबे सुकेणे",
            location = GeoPoint(lat = 20.1250, lng = 74.0320),
            elevationMeters = 550,
            platformsCount = 2,
            isJunction = false,
            stationCategory = "NSG-6",
            tracksCount = 3,
            description = "Vineyard belt station handling grape/onion agro rakes.",
            features = listOf("Goods Loop", "Intermediate Block Signal")
        ),
        NashikStationGeoNode(
            code = "NR",
            name = "Niphad",
            marathiName = "निफाड",
            location = GeoPoint(lat = 20.0864, lng = 74.1086),
            elevationMeters = 548,
            platformsCount = 3,
            isJunction = false,
            stationCategory = "NSG-5",
            tracksCount = 4,
            description = "Major agro-industrial station for onion, wheat, and sugarcane transport.",
            features = listOf("Agro Goods Terminal", "Kisan Express Stoppage", "Platform 1-3")
        ),
        NashikStationGeoNode(
            code = "LS",
            name = "Lasalgaon",
            marathiName = "लासलगाव",
            location = GeoPoint(lat = 20.1448, lng = 74.2389),
            elevationMeters = 545,
            platformsCount = 3,
            isJunction = false,
            stationCategory = "NSG-4",
            tracksCount = 5,
            description = "Asia's biggest onion wholesale market hub with heavy freight loading railhead.",
            features = listOf("Asia's Largest Onion Railhead", "BOXN Rake Siding", "High Speed Loop")
        ),
        NashikStationGeoNode(
            code = "MMR",
            name = "Manmad Junction",
            marathiName = "मनमाड जंक्शन",
            location = GeoPoint(lat = 20.2520, lng = 74.4370),
            elevationMeters = 580,
            platformsCount = 6,
            isJunction = true,
            stationCategory = "NSG-3",
            tracksCount = 12,
            description = "Central Railway mega-junction linking Mumbai-Howrah, Mumbai-Delhi, and South Central Railway (Nanded/Secunderabad line).",
            features = listOf("6 Platforms", "IOCL / BPCL Oil Siding", "WDM/WAG Diesel & Electric Trip Shed")
        )
    )

    val SECTIONS: List<NashikRailSection> = listOf(
        NashikRailSection(
            sectionId = "SEC-IGP-DVL",
            fromStationCode = "IGP",
            toStationCode = "DVL",
            name = "Igatpuri - Devlali Mainline",
            lengthKm = 35.8,
            trackType = "Double Electrified (25kV AC)",
            signalingSystem = "ABSOLUTE_BLOCK",
            maxPermissibleSpeedKmph = 110,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 78,
            currentSignalAspect = "GREEN (Proceed)",
            status = "CLEAR",
            pathPoints = listOf(
                GeoPoint(19.6983, 73.5654),
                GeoPoint(19.7214, 73.6543),
                GeoPoint(19.7820, 73.7120),
                GeoPoint(19.8250, 73.7460),
                GeoPoint(19.9242, 73.8310)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-DVL-NK",
            fromStationCode = "DVL",
            toStationCode = "NK",
            name = "Devlali - Nashik Road Urban Sector",
            lengthKm = 4.8,
            trackType = "Double Electrified + Loop Lines",
            signalingSystem = "AUTOMATIC_BLOCK",
            maxPermissibleSpeedKmph = 110,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 92,
            currentSignalAspect = "DOUBLE_YELLOW (Attention)",
            status = "OCCUPIED",
            pathPoints = listOf(
                GeoPoint(19.9242, 73.8310),
                GeoPoint(19.9390, 73.8325),
                GeoPoint(19.9575, 73.8340)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-NK-ODH",
            fromStationCode = "NK",
            toStationCode = "ODH",
            name = "Nashik Road - Odha (Godavari Crossing)",
            lengthKm = 10.2,
            trackType = "Double Electrified Mainline",
            signalingSystem = "AUTOMATIC_BLOCK",
            maxPermissibleSpeedKmph = 100,
            temporarySpeedRestrictionKmph = 30, // Bridge maintenance TSR
            lineSaturationPercent = 88,
            currentSignalAspect = "YELLOW (Caution)",
            status = "CAUTION",
            pathPoints = listOf(
                GeoPoint(19.9575, 73.8340),
                GeoPoint(19.9790, 73.8580),
                GeoPoint(19.9950, 73.8820),
                GeoPoint(20.0210, 73.9110)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-ODH-KW",
            fromStationCode = "ODH",
            toStationCode = "KW",
            name = "Odha - Kherwadi Agricultural Segment",
            lengthKm = 8.5,
            trackType = "Double Electrified Mainline",
            signalingSystem = "AUTOMATIC_BLOCK",
            maxPermissibleSpeedKmph = 110,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 64,
            currentSignalAspect = "GREEN (Proceed)",
            status = "CLEAR",
            pathPoints = listOf(
                GeoPoint(20.0210, 73.9110),
                GeoPoint(20.0510, 73.9450),
                GeoPoint(20.0780, 73.9780)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-KW-KBSN",
            fromStationCode = "KW",
            toStationCode = "KBSN",
            name = "Kherwadi - Kasbe Sukene Segment",
            lengthKm = 7.2,
            trackType = "Double Electrified Mainline",
            signalingSystem = "ABSOLUTE_BLOCK",
            maxPermissibleSpeedKmph = 110,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 70,
            currentSignalAspect = "GREEN (Proceed)",
            status = "CLEAR",
            pathPoints = listOf(
                GeoPoint(20.0780, 73.9780),
                GeoPoint(20.1010, 74.0050),
                GeoPoint(20.1250, 74.0320)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-KBSN-NR",
            fromStationCode = "KBSN",
            toStationCode = "NR",
            name = "Kasbe Sukene - Niphad Agro Line",
            lengthKm = 8.6,
            trackType = "Double Electrified Mainline",
            signalingSystem = "ABSOLUTE_BLOCK",
            maxPermissibleSpeedKmph = 120,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 74,
            currentSignalAspect = "GREEN (Proceed)",
            status = "CLEAR",
            pathPoints = listOf(
                GeoPoint(20.1250, 74.0320),
                GeoPoint(20.1050, 74.0700),
                GeoPoint(20.0864, 74.1086)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-NR-LS",
            fromStationCode = "NR",
            toStationCode = "LS",
            name = "Niphad - Lasalgaon Onion Corridor",
            lengthKm = 14.1,
            trackType = "Double Electrified Mainline",
            signalingSystem = "ABSOLUTE_BLOCK",
            maxPermissibleSpeedKmph = 120,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 82,
            currentSignalAspect = "GREEN (Proceed)",
            status = "CLEAR",
            pathPoints = listOf(
                GeoPoint(20.0864, 74.1086),
                GeoPoint(20.1150, 74.1730),
                GeoPoint(20.1448, 74.2389)
            )
        ),
        NashikRailSection(
            sectionId = "SEC-LS-MMR",
            fromStationCode = "LS",
            toStationCode = "MMR",
            name = "Lasalgaon - Manmad Junction Approach",
            lengthKm = 25.3,
            trackType = "Double Electrified + Yard Approaches",
            signalingSystem = "ABSOLUTE_BLOCK (Route Relay Interlocking)",
            maxPermissibleSpeedKmph = 110,
            temporarySpeedRestrictionKmph = null,
            lineSaturationPercent = 94,
            currentSignalAspect = "DOUBLE_YELLOW (Attention)",
            status = "CAUTION",
            pathPoints = listOf(
                GeoPoint(20.1448, 74.2389),
                GeoPoint(20.1980, 74.3380),
                GeoPoint(20.2520, 74.4370)
            )
        )
    )

    val LIVE_TRAINS: List<NashikLiveTrainMarker> = listOf(
        NashikLiveTrainMarker(
            trainNumber = "12109",
            trainName = "Panchavati Express",
            trainType = "Superfast Intercity",
            locomotive = "WAP-7 #30482 (KYN Shed)",
            currentPosition = GeoPoint(lat = 19.9540, lng = 73.8335), // Approaching Nashik Road PF-2
            headingDegrees = 40.0,
            speedKmph = 44,
            delayMinutes = 12,
            currentSectionId = "SEC-DVL-NK",
            nextStationCode = "NK",
            estimatedArrivalAtNextStation = "18:42",
            priorityLevel = 2,
            statusText = "Decelerating on home signal for Nashik Road PF-2. Speed 44 km/h."
        ),
        NashikLiveTrainMarker(
            trainNumber = "22223",
            trainName = "CSMT - Shirdi Vande Bharat",
            trainType = "Vande Bharat Express (Semi-High Speed)",
            locomotive = "Train-18 EMU Rake #14",
            currentPosition = GeoPoint(lat = 19.9120, lng = 73.8240), // Cruising through Devlali approach
            headingDegrees = 42.0,
            speedKmph = 108,
            delayMinutes = 0,
            currentSectionId = "SEC-IGP-DVL",
            nextStationCode = "DVL",
            estimatedArrivalAtNextStation = "18:50",
            priorityLevel = 1,
            statusText = "Cruising at top speed 108 km/h. Clear green aspect in block section."
        ),
        NashikLiveTrainMarker(
            trainNumber = "12137",
            trainName = "Punjab Mail",
            trainType = "Superfast Mail",
            locomotive = "WAP-7 #30211 (GZB Shed)",
            currentPosition = GeoPoint(lat = 20.0050, lng = 73.8950), // Negotiating Odha Bridge
            headingDegrees = 48.0,
            speedKmph = 28,
            delayMinutes = 8,
            currentSectionId = "SEC-NK-ODH",
            nextStationCode = "ODH",
            estimatedArrivalAtNextStation = "19:04",
            priorityLevel = 2,
            statusText = "Regulating over Godavari bridge caution zone at 28 km/h."
        ),
        NashikLiveTrainMarker(
            trainNumber = "12859",
            trainName = "Gitanjali Express",
            trainType = "Superfast Express",
            locomotive = "WAP-7 #37019 (SRC Shed)",
            currentPosition = GeoPoint(lat = 20.0890, lng = 74.1120), // Passing Niphad
            headingDegrees = 52.0,
            speedKmph = 104,
            delayMinutes = 22,
            currentSectionId = "SEC-KBSN-NR",
            nextStationCode = "NR",
            estimatedArrivalAtNextStation = "19:15",
            priorityLevel = 2,
            statusText = "Accelerating past Niphad agro zone. Right time run attempt."
        ),
        NashikLiveTrainMarker(
            trainNumber = "BOXN-78",
            trainName = "Lasalgaon Agro Freight (Onion Special)",
            trainType = "Freight (BOXNHL Rake)",
            locomotive = "WAG-9HC #31890 (BSL Shed)",
            currentPosition = GeoPoint(lat = 20.1460, lng = 74.2410), // Lasalgaon Siding
            headingDegrees = 0.0,
            speedKmph = 0,
            delayMinutes = 45,
            currentSectionId = "SEC-NR-LS",
            nextStationCode = "LS",
            estimatedArrivalAtNextStation = "Loading",
            priorityLevel = 3,
            statusText = "Stationary at Lasalgaon freight siding 3. Rake loading underway."
        ),
        NashikLiveTrainMarker(
            trainNumber = "BTPN-42",
            trainName = "HPCL Manmad POL Petroleum Tanker",
            trainType = "Freight (BTPN Tanker)",
            locomotive = "Twin WAG-9 #32104 (AQ Shed)",
            currentPosition = GeoPoint(lat = 20.1245, lng = 74.0315), // Loop Line at Kasbe Sukene
            headingDegrees = 225.0, // Downbound towards Mumbai
            speedKmph = 32,
            delayMinutes = 15,
            currentSectionId = "SEC-KW-KBSN",
            nextStationCode = "KBSN",
            estimatedArrivalAtNextStation = "19:12",
            priorityLevel = 3,
            statusText = "Regulated on loop line to give precedence to Vande Bharat."
        )
    )

    fun getStation(code: String): NashikStationGeoNode? =
        STATIONS.find { it.code.equals(code, ignoreCase = true) }

    fun getSection(sectionId: String): NashikRailSection? =
        SECTIONS.find { it.sectionId.equals(sectionId, ignoreCase = true) }

    fun getTrain(trainNumber: String): NashikLiveTrainMarker? =
        LIVE_TRAINS.find { it.trainNumber.equals(trainNumber, ignoreCase = true) }
}
