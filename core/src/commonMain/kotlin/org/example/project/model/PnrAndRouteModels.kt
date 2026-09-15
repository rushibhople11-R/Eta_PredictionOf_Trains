package org.example.project.model

enum class BookingStatus(val label: String, val colorHex: Long) {
    CONFIRMED("Confirmed (CNF)", 0xFF00E676),    // Green
    RAC("Reservation Against Cancellation", 0xFFFFB300), // Amber
    WAITLISTED("Waitlisted (WL)", 0xFFFF5252)    // Red
}

data class PassengerBooking(
    val passengerNumber: Int,
    val name: String,
    val coach: String,
    val berth: String,
    val berthType: String, // "Lower", "Upper", "Side Lower", "Chair"
    val status: BookingStatus
)

data class PnrDetails(
    val pnrNumber: String,
    val trainNumber: String,
    val trainName: String,
    val trainType: TrainType,
    val sourceStation: Station,
    val destinationStation: Station,
    val dateOfJourney: String, // e.g. "Tomorrow, 14 Sept"
    val departureTime: String,
    val arrivalTime: String,
    val passengers: List<PassengerBooking>
)

data class RouteQueryResult(
    val train: TrainJourney,
    val fromStation: Station,
    val toStation: Station,
    val departureTime: String,
    val arrivalTime: String,
    val durationFormatted: String,
    val distanceKm: Int,
    val typicalDelayMinutes: Int
)

object RouteSearchEngine {

    /**
     * Finds all trains that run from [fromStationCode] to [toStationCode]
     * in the valid direction of travel.
     */
    fun findTrainsBetweenStations(
        trains: List<TrainJourney>,
        fromStationCode: String,
        toStationCode: String
    ): List<RouteQueryResult> {
        val results = mutableListOf<RouteQueryResult>()

        for (train in trains) {
            val fromIndex = train.stops.indexOfFirst { it.station.code.equals(fromStationCode, ignoreCase = true) }
            val toIndex = train.stops.indexOfFirst { it.station.code.equals(toStationCode, ignoreCase = true) }

            // Must both exist and 'from' must come before 'to'
            if (fromIndex != -1 && toIndex != -1 && fromIndex < toIndex) {
                val fromStop = train.stops[fromIndex]
                val toStop = train.stops[toIndex]

                val depTime = fromStop.scheduledDeparture
                val arrTime = toStop.scheduledArrival
                val duration = computeDuration(depTime, arrTime)
                val distance = toStop.distanceKm - fromStop.distanceKm

                val typicalDelay = when (train.trainType) {
                    TrainType.VANDE_BHARAT -> 5
                    TrainType.RAJDHANI -> 15
                    TrainType.SUPERFAST -> 25
                    else -> 40
                }

                results.add(
                    RouteQueryResult(
                        train = train,
                        fromStation = fromStop.station,
                        toStation = toStop.station,
                        departureTime = depTime,
                        arrivalTime = arrTime,
                        durationFormatted = duration,
                        distanceKm = distance,
                        typicalDelayMinutes = typicalDelay
                    )
                )
            }
        }

        return results.sortedBy { it.departureTime }
    }

    private fun computeDuration(depTime: String, arrTime: String): String {
        val depParts = depTime.split(":")
        val arrParts = arrTime.split(":")
        if (depParts.size != 2 || arrParts.size != 2) return "--"

        val depMins = (depParts[0].toIntOrNull() ?: 0) * 60 + (depParts[1].toIntOrNull() ?: 0)
        var arrMins = (arrParts[0].toIntOrNull() ?: 0) * 60 + (arrParts[1].toIntOrNull() ?: 0)

        // Cross-midnight adjustment
        if (arrMins < depMins) {
            arrMins += 24 * 60
        }

        val diff = arrMins - depMins
        val hours = diff / 60
        val mins = diff % 60
        return "${hours}h ${mins}m"
    }
}
