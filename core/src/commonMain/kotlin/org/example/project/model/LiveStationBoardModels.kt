package org.example.project.model

enum class TrainStationStatus(val label: String, val colorHex: Long) {
    ON_TIME("On Time", 0xFF00E676),                     // Green
    DELAYED("Delayed", 0xFFFFB300),                     // Amber
    HEAVY_DELAY("Heavy Delay", 0xFFFF5252),             // Red
    ARRIVING_NOW("Arriving on Platform", 0xFF00E5FF),   // Cyan
    IN_KASARA_GHAT("Clearing Thal Ghat", 0xFFFF9100),   // Orange (Ghat section)
    DEPARTED("Departed", 0xFF64748B)                    // Muted grey
}

enum class MovementType {
    ARRIVAL,
    DEPARTURE
}

data class StationTrainEntry(
    val trainNumber: String,
    val trainName: String,
    val trainType: TrainType,
    val origin: Station,
    val destination: Station,
    val scheduledArrival: String,     // e.g. "09:30"
    val scheduledDeparture: String,   // e.g. "09:32"
    val predictedArrivalEta: String,  // e.g. "09:35"
    val predictedDepartureEta: String,// e.g. "09:37"
    val platformNumber: String,       // e.g. "Pf 2"
    val delayMinutes: Int,
    val status: TrainStationStatus,
    val delayReason: String = "",
    val intermediateVia: String = ""
)

data class CityHubProfile(
    val city: String,
    val state: String,
    val primaryStation: Station,
    val zone: String,
    val division: String,
    val totalPlatforms: Int,
    val ghatSectionAlert: String,
    val ghatDelayVarianceMin: Int,
    val transitServices: List<CityTransitLink>
)

data class CityTransitLink(
    val title: String,
    val destination: String,
    val transitType: String, // "MSRTC Bus", "Prepaid Taxi", "Pilgrim Special", "Airport Shuttle"
    val pickupLocation: String,
    val frequency: String
)
