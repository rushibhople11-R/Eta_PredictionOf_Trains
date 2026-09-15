package org.example.project.model

enum class TrainType(val displayName: String, val priorityScore: Int) {
    VANDE_BHARAT("Vande Bharat", 10),
    RAJDHANI("Rajdhani Express", 9),
    SHATABDI("Shatabdi Express", 8),
    SUPERFAST("Superfast Express", 6),
    MAIL_EXPRESS("Mail / Express", 4),
    PASSENGER("Passenger / Local", 2)
}

enum class CascadeRisk(val label: String, val colorHex: Long) {
    RECOVERING("Recovering Time", 0xFF00E676),      // Green
    STABLE("Stable Delay", 0xFF00E5FF),           // Cyan
    MODERATE("Moderate Risk", 0xFFFFB300),         // Amber
    HIGH("High Cascading Risk", 0xFFFF5252)       // Red
}

data class Station(
    val code: String,
    val name: String,
    val platformCount: Int = 4,
    val isJunction: Boolean = false,
    val division: String = "NR"
)

data class ScheduleStop(
    val station: Station,
    val scheduledArrival: String,       // e.g. "16:55"
    val scheduledDeparture: String,     // e.g. "17:00"
    val distanceKm: Int,
    val scheduledDwellMinutes: Int,
    val sectionSlackMinutes: Int = 0,   // Built-in engineering recovery buffer before this station
    val speedLimitKmph: Int = 130,
    val isSingleTrackSegment: Boolean = false,
    val junctionCongestionFactor: Double = 1.0 // 1.0 = normal, >1.0 = junction conflict risk
)

data class StopPrediction(
    val stop: ScheduleStop,
    val scheduledTime: String,
    val predictedEta: String,
    val delayMinutes: Int,
    val confidenceLowMinutes: Int,
    val confidenceHighMinutes: Int,
    val recoveryMinutes: Int,
    val cascadeRisk: CascadeRisk,
    val explanation: String,
    val isCurrentOrPassed: Boolean = false,
    val isCurrentStation: Boolean = false
)

data class TrainJourney(
    val trainNumber: String,
    val trainName: String,
    val trainType: TrainType,
    val source: Station,
    val destination: Station,
    val stops: List<ScheduleStop>
)
