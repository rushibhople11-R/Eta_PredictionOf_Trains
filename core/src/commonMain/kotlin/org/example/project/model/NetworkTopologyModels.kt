package org.example.project.model

/**
 * Congestion rating for physical track segments.
 */
enum class SegmentCongestionLevel(val label: String, val colorHex: Long) {
    FREE_FLOW("Free Flow", 0xFF10B981),
    MODERATE_HEADWAY("Moderate Spacing", 0xFF06B6D4),
    APPROACHING_CAPACITY("Approaching Capacity", 0xFFF59E0B),
    BLOCKED_WAIT("Blocked / Crossing Wait", 0xFFEF4444)
}

/**
 * Physical station node in the railway topology graph.
 */
data class StationNode(
    val stationCode: String,
    val stationName: String,
    val sequence: Int,
    val isJunction: Boolean = false,
    val platformCount: Int = 4,
    val currentOccupiedPlatforms: Int = 1,
    val scheduledDwellMinutes: Int = 2,
    val historicalMeanDwellMinutes: Double = 2.4,
    val dwellInflationRate: Double = 0.12 // Extends dwell when train arrives delayed due to crowd rush
)

/**
 * Physical track segment (edge) connecting two consecutive stations.
 */
data class RouteSegment(
    val segmentId: String,
    val routeName: String,
    val fromStationCode: String,
    val toStationCode: String,
    val distanceKm: Double,
    val speedLimitKmh: Double = 130.0,
    val isSingleTrack: Boolean = false,
    val isGhatSection: Boolean = false,
    val gradientFactor: Double = 1.0, // 1.37 = Kasara to Igatpuri Thal Ghat
    val sectionSlackMinutes: Int = 0,
    val historicalMedianRuntimeMinutes: Double = 25.0,
    val activeTrainsInBlock: List<String> = emptyList(),
    val congestionLevel: SegmentCongestionLevel = SegmentCongestionLevel.FREE_FLOW
)

/**
 * Detailed forecast for a single station node in the chained network.
 */
data class ChainedStationForecast(
    val stationNode: StationNode,
    val scheduledArrivalEpoch: Long,
    val scheduledDepartureEpoch: Long,
    val predictedArrivalEpoch: Long,
    val predictedDepartureEpoch: Long,
    val predictedArrivalHHmm: String,
    val predictedDepartureHHmm: String,
    val arrivalDelayMinutes: Int,
    val departureDelayMinutes: Int,
    val dwellDurationMinutes: Int,
    val dwellInflationMinutes: Int,
    val dwellExplanation: String
)

/**
 * Detailed transit calculation across an intermediate track segment.
 */
data class ChainedSegmentTransit(
    val segment: RouteSegment,
    val kinematicRuntimeMinutes: Double,
    val congestionPenaltyMinutes: Int,
    val slackAbsorptionMinutes: Int,
    val effectiveRuntimeMinutes: Double,
    val precedenceConflictWithTrain: String? = null,
    val transitExplanation: String
)

/**
 * Comprehensive network-aware forecast for an entire multi-station route.
 */
data class NetworkAwareForecastReport(
    val trainNumber: String,
    val routeId: String,
    val currentStationIndex: Int,
    val currentStationCode: String,
    val initialObservedDelayMinutes: Int,
    val stationForecasts: List<ChainedStationForecast>,
    val segmentTransits: List<ChainedSegmentTransit>,
    val totalJourneyDistanceKm: Double,
    val totalRuntimeMinutes: Double,
    val accumulatedDelayMinutes: Int,
    val multiTrainConflicts: List<PrecedenceResolution>,
    val networkAdvantageExplanation: String,
    val generatedAtEpochMillis: Long = System.currentTimeMillis()
)
