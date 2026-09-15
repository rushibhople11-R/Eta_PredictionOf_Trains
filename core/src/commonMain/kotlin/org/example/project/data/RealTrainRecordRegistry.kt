package org.example.project.data

import org.example.project.model.Station
import org.example.project.model.TrainType

data class RealStopRecord(
    val stationCode: String,
    val stationName: String,
    val platform: String,
    val scheduledArrival: String,
    val scheduledDeparture: String,
    val actualArrivalOrEta: String,
    val delayMinutes: Int,
    val status: String, // "DEPARTED", "CURRENT", "UPCOMING"
    val distanceFromSourceKm: Int
)

data class RealTrainProfile(
    val trainNumber: String,
    val trainName: String,
    val trainType: TrainType,
    val operatingZone: String,
    val source: Station,
    val destination: Station,
    val totalDistanceKm: Int,
    val averageSpeedKmh: Int,
    val maxPermissibleSpeedKmh: Int,
    val locomotiveClass: String,
    val locomotiveShed: String,
    val rakeType: String,
    val onTimePunctuality30Days: Double,
    val commonDelayHotspots: List<String>,
    val stops: List<RealStopRecord>
)

object RealTrainRecordRegistry {

    private val csmt = Station("CSMT", "Mumbai Chhatrapati Shivaji Maharaj Terminus", 18, true, "CR")
    private val dr = Station("DR", "Dadar Central", 8, true, "CR")
    private val tna = Station("TNA", "Thane", 10, true, "CR")
    private val kyn = Station("KYN", "Kalyan Junction", 8, true, "CR")
    private val ksra = Station("KSRA", "Kasara (Thal Ghat Foot)", 4, false, "CR")
    private val igp = Station("IGP", "Igatpuri (Thal Ghat Top)", 4, true, "CR")
    private val dvl = Station("DVL", "Devlali", 3, false, "CR")
    private val nk = Station("NK", "Nashik Road", 4, true, "CR")
    private val odh = Station("ODH", "Odha", 2, false, "CR")
    private val lsr = Station("LSR", "Lasalgaon", 2, false, "CR")
    private val mmr = Station("MMR", "Manmad Junction", 6, true, "CR")
    private val bsl = Station("BSL", "Bhusawal Junction", 8, true, "CR")
    private val ngp = Station("NGP", "Nagpur Junction", 8, true, "CR")
    private val hwh = Station("HWH", "Howrah Junction", 23, true, "ER")
    private val ndls = Station("NDLS", "New Delhi", 16, true, "NR")
    private val fzr = Station("FZR", "Firozpur Cantt", 5, true, "NR")
    private val snsi = Station("SNSI", "Sainagar Shirdi", 3, false, "CR")

    private val masterTrainList = listOf(
        RealTrainProfile(
            trainNumber = "12109",
            trainName = "Panchavati Superfast Express",
            trainType = TrainType.SUPERFAST,
            operatingZone = "Central Railway (CR)",
            source = csmt,
            destination = mmr,
            totalDistanceKm = 260,
            averageSpeedKmh = 62,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "WAP-7 #30482 (3-Phase AC Electric)",
            locomotiveShed = "Kalyan (KYN)",
            rakeType = "22 LHB Coaches (Push-Pull Compatible)",
            onTimePunctuality30Days = 91.2,
            commonDelayHotspots = listOf("Kalyan Suburban Confluence", "Thal Ghat (Kasara - Igatpuri)", "Devlali - Odha Section"),
            stops = listOf(
                RealStopRecord("CSMT", "Mumbai CSMT", "PF 14", "18:15", "18:15", "18:15", 0, "DEPARTED", 0),
                RealStopRecord("DR", "Dadar Central", "PF 4", "18:28", "18:30", "18:30", 0, "DEPARTED", 9),
                RealStopRecord("KYN", "Kalyan Jn", "PF 4", "19:07", "19:10", "19:16", 6, "DEPARTED", 54),
                RealStopRecord("KSRA", "Kasara", "PF 1", "20:06", "20:08", "20:20", 12, "DEPARTED", 121),
                RealStopRecord("IGP", "Igatpuri", "PF 2", "20:40", "20:45", "21:01", 16, "DEPARTED", 137),
                RealStopRecord("DVL", "Devlali", "PF 2", "21:28", "21:30", "21:48", 18, "CURRENT", 182),
                RealStopRecord("NK", "Nashik Road", "PF 2", "21:38", "21:40", "22:01", 21, "UPCOMING", 188),
                RealStopRecord("ODH", "Odha", "PF 1", "21:50", "21:51", "22:15", 24, "UPCOMING", 200),
                RealStopRecord("LSR", "Lasalgaon", "PF 1", "22:20", "22:22", "22:42", 20, "UPCOMING", 235),
                RealStopRecord("MMR", "Manmad Jn", "PF 3", "23:05", "23:05", "23:23", 18, "UPCOMING", 260)
            )
        ),
        RealTrainProfile(
            trainNumber = "22223",
            trainName = "Mumbai CSMT - Sainagar Shirdi Vande Bharat Express",
            trainType = TrainType.VANDE_BHARAT,
            operatingZone = "Central Railway (CR)",
            source = csmt,
            destination = snsi,
            totalDistanceKm = 343,
            averageSpeedKmh = 66,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "Train-18 EMU Self-Propelled Trainset",
            locomotiveShed = "Wadi Bunder / KYN",
            rakeType = "16 Car Vande Bharat Aerodynamic Trainset",
            onTimePunctuality30Days = 96.8,
            commonDelayHotspots = listOf("Kalyan Outer", "Puntamba Chord Line"),
            stops = listOf(
                RealStopRecord("CSMT", "Mumbai CSMT", "PF 18", "06:20", "06:20", "06:20", 0, "DEPARTED", 0),
                RealStopRecord("DR", "Dadar Central", "PF 6", "06:30", "06:32", "06:32", 0, "DEPARTED", 9),
                RealStopRecord("TNA", "Thane", "PF 5", "06:49", "06:51", "06:51", 0, "DEPARTED", 33),
                RealStopRecord("NK", "Nashik Road", "PF 1", "08:57", "08:59", "08:59", 0, "CURRENT", 188),
                RealStopRecord("MMR", "Manmad Jn", "PF 1", "09:43", "09:45", "09:45", 0, "UPCOMING", 260),
                RealStopRecord("SNSI", "Sainagar Shirdi", "PF 1", "11:40", "11:40", "11:40", 0, "UPCOMING", 343)
            )
        ),
        RealTrainProfile(
            trainNumber = "12951",
            trainName = "Mumbai Central - New Delhi Tejas Rajdhani Express",
            trainType = TrainType.RAJDHANI,
            operatingZone = "Western Railway (WR)",
            source = Station("MMCT", "Mumbai Central", 5, true, "WR"),
            destination = ndls,
            totalDistanceKm = 1386,
            averageSpeedKmh = 89,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "WAP-7 #30514 (HOG Enabled)",
            locomotiveShed = "Vadodara (BRC)",
            rakeType = "21 Smart Tejas Sleeper LHB Coaches",
            onTimePunctuality30Days = 97.4,
            commonDelayHotspots = listOf("Mathura Junction Signal Confluence"),
            stops = listOf(
                RealStopRecord("MMCT", "Mumbai Central", "PF 1", "17:00", "17:00", "17:00", 0, "DEPARTED", 0),
                RealStopRecord("BVI", "Borivali", "PF 6", "17:22", "17:24", "17:24", 0, "DEPARTED", 30),
                RealStopRecord("ST", "Surat", "PF 1", "19:43", "19:48", "19:49", 1, "DEPARTED", 263),
                RealStopRecord("BRC", "Vadodara Jn", "PF 2", "21:06", "21:16", "21:16", 0, "DEPARTED", 392),
                RealStopRecord("RTM", "Ratlam Jn", "PF 5", "00:25", "00:28", "00:28", 0, "DEPARTED", 653),
                RealStopRecord("KOTA", "Kota Jn", "PF 1", "03:15", "03:20", "03:20", 0, "CURRENT", 920),
                RealStopRecord("NDLS", "New Delhi", "PF 2", "08:32", "08:32", "08:32", 0, "UPCOMING", 1386)
            )
        ),
        RealTrainProfile(
            trainNumber = "12137",
            trainName = "Punjab Mail",
            trainType = TrainType.SUPERFAST,
            operatingZone = "Central Railway (CR)",
            source = csmt,
            destination = fzr,
            totalDistanceKm = 1930,
            averageSpeedKmh = 58,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "WAP-7 #30299",
            locomotiveShed = "Ajni (AQ) / Kalyan",
            rakeType = "24 LHB Coaches",
            onTimePunctuality30Days = 84.6,
            commonDelayHotspots = listOf("Thal Ghat", "Bhusawal Yard", "Bhopal Section", "Delhi Area"),
            stops = listOf(
                RealStopRecord("CSMT", "Mumbai CSMT", "PF 17", "19:35", "19:35", "19:35", 0, "DEPARTED", 0),
                RealStopRecord("DR", "Dadar Central", "PF 4", "19:47", "19:50", "19:50", 0, "DEPARTED", 9),
                RealStopRecord("KYN", "Kalyan Jn", "PF 4", "20:32", "20:35", "20:41", 6, "DEPARTED", 54),
                RealStopRecord("KSRA", "Kasara", "PF 1", "21:42", "21:45", "22:01", 16, "DEPARTED", 121),
                RealStopRecord("IGP", "Igatpuri", "PF 2", "22:20", "22:25", "22:47", 22, "CURRENT", 137),
                RealStopRecord("NK", "Nashik Road", "PF 2", "23:10", "23:15", "23:41", 26, "UPCOMING", 188),
                RealStopRecord("MMR", "Manmad Jn", "PF 2", "00:15", "00:20", "00:44", 24, "UPCOMING", 260),
                RealStopRecord("BSL", "Bhusawal Jn", "PF 6", "02:30", "02:35", "02:55", 20, "UPCOMING", 444)
            )
        ),
        RealTrainProfile(
            trainNumber = "12139",
            trainName = "Sevagram Superfast Express",
            trainType = TrainType.SUPERFAST,
            operatingZone = "Central Railway (CR)",
            source = csmt,
            destination = ngp,
            totalDistanceKm = 837,
            averageSpeedKmh = 56,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "WAP-7 #30345",
            locomotiveShed = "Ajni (AQ)",
            rakeType = "22 LHB Coaches",
            onTimePunctuality30Days = 88.0,
            commonDelayHotspots = listOf("Thal Ghat Banking Detach", "Badnera Junction"),
            stops = listOf(
                RealStopRecord("CSMT", "Mumbai CSMT", "PF 16", "14:55", "14:55", "14:55", 0, "DEPARTED", 0),
                RealStopRecord("DR", "Dadar Central", "PF 4", "15:07", "15:10", "15:10", 0, "DEPARTED", 9),
                RealStopRecord("TNA", "Thane", "PF 5", "15:30", "15:32", "15:36", 4, "DEPARTED", 33),
                RealStopRecord("KYN", "Kalyan Jn", "PF 4", "15:52", "15:55", "16:03", 8, "DEPARTED", 54),
                RealStopRecord("IGP", "Igatpuri", "PF 2", "17:40", "17:45", "17:57", 12, "DEPARTED", 137),
                RealStopRecord("DVL", "Devlali", "PF 2", "18:28", "18:30", "18:43", 13, "CURRENT", 182),
                RealStopRecord("NK", "Nashik Road", "PF 2", "18:40", "18:45", "18:59", 14, "UPCOMING", 188),
                RealStopRecord("MMR", "Manmad Jn", "PF 2", "19:40", "19:45", "19:57", 12, "UPCOMING", 260)
            )
        ),
        RealTrainProfile(
            trainNumber = "12859",
            trainName = "Gitanjali Express",
            trainType = TrainType.SUPERFAST,
            operatingZone = "South Eastern Railway / CR",
            source = csmt,
            destination = hwh,
            totalDistanceKm = 1968,
            averageSpeedKmh = 63,
            maxPermissibleSpeedKmh = 130,
            locomotiveClass = "WAP-7 #30411",
            locomotiveShed = "Santragachi (SRC)",
            rakeType = "22 LHB Coaches",
            onTimePunctuality30Days = 82.5,
            commonDelayHotspots = listOf("Kalyan Confluence", "Nagpur Yard", "Kharagpur"),
            stops = listOf(
                RealStopRecord("CSMT", "Mumbai CSMT", "PF 18", "06:00", "06:00", "06:00", 0, "DEPARTED", 0),
                RealStopRecord("DR", "Dadar Central", "PF 4", "06:12", "06:15", "06:15", 0, "DEPARTED", 9),
                RealStopRecord("KYN", "Kalyan Jn", "PF 4", "06:52", "06:55", "07:05", 10, "DEPARTED", 54),
                RealStopRecord("IGP", "Igatpuri", "PF 2", "08:42", "08:45", "09:00", 15, "DEPARTED", 137),
                RealStopRecord("NK", "Nashik Road", "PF 2", "09:25", "09:30", "09:48", 18, "CURRENT", 188),
                RealStopRecord("MMR", "Manmad Jn", "PF 2", "10:28", "10:30", "10:48", 18, "UPCOMING", 260),
                RealStopRecord("BSL", "Bhusawal Jn", "PF 6", "12:40", "12:45", "13:00", 15, "UPCOMING", 444)
            )
        )
    )

    fun getAllRealTrains(): List<RealTrainProfile> = masterTrainList

    fun getTrainByNumber(trainNumber: String): RealTrainProfile? {
        val cleanNumber = trainNumber.trim()
        return masterTrainList.find { it.trainNumber == cleanNumber }
            ?: masterTrainList.find { it.trainNumber.contains(cleanNumber) }
    }

    fun searchTrains(query: String): List<RealTrainProfile> {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return masterTrainList
        return masterTrainList.filter {
            it.trainNumber.lowercase().contains(clean) ||
            it.trainName.lowercase().contains(clean) ||
            it.source.code.lowercase().contains(clean) ||
            it.destination.code.lowercase().contains(clean) ||
            it.source.name.lowercase().contains(clean) ||
            it.destination.name.lowercase().contains(clean)
        }
    }
}
