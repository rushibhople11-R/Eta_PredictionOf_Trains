package org.example.project.engine

enum class AlertCategory(val label: String) {
    ACTIVE("Active Alerts"),
    WARNING("Warning Alerts"),
    RESOLVED("Resolved Alerts")
}

enum class ControllerDecisionType {
    APPROVE,
    MODIFY,
    REJECT,
    PENDING
}

data class ControlRoomAlert(
    val id: String,
    val title: String,
    val category: AlertCategory,
    val trainNumber: String,
    val trainName: String,
    val section: String,
    val description: String,
    val delayMinutes: Int,
    val severity: String, // "CRITICAL", "WARNING", "INFO"
    val timestampFormatted: String,
    val hasWhatIfWorkflow: Boolean = false
)

data class WhatIfScenario(
    val id: String,
    val title: String,
    val affectedTrainNumber: String,
    val affectedTrainName: String,
    val conflictTrainNumber: String,
    val conflictTrainName: String,
    val conflictSection: String,
    val initialDelayMinutes: Int,
    val scenarioDescription: String
)

data class AiRecommendation(
    val scenarioId: String,
    val recommendedAction: String,
    val targetTrainNumber: String,
    val regulationStation: String,
    val holdMinutes: Int,
    val expectedSavingsMinutes: Int,
    val confidencePercent: Int,
    val rationale: String,
    val speedAdvisoryKmH: Int
)

data class SafetyRuleCheck(
    val ruleName: String,
    val ruleDescription: String,
    val isPassed: Boolean,
    val safetyMargin: String
)

data class SafetyEngineAudit(
    val auditId: String,
    val isSafe: Boolean,
    val safetyScorePercent: Int,
    val ruleChecks: List<SafetyRuleCheck>,
    val certificationNote: String
)

data class ControllerDecision(
    val scenarioId: String,
    val decisionType: ControllerDecisionType,
    val controllerName: String = "Nashik Section Controller (Shift A)",
    val notes: String,
    val modifiedHoldMinutes: Int? = null,
    val timestampEpochMillis: Long = 0L
)

data class SectionThroughput(
    val sectionId: String,
    val sectionName: String,
    val fromStation: String,
    val toStation: String,
    val capacityTrainsPerHour: Int,
    val currentTrainsPerHour: Int,
    val utilizationPercent: Int,
    val averageSpeedKmH: Int,
    val bottleneckStatus: String, // "NORMAL", "CONGESTED", "BOTTLENECK"
    val optimizationRecommendation: String
)

data class ReportSummary(
    val trainNumber: String,
    val trainName: String,
    val scheduledDeparture: String,
    val actualDeparture: String,
    val scheduledArrivalNashik: String,
    val predictedArrivalNashik: String,
    val delayMinutes: Int,
    val punctualityGrade: String, // "A+", "A", "B", "C"
    val thalGhatPerformance: String
)

/**
 * RailNexus AI Control Room Decision Support System (DSS) & Safety Engine
 * Implements the 6-pillar control flow:
 * 1. Active & Delayed Trains -> Details -> Dynamic ETA
 * 2. Alerts -> What-If Scenario -> AI Recommendation -> Safety Engine -> Controller Decision
 * 3. Network -> Live Map (Train, Station, Section details)
 * 4. Dynamic ETA -> Upcoming stations -> Explanation
 * 5. Throughput -> Section performance -> Optimization Recommendation -> Safety -> Controller Decision
 * 6. Analytics & Reports -> Train list & reports
 */
object AiDecisionAndSafetyEngine {

    fun getDefaultAlerts(): List<ControlRoomAlert> = listOf(
        ControlRoomAlert(
            id = "ALERT_ODHA_01",
            title = "Odha Section Congestion & Restrictive Signal",
            category = AlertCategory.ACTIVE,
            trainNumber = "12109",
            trainName = "Panchavati Express",
            section = "Devlali - Nashik Road - Odha",
            description = "Train 12109 facing restrictive signal at Devlali outer. Preceding freight train clearing Odha block.",
            delayMinutes = 18,
            severity = "CRITICAL",
            timestampFormatted = "10:14 IST",
            hasWhatIfWorkflow = true
        ),
        ControlRoomAlert(
            id = "ALERT_GITANJALI_02",
            title = "Loop Line Hold & Precedence Regulation",
            category = AlertCategory.ACTIVE,
            trainNumber = "12859",
            trainName = "Gitanjali Express",
            section = "Nashik Road (NK) - Odha - Kherwadi",
            description = "Superfast 12859 regulated at Platform 2 loop line for through clearance of down priority mail. Estimated hold: 12 min.",
            delayMinutes = 22,
            severity = "CRITICAL",
            timestampFormatted = "10:18 IST",
            hasWhatIfWorkflow = true
        ),
        ControlRoomAlert(
            id = "ALERT_VANDE_03",
            title = "OHE Traction Voltage Drop at Ghoti Neutral Section",
            category = AlertCategory.ACTIVE,
            trainNumber = "22223",
            trainName = "CSMT - Shirdi Vande Bharat",
            section = "Igatpuri (IGP) - Ghoti - Padli",
            description = "Automated pantograph cycle engaged after 25kV catenary voltage dip. Train running under automated speed restriction of 75 km/h.",
            delayMinutes = 8,
            severity = "WARNING",
            timestampFormatted = "10:22 IST",
            hasWhatIfWorkflow = true
        ),
        ControlRoomAlert(
            id = "ALERT_GHAT_04",
            title = "Thal Ghat Banking Engine Detachment Queue",
            category = AlertCategory.ACTIVE,
            trainNumber = "12137",
            trainName = "Punjab Mail",
            section = "Kasara - Igatpuri (Thal Ghat)",
            description = "Banking locomotive detachment backlog at Igatpuri siding.",
            delayMinutes = 14,
            severity = "WARNING",
            timestampFormatted = "10:02 IST",
            hasWhatIfWorkflow = true
        ),
        ControlRoomAlert(
            id = "ALERT_FREIGHT_05",
            title = "Heavy Freight Siding Regulation for Passenger Run",
            category = AlertCategory.ACTIVE,
            trainNumber = "BOXN-6022",
            trainName = "Agro Bulk Freight (Manmad -> JNPT)",
            section = "Odha (ODH) - Lahavit Loop Siding",
            description = "58 loaded wagons regulated to Lahavit loop siding to grant clear corridor path to 12109 Panchavati Express.",
            delayMinutes = 25,
            severity = "WARNING",
            timestampFormatted = "09:58 IST",
            hasWhatIfWorkflow = false
        ),
        ControlRoomAlert(
            id = "ALERT_JHELUM_06",
            title = "Yard Shunting & Caution Aspect on Manmad Approach",
            category = AlertCategory.WARNING,
            trainNumber = "11077",
            trainName = "Jhelum Express",
            section = "Lasalgaon (LS) - Manmad Junction (MMR)",
            description = "Encountering single-aspect yellow signal due to onion parcel rake shunting onto freight line 4 at Manmad Outer.",
            delayMinutes = 11,
            severity = "WARNING",
            timestampFormatted = "10:08 IST",
            hasWhatIfWorkflow = false
        ),
        ControlRoomAlert(
            id = "ALERT_SPEED_07",
            title = "Track Caution Order 30 km/h Active",
            category = AlertCategory.WARNING,
            trainNumber = "All Trains",
            trainName = "Corridor Maintenance",
            section = "Devlali - Nashik Road Up Line",
            description = "Tamping machine maintenance active on Up line. Caution order enforced.",
            delayMinutes = 10,
            severity = "WARNING",
            timestampFormatted = "09:45 IST"
        ),
        ControlRoomAlert(
            id = "ALERT_MANGALA_08",
            title = "Banker Loco Safe Decoupling Completed",
            category = AlertCategory.RESOLVED,
            trainNumber = "12618",
            trainName = "Mangala Lakshadweep Express",
            section = "Igatpuri Siding 3",
            description = "Triple WAG-9 banking locomotives safely detached after ascending 1:37 gradient. Air brake continuity test passed.",
            delayMinutes = 0,
            severity = "INFO",
            timestampFormatted = "09:35 IST",
            hasWhatIfWorkflow = false
        ),
        ControlRoomAlert(
            id = "ALERT_CLEARED_09",
            title = "Kasara Yard Shunting Cleared",
            category = AlertCategory.RESOLVED,
            trainNumber = "22223",
            trainName = "Vande Bharat Express",
            section = "Kasara Yard Block",
            description = "Platform 2 loop line shunting completed. Mainline clear for through passage.",
            delayMinutes = 0,
            severity = "INFO",
            timestampFormatted = "09:20 IST"
        )
    )

    fun getOdhaCongestionScenario(): WhatIfScenario = WhatIfScenario(
        id = "SCENARIO_ODHA_CONGESTION",
        title = "Odha Section Congestion & Dynamic ETA Recalculation",
        affectedTrainNumber = "12109",
        affectedTrainName = "Panchavati Express",
        conflictTrainNumber = "22223",
        conflictTrainName = "CSMT - Shirdi Vande Bharat",
        conflictSection = "Devlali (DVL) - Nashik Road (NK) - Odha (ODH)",
        initialDelayMinutes = 18,
        scenarioDescription = "Train 12109 Panchavati Express is trailing 22223 Vande Bharat on the Up mainline. Restrictive double-yellow aspect at Devlali outer caused by single-line bridge inspection at Odha."
    )

    fun generateAiRecommendation(scenario: WhatIfScenario): AiRecommendation {
        return AiRecommendation(
            scenarioId = scenario.id,
            recommendedAction = "Precedence Regulation & Loop Hold",
            targetTrainNumber = scenario.affectedTrainNumber,
            regulationStation = "Devlali (DVL) Loop Line 2",
            holdMinutes = 7,
            expectedSavingsMinutes = 19,
            confidencePercent = 94,
            rationale = "Regulating 12109 at Devlali for 7 min allows 22223 Vande Bharat to clear the Nashik Road - Odha block without deceleration. 12109 then gains full green clear line, recovering 12 minutes of scheduled section slack before Manmad.",
            speedAdvisoryKmH = 95
        )
    }

    fun auditSafety(recommendation: AiRecommendation): SafetyEngineAudit {
        val rules = listOf(
            SafetyRuleCheck(
                ruleName = "Solid-State Interlocking (SSI)",
                ruleDescription = "Points and signals route locking at Devlali Loop 2",
                isPassed = true,
                safetyMargin = "Route locked & confirmed"
            ),
            SafetyRuleCheck(
                ruleName = "Headway Separation Buffer",
                ruleDescription = "Minimum safe spacing behind preceding Vande Bharat",
                isPassed = true,
                safetyMargin = "5.8 min separation (> 4.0 min requirement)"
            ),
            SafetyRuleCheck(
                ruleName = "Maximum Permissible Speed (MPS)",
                ruleDescription = "Speed compliance over Devlali turnout (1 in 12 switch)",
                isPassed = true,
                safetyMargin = "Speed advisory 30 km/h over turnout, 95 km/h mainline"
            ),
            SafetyRuleCheck(
                ruleName = "Platform & Loop Clearance Length",
                ruleDescription = "Devlali Loop 2 length vs 12109 22-coach train length",
                isPassed = true,
                safetyMargin = "Loop 680m > Train 540m (140m clearance buffer)"
            )
        )

        return SafetyEngineAudit(
            auditId = "SAFETY_AUDIT_${System.currentTimeMillis()}",
            isSafe = rules.all { it.isPassed },
            safetyScorePercent = 98,
            ruleChecks = rules,
            certificationNote = "Central Railway Safety Rules Book (Chapter IV, Rule 4.12) compliance verified. Certified safe for controller execution."
        )
    }

    fun getCorridorThroughput(): List<SectionThroughput> = listOf(
        SectionThroughput(
            sectionId = "SEC_KYN_KSRA",
            sectionName = "Kalyan - Kasara",
            fromStation = "KYN",
            toStation = "KSRA",
            capacityTrainsPerHour = 18,
            currentTrainsPerHour = 14,
            utilizationPercent = 78,
            averageSpeedKmH = 92,
            bottleneckStatus = "NORMAL",
            optimizationRecommendation = "Maintain standard block spacing of 3.5 mins"
        ),
        SectionThroughput(
            sectionId = "SEC_KSRA_IGP",
            sectionName = "Kasara - Igatpuri (Thal Ghat)",
            fromStation = "KSRA",
            toStation = "IGP",
            capacityTrainsPerHour = 8,
            currentTrainsPerHour = 7,
            utilizationPercent = 88,
            averageSpeedKmH = 42,
            bottleneckStatus = "CONGESTED",
            optimizationRecommendation = "Expedite banker locomotive turnarounds at Igatpuri siding"
        ),
        SectionThroughput(
            sectionId = "SEC_IGP_DVL",
            sectionName = "Igatpuri - Devlali",
            fromStation = "IGP",
            toStation = "DVL",
            capacityTrainsPerHour = 16,
            currentTrainsPerHour = 11,
            utilizationPercent = 68,
            averageSpeedKmH = 104,
            bottleneckStatus = "NORMAL",
            optimizationRecommendation = "Clear run; permit MPS acceleration up to 120 km/h"
        ),
        SectionThroughput(
            sectionId = "SEC_DVL_NK_ODH",
            sectionName = "Devlali - Nashik Road - Odha",
            fromStation = "DVL",
            toStation = "ODH",
            capacityTrainsPerHour = 14,
            currentTrainsPerHour = 13,
            utilizationPercent = 93,
            averageSpeedKmH = 58,
            bottleneckStatus = "BOTTLENECK",
            optimizationRecommendation = "Enforce AI Recommendation loop hold on 12109 to relieve bridge bottleneck"
        ),
        SectionThroughput(
            sectionId = "SEC_ODH_MMR",
            sectionName = "Odha - Lasalgaon - Manmad",
            fromStation = "ODH",
            toStation = "MMR",
            capacityTrainsPerHour = 16,
            currentTrainsPerHour = 10,
            utilizationPercent = 62,
            averageSpeedKmH = 108,
            bottleneckStatus = "NORMAL",
            optimizationRecommendation = "High section slack available; recovered delays can be absorbed"
        )
    )

    fun getPerformanceReports(): List<ReportSummary> = listOf(
        ReportSummary("22223", "Vande Bharat Express", "06:20", "06:20", "09:30", "09:34", 4, "A+", "Braking tests smooth; 0m loss"),
        ReportSummary("12109", "Panchavati Express", "06:15", "06:18", "10:15", "10:22", 7, "A", "Loop regulation saved 12m delay"),
        ReportSummary("12137", "Punjab Mail Express", "07:35", "07:42", "11:45", "11:58", 13, "B", "Thal Ghat banking queue: +11m"),
        ReportSummary("12859", "Gitanjali Express", "06:00", "06:14", "08:45", "09:05", 20, "C", "Delayed by suburban rake at Kalyan"),
        ReportSummary("12617", "Mangala Lakshadweep", "01:15", "01:20", "07:20", "07:25", 5, "A", "Maintained scheduled sectional speeds"),
        ReportSummary("12071", "Jan Shatabdi Express", "12:10", "12:11", "15:45", "15:48", 3, "A+", "Optimal block headway throughout"),
        ReportSummary("12105", "Vidarbha Superfast", "19:05", "19:10", "22:10", "22:17", 7, "A", "Slack absorbed between IGP & NK")
    )
}
