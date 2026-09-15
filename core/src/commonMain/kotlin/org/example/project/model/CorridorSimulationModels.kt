package org.example.project.model

/**
 * Real-time operational state of a train traversing the Nashik corridor.
 */
data class CorridorTrainState(
    val trainNumber: String,
    val trainName: String,
    val trainType: TrainType,
    val originCode: String,
    val destinationCode: String,
    val currentSpeedKmH: Double,
    val latitude: Double,
    val longitude: Double,
    val currentSection: String,
    val nextStationCode: String,
    val distanceRemainingToNashikKm: Double,
    val scheduledArrivalAtNashik: String,
    val currentDelayMinutes: Int,
    val predictedDelayMinutes: Int,
    val predictedEtaAtNashik: String,
    val assignedPlatform: String,
    val blockId: String,
    val isHeldForPrecedence: Boolean = false,
    val operationalNote: String = "Proceeding on clear signal"
)

/**
 * Signal and track block on the Nashik mainline.
 */
data class CorridorBlock(
    val blockId: String,
    val blockName: String,
    val fromStation: String,
    val toStation: String,
    val isSingleLine: Boolean,
    val lengthKm: Double,
    val mpsKmph: Int,
    val isGhatSection: Boolean = false,
    val activeTrainNumber: String? = null,
    val cautionOrderActive: Boolean = false,
    val speedRestrictionKmph: Int? = null
)

/**
 * What-If Contingency event injected by dispatcher/station master.
 */
data class ContingencyEvent(
    val id: String,
    val title: String,
    val description: String,
    val affectedBlockId: String,
    val affectedTrainNumber: String? = null,
    val delayPenaltyMinutes: Int,
    val isTrackBlocked: Boolean = false,
    val speedLimitKmph: Int? = null,
    val timestampEpochMillis: Long = 0L,
    val isActive: Boolean = true
)

/**
 * Comprehensive real-time snapshot of the entire corridor.
 */
data class NashikCorridorSnapshot(
    val timestampEpochMillis: Long,
    val istTime: String,
    val activeTrains: List<CorridorTrainState>,
    val corridorBlocks: List<CorridorBlock>,
    val activeContingencies: List<ContingencyEvent>,
    val platformOccupancy: Map<String, String?>, // "Pf 1" -> "12109"
    val systemAlert: String? = null
)

/**
 * Historical punctuality and performance analytics.
 */
data class PunctualityReport(
    val division: String = "Bhusawal (BSL)",
    val period: String = "Rolling 30 Days",
    val totalRunsRecorded: Int,
    val onTimeRuns: Int,
    val onTimePercentage: Double,
    val minorDelayPercentage: Double, // 5-15 mins
    val severeDelayPercentage: Double, // > 15 mins
    val averageDelayMinutes: Double,
    val thalGhatAverageDelayMinutes: Double,
    val sectionSlackAbsorptionAvgMinutes: Double,
    val keyBottleneckSection: String
)
