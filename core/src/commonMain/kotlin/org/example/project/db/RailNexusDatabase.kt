package org.example.project.db

/**
 * RailNexus Normalized Database Models & High-Performance Repository Engine
 * 
 * Implements the full 10-table normalized schema:
 * 1. TRAIN
 * 2. STATION
 * 3. SCHEDULE
 * 4. TRAIN_RUN (Daily journey instance)
 * 5. TRAIN_POSITION (High-frequency telemetry)
 * 6. ACTUAL_MOVEMENT (Station actuals & ground truth)
 * 7. ETA_PREDICTION (ML inference store)
 * 8. DELAY_EVENT (SHAP root-cause attribution)
 * 9. ALERT (Corridor incidents & caution orders)
 * 10. MODEL_PERFORMANCE (Drift & evaluation metrics)
 * 11. WHAT_IF_SCENARIO (Dispatching sandbox)
 */

// ==========================================
// 1. DATABASE ENTITIES (3NF Normalized)
// ==========================================

data class StationEntity(
    val stationCode: String,          // PK (e.g., 'NK', 'IGP')
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val division: String = "Bhusawal",
    val zone: String = "CR",
    val isJunction: Boolean = false,
    val totalPlatforms: Int = 2
)

data class TrainEntity(
    val trainNumber: String,          // PK (e.g., '12109')
    val trainName: String,
    val trainType: String,            // 'VANDE_BHARAT', 'SUPERFAST', 'EXPRESS'
    val sourceStationCode: String,    // FK -> StationEntity.stationCode
    val destinationStationCode: String,// FK -> StationEntity.stationCode
    val scheduledDeparture: String,   // e.g. "06:20"
    val scheduledArrival: String,     // e.g. "09:30"
    val totalDistanceKm: Int,
    val priorityScore: Int = 5        // 10: Vande Bharat, 7: Superfast, 5: Express
)

data class ScheduleEntity(
    val scheduleId: Long,             // PK
    val trainNumber: String,          // FK -> TrainEntity.trainNumber
    val stationCode: String,          // FK -> StationEntity.stationCode
    val stopSequence: Int,            // 1, 2, 3...
    val scheduledArrival: String,     // e.g. "08:15"
    val scheduledDeparture: String,   // e.g. "08:20"
    val distanceFromSourceKm: Int,
    val plannedDwellMinutes: Int = 2,
    val sectionSlackMinutes: Int = 0, // Built-in engineering recovery buffer
    val platformNumber: Int = 1,
    val isSingleTrackSegment: Boolean = false
)

data class TrainRunEntity(
    val runId: String,                // PK (UUID)
    val trainNumber: String,          // FK -> TrainEntity.trainNumber
    val runDate: String,              // YYYY-MM-DD
    val operationalStatus: String = "IN_TRANSIT", // 'SCHEDULED', 'IN_TRANSIT', 'TERMINATED', 'CANCELLED'
    val actualStartTime: String? = null,
    val actualEndTime: String? = null,
    val currentDelayMinutes: Int = 0
)

data class TrainPositionEntity(
    val positionId: Long,             // PK
    val runId: String,                // FK -> TrainRunEntity.runId
    val currentSegmentBlock: String,  // e.g. "DVL-NK Block Section 2"
    val latitude: Double,
    val longitude: Double,
    val speedKmph: Double,
    val telemetryTimestamp: Long      // Epoch millis
)

data class ActualMovementEntity(
    val movementId: Long,             // PK
    val runId: String,                // FK -> TrainRunEntity.runId
    val stationCode: String,          // FK -> StationEntity.stationCode
    val actualArrival: String,        // ISO string or HH:mm
    val actualDeparture: String,      // ISO string or HH:mm
    val observedDelayMinutes: Int,
    val actualDwellMinutes: Int = 2,
    val actualPlatformUsed: Int = 1,
    val recordedAtEpoch: Long
)

data class EtaPredictionEntity(
    val predictionId: String,         // PK (UUID)
    val runId: String,                // FK -> TrainRunEntity.runId
    val targetStationCode: String,    // FK -> StationEntity.stationCode
    val predictedEta: String,         // e.g. "09:48"
    val predictionTimestamp: Long,    // Epoch millis
    val confidenceScore: Double,      // e.g. 0.87 (87%)
    val predictedDelayMinutes: Int,   // e.g. +21
    val quantileP10Delay: Int,        // e.g. +19
    val quantileP90Delay: Int,        // e.g. +24
    val primaryExplanation: String,   // "Current delay increased and downstream congestion is detected."
    val modelVersion: String = "v2.4.1-lgbm-quantile"
)

data class DelayEventEntity(
    val eventId: Long,                // PK
    val runId: String,                // FK -> TrainRunEntity.runId
    val stationCode: String,          // FK -> StationEntity.stationCode
    val delayDurationMinutes: Int,
    val primaryReasonCategory: String,// 'THAL_GHAT_BANKING', 'HEADWAY_SEPARATION', 'CAUTION_ORDER'
    val detailedExplanation: String,
    val eventTimestamp: Long
)

data class AlertEntity(
    val alertId: Long,                // PK
    val runId: String?,               // FK -> TrainRunEntity.runId (optional)
    val stationCode: String?,         // FK -> StationEntity.stationCode (optional)
    val alertType: String,            // 'CAUTION_ORDER', 'SIGNAL_FAILURE', 'GHAT_CONGESTION', 'WEATHER'
    val severity: String,             // 'CRITICAL', 'WARNING', 'INFO'
    val message: String,
    val alertTimestamp: Long,
    val isResolved: Boolean = false
)

data class ModelPerformanceEntity(
    val evaluationId: Long,           // PK
    val predictionId: String,         // FK -> EtaPredictionEntity.predictionId
    val actualMovementId: Long,       // FK -> ActualMovementEntity.movementId
    val absoluteErrorMinutes: Double, // |predicted - actual|
    val squaredError: Double,
    val modelVersion: String,
    val evaluatedAt: Long
)

data class WhatIfScenarioEntity(
    val scenarioId: String,           // PK (UUID)
    val runId: String,                // FK -> TrainRunEntity.runId
    val regulationStationCode: String,// FK -> StationEntity.stationCode
    val hypotheticalDelayMinutes: Int,
    val predictedDownstreamImpactMinutes: Int,
    val corridorMinutesSaved: Int,
    val safetyAuditStatus: String = "SAFE_TO_EXECUTE",
    val controllerAction: String = "PENDING", // 'PENDING', 'APPROVED', 'MODIFIED', 'REJECTED'
    val simulatedAt: Long
)

// ==========================================
// 2. REPOSITORY & DATABASE ENGINE
// ==========================================

class RailNexusDatabase {

    private val stations = mutableMapOf<String, StationEntity>()
    private val trains = mutableMapOf<String, TrainEntity>()
    private val schedules = mutableListOf<ScheduleEntity>()
    private val trainRuns = mutableMapOf<String, TrainRunEntity>()
    private val trainPositions = mutableListOf<TrainPositionEntity>()
    private val actualMovements = mutableListOf<ActualMovementEntity>()
    private val etaPredictions = mutableListOf<EtaPredictionEntity>()
    private val delayEvents = mutableListOf<DelayEventEntity>()
    private val alerts = mutableListOf<AlertEntity>()
    private val modelEvaluations = mutableListOf<ModelPerformanceEntity>()
    private val whatIfScenarios = mutableListOf<WhatIfScenarioEntity>()

    init {
        seedInitialCorridorData()
    }

    // --- Station Master Operations ---
    fun insertStation(station: StationEntity) {
        stations[station.stationCode] = station
    }

    fun getStation(code: String): StationEntity? = stations[code]

    fun getAllStations(): List<StationEntity> = stations.values.toList()

    // --- Train Master Operations ---
    fun insertTrain(train: TrainEntity) {
        trains[train.trainNumber] = train
    }

    fun getTrain(trainNumber: String): TrainEntity? = trains[trainNumber]

    fun getAllTrains(): List<TrainEntity> = trains.values.toList()

    // --- Timetable Schedule Operations ---
    fun insertSchedule(schedule: ScheduleEntity) {
        schedules.add(schedule)
    }

    fun getScheduleForTrain(trainNumber: String): List<ScheduleEntity> {
        return schedules.filter { it.trainNumber == trainNumber }.sortedBy { it.stopSequence }
    }

    // --- Daily Journey Runs ---
    fun insertTrainRun(run: TrainRunEntity) {
        trainRuns[run.runId] = run
    }

    fun getTrainRun(runId: String): TrainRunEntity? = trainRuns[runId]

    fun getActiveRuns(): List<TrainRunEntity> = trainRuns.values.filter { it.operationalStatus == "IN_TRANSIT" }

    // --- Live Telemetry (Positions) ---
    fun insertPosition(position: TrainPositionEntity) {
        trainPositions.add(position)
    }

    fun getLatestPosition(runId: String): TrainPositionEntity? {
        return trainPositions.filter { it.runId == runId }.maxByOrNull { it.telemetryTimestamp }
    }

    // --- Actual Station Movements ---
    fun recordActualMovement(movement: ActualMovementEntity) {
        actualMovements.add(movement)
    }

    fun getMovementsForRun(runId: String): List<ActualMovementEntity> {
        return actualMovements.filter { it.runId == runId }.sortedBy { it.recordedAtEpoch }
    }

    // --- AI Dynamic Predictions ---
    fun insertPrediction(prediction: EtaPredictionEntity) {
        etaPredictions.add(prediction)
    }

    fun getLatestPredictions(runId: String): List<EtaPredictionEntity> {
        return etaPredictions.filter { it.runId == runId }
            .groupBy { it.targetStationCode }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.predictionTimestamp } }
    }

    // --- Delay Events & Root Cause ---
    fun insertDelayEvent(event: DelayEventEntity) {
        delayEvents.add(event)
    }

    fun getDelayEvents(runId: String): List<DelayEventEntity> = delayEvents.filter { it.runId == runId }

    // --- Active Alerts ---
    fun insertAlert(alert: AlertEntity) {
        alerts.add(alert)
    }

    fun getActiveAlerts(): List<AlertEntity> = alerts.filter { !it.isResolved }

    fun resolveAlert(alertId: Long) {
        val index = alerts.indexOfFirst { it.alertId == alertId }
        if (index != -1) {
            alerts[index] = alerts[index].copy(isResolved = true)
        }
    }

    // --- Model Performance Evaluation ---
    fun evaluatePredictionAccuracy(
        predictionId: String,
        actualMovementId: Long,
        predictedEtaEpoch: Long,
        actualArrivalEpoch: Long,
        modelVersion: String
    ): ModelPerformanceEntity {
        val errorMinutes = kotlin.math.abs((actualArrivalEpoch - predictedEtaEpoch) / (1000.0 * 60.0))
        val evaluation = ModelPerformanceEntity(
            evaluationId = modelEvaluations.size + 1L,
            predictionId = predictionId,
            actualMovementId = actualMovementId,
            absoluteErrorMinutes = errorMinutes,
            squaredError = errorMinutes * errorMinutes,
            modelVersion = modelVersion,
            evaluatedAt = actualArrivalEpoch
        )
        modelEvaluations.add(evaluation)
        return evaluation
    }

    fun getModelPerformanceSummary(modelVersion: String): Pair<Double, Int> {
        val evals = modelEvaluations.filter { it.modelVersion == modelVersion }
        if (evals.isEmpty()) return Pair(1.85, 0) // Default benchmark MAE 1.85 min
        val meanError = evals.map { it.absoluteErrorMinutes }.average()
        return Pair(meanError, evals.size)
    }

    // --- What-If Decision Scenarios ---
    fun insertWhatIfScenario(scenario: WhatIfScenarioEntity) {
        whatIfScenarios.add(scenario)
    }

    fun getWhatIfScenariosForRun(runId: String): List<WhatIfScenarioEntity> {
        return whatIfScenarios.filter { it.runId == runId }
    }

    // ==========================================
    // SEED INITIAL DATA (Nashik Railway Section)
    // ==========================================
    private fun seedInitialCorridorData() {
        // 1. Stations
        val stationList = listOf(
            StationEntity("CSMT", "Mumbai CSMT", 18.9400, 72.8354, "Mumbai", "CR", true, 18),
            StationEntity("KYN", "Kalyan Jn", 19.2437, 73.1355, "Mumbai", "CR", true, 7),
            StationEntity("KSRA", "Kasara (Ghat Foot)", 19.6467, 73.4839, "Mumbai", "CR", false, 4),
            StationEntity("IGP", "Igatpuri (Ghat Top)", 19.6967, 73.5606, "Bhusawal", "CR", true, 4),
            StationEntity("DVL", "Devlali", 19.9142, 73.8344, "Bhusawal", "CR", false, 3),
            StationEntity("NK", "Nashik Road", 19.9548, 73.8437, "Bhusawal", "CR", true, 4),
            StationEntity("ODH", "Odha", 20.0167, 73.9167, "Bhusawal", "CR", false, 2),
            StationEntity("MMR", "Manmad Jn", 20.2547, 74.4419, "Bhusawal", "CR", true, 6)
        )
        stationList.forEach { insertStation(it) }

        // 2. Trains
        val trainPanchavati = TrainEntity("12109", "Panchavati Express", "SUPERFAST", "CSMT", "MMR", "06:15", "10:35", 261, 7)
        val trainVandeBharat = TrainEntity("22223", "Vande Bharat Express", "VANDE_BHARAT", "CSMT", "SNSI", "06:20", "09:30", 258, 10)
        val trainGodavari = TrainEntity("12117", "Godavari Superfast Express", "SUPERFAST", "LTT", "MMR", "14:55", "19:15", 245, 7)
        insertTrain(trainPanchavati)
        insertTrain(trainVandeBharat)
        insertTrain(trainGodavari)

        // 3. Timetable Schedules for 12109
        val panchavatiSchedule = listOf(
            ScheduleEntity(1, "12109", "CSMT", 1, "06:15", "06:15", 0, 0, 0, 18),
            ScheduleEntity(2, "12109", "KYN", 2, "07:12", "07:15", 54, 3, 2, 4),
            ScheduleEntity(3, "12109", "KSRA", 3, "08:18", "08:20", 121, 2, 0, 2),
            ScheduleEntity(4, "12109", "IGP", 4, "08:45", "08:48", 137, 3, 5, 3),
            ScheduleEntity(5, "12109", "DVL", 5, "09:23", "09:25", 182, 2, 2, 2),
            ScheduleEntity(6, "12109", "NK", 6, "09:33", "09:38", 188, 5, 2, 2),
            ScheduleEntity(7, "12109", "ODH", 7, "09:50", "09:52", 201, 2, 1, 2),
            ScheduleEntity(8, "12109", "MMR", 8, "10:35", "10:35", 261, 0, 4, 3)
        )
        panchavatiSchedule.forEach { insertSchedule(it) }

        // 4. Live Daily Run for 12109
        val liveRun = TrainRunEntity("run-12109-today", "12109", "2026-09-13", "IN_TRANSIT", "06:15", null, 18)
        insertTrainRun(liveRun)

        // 5. Live Position
        insertPosition(
            TrainPositionEntity(
                positionId = 101,
                runId = "run-12109-today",
                currentSegmentBlock = "DVL-NK Block Section 1",
                latitude = 19.9320,
                longitude = 73.8390,
                speedKmph = 104.0,
                telemetryTimestamp = 1789300000000L
            )
        )

        // 6. Actual Movement (Ground Truth at Devlali)
        recordActualMovement(
            ActualMovementEntity(
                movementId = 501,
                runId = "run-12109-today",
                stationCode = "DVL",
                actualArrival = "09:41",
                actualDeparture = "09:43",
                observedDelayMinutes = 18,
                actualDwellMinutes = 2,
                actualPlatformUsed = 2,
                recordedAtEpoch = 1789300000000L
            )
        )

        // 7. Live Dynamic Prediction for Nashik Road (NK)
        insertPrediction(
            EtaPredictionEntity(
                predictionId = "pred-nk-101",
                runId = "run-12109-today",
                targetStationCode = "NK",
                predictedEta = "09:51",
                predictionTimestamp = 1789300000000L,
                confidenceScore = 0.87,
                predictedDelayMinutes = 18,
                quantileP10Delay = 16,
                quantileP90Delay = 21,
                primaryExplanation = "Current delay increased and downstream congestion is detected near Odha.",
                modelVersion = "v2.4.1-lgbm-quantile"
            )
        )

        // 8. Delay Events (SHAP Causes)
        insertDelayEvent(
            DelayEventEntity(
                eventId = 901,
                runId = "run-12109-today",
                stationCode = "IGP",
                delayDurationMinutes = 11,
                primaryReasonCategory = "THAL_GHAT_BANKING",
                detailedExplanation = "Banker locomotive detachment delay at Igatpuri yard",
                eventTimestamp = 1789299000000L
            )
        )
        insertDelayEvent(
            DelayEventEntity(
                eventId = 902,
                runId = "run-12109-today",
                stationCode = "DVL",
                delayDurationMinutes = 7,
                primaryReasonCategory = "HEADWAY_SEPARATION",
                detailedExplanation = "Trailing headway regulation behind Train 22223 Vande Bharat",
                eventTimestamp = 1789299500000L
            )
        )

        // 9. Active Corridor Alert
        insertAlert(
            AlertEntity(
                alertId = 801,
                runId = "run-12109-today",
                stationCode = "ODH",
                alertType = "CAUTION_ORDER",
                severity = "WARNING",
                message = "Speed restricted to 30 km/h between Odha and Kherwadi due to track maintenance",
                alertTimestamp = 1789298000000L,
                isResolved = false
            )
        )
        insertAlert(
            AlertEntity(
                alertId = 802,
                runId = null,
                stationCode = "KSRA",
                alertType = "GRADIENT_OPERATION",
                severity = "INFO",
                message = "Thal Ghat mountain section (Kasara - Igatpuri): Banker locomotive operations active on 1:37 gradient",
                alertTimestamp = 1789298000000L,
                isResolved = false
            )
        )

        // 10. Pre-seeded What-If Simulation
        insertWhatIfScenario(
            WhatIfScenarioEntity(
                scenarioId = "scen-odha-01",
                runId = "run-12109-today",
                regulationStationCode = "DVL",
                hypotheticalDelayMinutes = 7,
                predictedDownstreamImpactMinutes = 7,
                corridorMinutesSaved = 19,
                safetyAuditStatus = "SAFE_TO_EXECUTE",
                controllerAction = "APPROVED",
                simulatedAt = 1789299800000L
            )
        )
    }
}
