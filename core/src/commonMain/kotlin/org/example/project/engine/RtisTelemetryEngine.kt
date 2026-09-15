package org.example.project.engine

import org.example.project.data.RealTrainRecordRegistry

enum class SignalAspect(val label: String, val colorHex: Long, val symbol: String) {
    GREEN_PROCEED("PROCEED (GREEN)", 0xFF10B981, "🟢"),
    DOUBLE_YELLOW_ATTENTION("ATTENTION (DOUBLE YELLOW)", 0xFFF59E0B, "🟡🟡"),
    YELLOW_CAUTION("CAUTION (YELLOW)", 0xFFEAB308, "🟡"),
    RED_STOP("DANGER (RED)", 0xFFEF4444, "🔴")
}

data class RtisTelemetrySnapshot(
    val trainNumber: String,
    val timestampEpoch: Long,
    val timestampFormatted: String,
    val latitude: Double,
    val longitude: Double,
    val currentSpeedKmh: Double,
    val maxPermissibleSpeedKmh: Double,
    val kilometerPost: String,
    val blockSectionName: String,
    val signalAspect: SignalAspect,
    val throttleNotch: String,
    val tractionCurrentAmps: Double,
    val brakePipePressureKgCm2: Double,
    val satelliteNavIcStatus: String,
    val nextStationCode: String,
    val nextStationDistanceKm: Double,
    val estimatedTimeToNextMinutes: Int
)

object RtisTelemetryEngine {

    fun generateLiveTelemetry(trainNumber: String, tickOffset: Int = 0): RtisTelemetrySnapshot {
        val profile = RealTrainRecordRegistry.getTrainByNumber(trainNumber)
            ?: RealTrainRecordRegistry.getTrainByNumber("12109")!!

        // Realistic variation based on train number and tick offset
        val tick = (tickOffset % 4)
        val speeds = when (trainNumber) {
            "22223" -> listOf(118.0, 122.0, 128.0, 110.0) // Vande Bharat
            "12951" -> listOf(125.0, 129.0, 130.0, 120.0) // Rajdhani
            "12109" -> listOf(98.0, 102.0, 84.0, 92.0)    // Panchavati
            else -> listOf(88.0, 94.0, 78.0, 85.0)
        }
        val currentSpeed = speeds[tick]

        val aspects = when (tick) {
            0 -> SignalAspect.GREEN_PROCEED
            1 -> SignalAspect.GREEN_PROCEED
            2 -> SignalAspect.DOUBLE_YELLOW_ATTENTION
            else -> SignalAspect.YELLOW_CAUTION
        }

        val kmPosts = when (trainNumber) {
            "12109" -> listOf("KM 184/12", "KM 185/20", "KM 186/14", "KM 187/08")
            "22223" -> listOf("KM 198/04", "KM 200/12", "KM 202/18", "KM 204/02")
            else -> listOf("KM 142/18", "KM 144/06", "KM 145/22", "KM 147/10")
        }

        val blocks = when (trainNumber) {
            "12109" -> listOf(
                "Devlali - Nashik Road Down Auto Block 184",
                "Devlali - Nashik Road Down Auto Block 185",
                "Approaching Nashik Road Outer Signal",
                "Nashik Road Yard Home Signal Circuit"
            )
            "22223" -> listOf(
                "Nashik Road - Odha Down Section 198",
                "Odha Up Line Loop Divergence Section",
                "Odha - Kherwadi Single Line Precedence Block",
                "Kherwadi Main High-Speed Section"
            )
            else -> listOf(
                "Igatpuri Down Departure Yard Block",
                "Padli - Asvali Sectional Auto Circuit",
                "Lahavit Station Approach Block",
                "Devlali Approach Track Circuit"
            )
        }

        val currentStop = profile.stops.find { it.status == "CURRENT" } ?: profile.stops.first()
        val upcomingStop = profile.stops.find { it.status == "UPCOMING" } ?: profile.stops.last()

        val nowEpoch = System.currentTimeMillis() + (tickOffset * 30_000L)
        val ist = org.example.project.model.IndianStandardTime.fromEpochMillis(nowEpoch)

        return RtisTelemetrySnapshot(
            trainNumber = profile.trainNumber,
            timestampEpoch = nowEpoch,
            timestampFormatted = ist.compactBadge,
            latitude = 19.9975 + (tick * 0.0035),
            longitude = 73.7898 + (tick * 0.0042),
            currentSpeedKmh = currentSpeed,
            maxPermissibleSpeedKmh = profile.maxPermissibleSpeedKmh.toDouble(),
            kilometerPost = kmPosts[tick],
            blockSectionName = blocks[tick],
            signalAspect = aspects,
            throttleNotch = if (currentSpeed > 100) "Notch 7 (Cruising)" else "Notch 4 (Regulating)",
            tractionCurrentAmps = 580.0 + (tick * 25.0),
            brakePipePressureKgCm2 = 5.0,
            satelliteNavIcStatus = "LOCKED • 9 SATELLITES (ISRO NAVIC RTIS)",
            nextStationCode = upcomingStop.stationCode,
            nextStationDistanceKm = 6.4 - (tick * 1.2),
            estimatedTimeToNextMinutes = upcomingStop.delayMinutes.coerceAtLeast(3)
        )
    }
}
