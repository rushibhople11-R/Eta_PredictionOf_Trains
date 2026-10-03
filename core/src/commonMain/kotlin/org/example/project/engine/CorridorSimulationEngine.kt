package org.example.project.engine

import org.example.project.data.NashikStationData
import org.example.project.model.*

class CorridorSimulationEngine(
    private var predictor: IEtaPredictor = QuantileEtaPredictor()
) {
    // Definitive corridor blocks connecting to Nashik Road
    private val defaultBlocks = listOf(
        CorridorBlock(
            blockId = "BLOCK_KYN_KSRA",
            blockName = "Kalyan - Kasara Plain Mainline",
            fromStation = "KYN",
            toStation = "KSRA",
            isSingleLine = false,
            lengthKm = 67.0,
            mpsKmph = 110
        ),
        CorridorBlock(
            blockId = "BLOCK_KSRA_IGP",
            blockName = "Kasara - Igatpuri (Thal Ghat 1:37 Gradient)",
            fromStation = "KSRA",
            toStation = "IGP",
            isSingleLine = true,
            lengthKm = 14.0,
            mpsKmph = 50,
            isGhatSection = true
        ),
        CorridorBlock(
            blockId = "BLOCK_IGP_DVL",
            blockName = "Igatpuri - Devlali Plateau Section",
            fromStation = "IGP",
            toStation = "DVL",
            isSingleLine = false,
            lengthKm = 45.0,
            mpsKmph = 120
        ),
        CorridorBlock(
            blockId = "BLOCK_DVL_NK",
            blockName = "Devlali - Nashik Road Approach",
            fromStation = "DVL",
            toStation = "NK",
            isSingleLine = false,
            lengthKm = 9.0,
            mpsKmph = 110
        ),
        CorridorBlock(
            blockId = "BLOCK_NK_ODH",
            blockName = "Nashik Road - Odha Section",
            fromStation = "NK",
            toStation = "ODH",
            isSingleLine = false,
            lengthKm = 11.0,
            mpsKmph = 120
        ),
        CorridorBlock(
            blockId = "BLOCK_ODH_LS",
            blockName = "Odha - Lasalgaon Section",
            fromStation = "ODH",
            toStation = "LS",
            isSingleLine = false,
            lengthKm = 32.0,
            mpsKmph = 120
        ),
        CorridorBlock(
            blockId = "BLOCK_LS_MMR",
            blockName = "Lasalgaon - Manmad Jn Corridor",
            fromStation = "LS",
            toStation = "MMR",
            isSingleLine = false,
            lengthKm = 25.0,
            mpsKmph = 110
        )
    )

    private val activeContingencies = mutableListOf<ContingencyEvent>()

    // Initial baseline states for trains on the corridor
    private val baselineTrainPositions = mutableListOf(
        CorridorTrainState(
            trainNumber = "22223",
            trainName = "CSMT - Sainagar Shirdi Vande Bharat",
            trainType = TrainType.VANDE_BHARAT,
            originCode = "CSMT",
            destinationCode = "SNSI",
            currentSpeedKmH = 92.0,
            latitude = 19.8250,
            longitude = 73.7120,
            currentSection = "Igatpuri - Devlali Plateau",
            nextStationCode = "DVL",
            distanceRemainingToNashikKm = 28.0,
            scheduledArrivalAtNashik = "9:30 AM",
            currentDelayMinutes = 4,
            predictedDelayMinutes = 4,
            predictedEtaAtNashik = "9:34 AM",
            assignedPlatform = "Pf 2",
            blockId = "BLOCK_IGP_DVL",
            operationalNote = "Thal Ghat banking detached at IGP; running smoothly"
        ),
        CorridorTrainState(
            trainNumber = "12109",
            trainName = "Mumbai - Manmad Panchavati Express",
            trainType = TrainType.SUPERFAST,
            originCode = "CSMT",
            destinationCode = "MMR",
            currentSpeedKmH = 45.0,
            latitude = 19.6830,
            longitude = 73.5180,
            currentSection = "Kasara - Igatpuri Thal Ghat",
            nextStationCode = "IGP",
            distanceRemainingToNashikKm = 62.0,
            scheduledArrivalAtNashik = "10:15 AM",
            currentDelayMinutes = 8,
            predictedDelayMinutes = 11,
            predictedEtaAtNashik = "10:26 AM",
            assignedPlatform = "Pf 1",
            blockId = "BLOCK_KSRA_IGP",
            operationalNote = "Banking engines (WAG-9) attached; ascending 1:37 grade"
        ),
        CorridorTrainState(
            trainNumber = "12137",
            trainName = "Punjab Mail Express",
            trainType = TrainType.SUPERFAST,
            originCode = "CSMT",
            destinationCode = "FZR",
            currentSpeedKmH = 98.0,
            latitude = 19.5200,
            longitude = 73.3400,
            currentSection = "Kalyan - Kasara Plain",
            nextStationCode = "KSRA",
            distanceRemainingToNashikKm = 88.0,
            scheduledArrivalAtNashik = "11:45 AM",
            currentDelayMinutes = 14,
            predictedDelayMinutes = 18,
            predictedEtaAtNashik = "12:03 PM",
            assignedPlatform = "Pf 3",
            blockId = "BLOCK_KYN_KSRA",
            operationalNote = "Approaching Kasara for mandatory brake test"
        ),
        CorridorTrainState(
            trainNumber = "12859",
            trainName = "Gitanjali Express",
            trainType = TrainType.SUPERFAST,
            originCode = "CSMT",
            destinationCode = "HWH",
            currentSpeedKmH = 88.0,
            latitude = 19.4500,
            longitude = 73.2800,
            currentSection = "Kalyan - Kasara Plain",
            nextStationCode = "KSRA",
            distanceRemainingToNashikKm = 102.0,
            scheduledArrivalAtNashik = "8:45 AM",
            currentDelayMinutes = 22,
            predictedDelayMinutes = 25,
            predictedEtaAtNashik = "9:10 AM",
            assignedPlatform = "Pf 1",
            blockId = "BLOCK_KYN_KSRA",
            operationalNote = "Following behind Punjab Mail with safe block headway"
        ),
        CorridorTrainState(
            trainNumber = "12617",
            trainName = "Mangala Lakshadweep Express",
            trainType = TrainType.SUPERFAST,
            originCode = "ERS",
            destinationCode = "NZM",
            currentSpeedKmH = 104.0,
            latitude = 20.0600,
            longitude = 74.0200,
            currentSection = "Odha - Lasalgaon",
            nextStationCode = "LS",
            distanceRemainingToNashikKm = 0.0, // Already crossed Nashik
            scheduledArrivalAtNashik = "7:20 AM",
            currentDelayMinutes = 5,
            predictedDelayMinutes = 5,
            predictedEtaAtNashik = "7:25 AM",
            assignedPlatform = "Pf 2",
            blockId = "BLOCK_ODH_LS",
            operationalNote = "Departed Nashik Road on time; heading towards Manmad"
        ),
        CorridorTrainState(
            trainNumber = "12071",
            trainName = "Mumbai - Jalna Jan Shatabdi",
            trainType = TrainType.SUPERFAST,
            originCode = "CSMT",
            destinationCode = "J",
            currentSpeedKmH = 95.0,
            latitude = 19.9100,
            longitude = 73.8200,
            currentSection = "Devlali - Nashik Road",
            nextStationCode = "NK",
            distanceRemainingToNashikKm = 6.0,
            scheduledArrivalAtNashik = "3:45 PM",
            currentDelayMinutes = 2,
            predictedDelayMinutes = 3,
            predictedEtaAtNashik = "3:48 PM",
            assignedPlatform = "Pf 2",
            blockId = "BLOCK_DVL_NK",
            operationalNote = "Slowing down for Nashik Road outer approach signal"
        ),
        CorridorTrainState(
            trainNumber = "12105",
            trainName = "Vidarbha Superfast Express",
            trainType = TrainType.SUPERFAST,
            originCode = "CSMT",
            destinationCode = "G",
            currentSpeedKmH = 85.0,
            latitude = 19.7800,
            longitude = 73.6500,
            currentSection = "Igatpuri - Devlali Plateau",
            nextStationCode = "DVL",
            distanceRemainingToNashikKm = 34.0,
            scheduledArrivalAtNashik = "10:10 PM",
            currentDelayMinutes = 6,
            predictedDelayMinutes = 7,
            predictedEtaAtNashik = "10:17 PM",
            assignedPlatform = "Pf 3",
            blockId = "BLOCK_IGP_DVL",
            operationalNote = "Cruising on double line section towards Nashik"
        )
    )

    fun switchPredictor(newPredictor: IEtaPredictor) {
        this.predictor = newPredictor
    }

    fun getPredictor(): IEtaPredictor = predictor

    fun injectContingency(event: ContingencyEvent) {
        activeContingencies.removeAll { it.id == event.id }
        activeContingencies.add(event)
    }

    fun removeContingency(eventId: String) {
        activeContingencies.removeAll { it.id == eventId }
    }

    fun clearAllContingencies() {
        activeContingencies.clear()
    }

    fun getActiveContingencies(): List<ContingencyEvent> = activeContingencies.toList()

    /**
     * Advances simulation and returns current snapshot.
     */
    fun computeCorridorSnapshot(nowEpochMillis: Long = System.currentTimeMillis()): NashikCorridorSnapshot {
        val ist = IndianStandardTime.fromEpochMillis(nowEpochMillis)

        // 1. Update blocks based on contingencies
        val updatedBlocks = defaultBlocks.map { block ->
            val contingency = activeContingencies.find { it.affectedBlockId == block.blockId && it.isActive }
            if (contingency != null) {
                block.copy(
                    cautionOrderActive = true,
                    speedRestrictionKmph = contingency.speedLimitKmph ?: (block.mpsKmph / 2)
                )
            } else {
                block
            }
        }

        // 2. Compute dynamic impact of contingencies on trains
        val updatedTrains = baselineTrainPositions.map { train ->
            val block = updatedBlocks.find { it.blockId == train.blockId }
            val directContingency = activeContingencies.find {
                it.isActive && (it.affectedTrainNumber == train.trainNumber || it.affectedBlockId == train.blockId)
            }

            var extraDelay = 0
            var note = train.operationalNote
            var isHeld = false

            if (directContingency != null) {
                extraDelay += directContingency.delayPenaltyMinutes
                note = "${directContingency.title}: +${directContingency.delayPenaltyMinutes}m"
                if (directContingency.isTrackBlocked) {
                    isHeld = true
                    note = "HELD: ${directContingency.description}"
                }
            }

            // Vande Bharat priority over normal trains if sharing block
            if (train.trainNumber != "22223" && activeContingencies.any { it.id == "VANDE_BHARAT_PREFERENCE" }) {
                extraDelay += 8
                isHeld = true
                note = "Loop line hold: Precedence granted to 22223 Vande Bharat"
            }

            val dynamicPredictedDelay = train.predictedDelayMinutes + extraDelay
            val cleanSched = train.scheduledArrivalAtNashik.trim().uppercase()
            val isPm = cleanSched.endsWith("PM")
            val isAm = cleanSched.endsWith("AM")
            val coreTime = cleanSched.replace("AM", "").replace("PM", "").trim()
            val scheduledParts = coreTime.split(":")
            var schedH = scheduledParts.getOrNull(0)?.toIntOrNull() ?: 9
            val schedM = scheduledParts.getOrNull(1)?.toIntOrNull() ?: 30
            if (isPm && schedH < 12) schedH += 12
            if (isAm && schedH == 12) schedH = 0
            val totalMins = (schedH * 60 + schedM + dynamicPredictedDelay) % (24 * 60)
            val etaH24 = totalMins / 60
            val etaM = totalMins % 60
            val amPm = if (etaH24 >= 12) "PM" else "AM"
            val etaH12 = when (val h = etaH24 % 12) {
                0 -> 12
                else -> h
            }
            val newEta = "$etaH12:${etaM.toString().padStart(2, '0')} $amPm"

            val effectiveSpeed = if (isHeld) 0.0 else (block?.speedRestrictionKmph?.toDouble() ?: train.currentSpeedKmH)

            train.copy(
                currentSpeedKmH = effectiveSpeed,
                predictedDelayMinutes = dynamicPredictedDelay,
                predictedEtaAtNashik = newEta,
                isHeldForPrecedence = isHeld,
                operationalNote = note
            )
        }

        // 3. Platform occupancy at Nashik Road (NK)
        val platformOccupancy = mutableMapOf<String, String?>(
            "Pf 1" to null,
            "Pf 2" to null,
            "Pf 3" to null,
            "Pf 4" to null
        )

        updatedTrains.forEach { t ->
            if (t.distanceRemainingToNashikKm in 0.0..10.0) {
                platformOccupancy[t.assignedPlatform] = "${t.trainNumber} (${t.trainName.take(16)}...)"
            }
        }

        val systemAlert = if (activeContingencies.isNotEmpty()) {
            "⚠ ${activeContingencies.size} ACTIVE CONTINGENCY DISPATCHES ENFORCED ON NASHIK CORRIDOR"
        } else {
            null
        }

        return NashikCorridorSnapshot(
            timestampEpochMillis = nowEpochMillis,
            istTime = ist.fullFormatted,
            activeTrains = updatedTrains,
            corridorBlocks = updatedBlocks,
            activeContingencies = activeContingencies.toList(),
            platformOccupancy = platformOccupancy,
            systemAlert = systemAlert
        )
    }
}
