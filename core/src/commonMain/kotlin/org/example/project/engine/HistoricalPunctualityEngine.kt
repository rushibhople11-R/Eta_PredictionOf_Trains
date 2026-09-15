package org.example.project.engine

import org.example.project.model.PunctualityReport

data class HistoricalTrainRunRecord(
    val trainNumber: String,
    val dateEpochMillis: Long,
    val scheduledArrivalMinutes: Int,
    val actualArrivalMinutes: Int,
    val delayMinutes: Int,
    val thalGhatDelayMinutes: Int,
    val slackRecoveredMinutes: Int,
    val runDateFormatted: String = "",
    val status: String = if (delayMinutes < 5) "ON_TIME" else if (delayMinutes <= 15) "MINOR_DELAY" else "SEVERE_DELAY",
    val primaryDelayReason: String = if (delayMinutes < 5) "Normal Traffic Flow" else if (thalGhatDelayMinutes > 5) "Thal Ghat Banker Detachment" else "Sectional Precedence Regulation"
)

/**
 * Historical Punctuality & Slack Budget Analytics Engine.
 */
object HistoricalPunctualityEngine {

    private val sampleRunHistory = mutableListOf(
        HistoricalTrainRunRecord("22223", 1740000000000L, 570, 574, 4, 2, 3, "Today, 14 Sep"),
        HistoricalTrainRunRecord("22223", 1740086400000L, 570, 572, 2, 1, 4, "13 Sep 2026"),
        HistoricalTrainRunRecord("22223", 1740172800000L, 570, 570, 0, 0, 5, "12 Sep 2026"),
        HistoricalTrainRunRecord("12109", 1740000000000L, 1225, 1237, 12, 5, 2, "Today, 14 Sep"),
        HistoricalTrainRunRecord("12109", 1740086400000L, 1225, 1230, 5, 3, 4, "13 Sep 2026"),
        HistoricalTrainRunRecord("12137", 1740000000000L, 1400, 1422, 22, 11, 2, "Today, 14 Sep"),
        HistoricalTrainRunRecord("12137", 1740086400000L, 1400, 1414, 14, 7, 3, "13 Sep 2026"),
        HistoricalTrainRunRecord("12859", 1740000000000L, 680, 698, 18, 9, 1, "Today, 14 Sep"),
        HistoricalTrainRunRecord("12617", 1740000000000L, 480, 488, 8, 4, 3, "Today, 14 Sep"),
        HistoricalTrainRunRecord("12071", 1740000000000L, 890, 893, 3, 2, 4, "Today, 14 Sep"),
        HistoricalTrainRunRecord("12105", 1740000000000L, 1340, 1346, 6, 3, 3, "Today, 14 Sep")
    )

    fun get30DayRunHistory(trainNumber: String): List<HistoricalTrainRunRecord> {
        val cleanNo = trainNumber.trim()
        val baseDelays = when (cleanNo) {
            "22223" -> listOf(0, 2, 0, 4, 1, 0, 3, 0, 2, 0, 5, 1, 0, 0, 2, 0, 1, 0, 3, 0, 0, 2, 1, 0, 4, 0, 0, 2, 1, 0)
            "12951" -> listOf(0, 1, 0, 0, 3, 0, 0, 2, 0, 1, 0, 0, 4, 0, 0, 1, 0, 0, 2, 0, 0, 1, 0, 0, 2, 0, 1, 0, 0, 0)
            "12109" -> listOf(18, 12, 5, 8, 14, 6, 2, 16, 9, 4, 11, 7, 3, 15, 8, 4, 12, 19, 5, 7, 10, 4, 2, 14, 8, 6, 3, 11, 5, 7)
            "12137" -> listOf(24, 18, 14, 28, 12, 9, 22, 16, 11, 25, 19, 14, 8, 26, 15, 10, 21, 17, 12, 30, 14, 8, 19, 13, 9, 23, 16, 11, 20, 15)
            "12139" -> listOf(14, 9, 6, 18, 11, 4, 8, 15, 7, 3, 12, 8, 5, 16, 10, 4, 7, 13, 9, 5, 11, 6, 3, 14, 8, 5, 9, 12, 7, 4)
            else -> listOf(10, 6, 4, 12, 8, 3, 5, 11, 7, 2, 9, 6, 4, 13, 8, 3, 5, 10, 7, 4, 8, 5, 2, 11, 7, 4, 6, 9, 5, 3)
        }

        val reasons = listOf(
            "On-Time Timetable Adherence",
            "Minor Precedence Wait at Kalyan",
            "Thal Ghat Banker Detachment Wait",
            "Devlali TSR (30 km/h Caution Order)",
            "Automatic Block Section Headway",
            "Odha Single Line Precedence Hold"
        )

        val nowEpoch = System.currentTimeMillis()
        return baseDelays.mapIndexed { index, delay ->
            val dayOffset = index
            val runEpoch = nowEpoch - (dayOffset * 86400000L)
            val ist = org.example.project.model.IndianStandardTime.fromEpochMillis(runEpoch)
            val dateLabel = if (index == 0) "Today, ${ist.day} ${ist.monthName}" else if (index == 1) "Yesterday, ${ist.day} ${ist.monthName}" else "${ist.day} ${ist.monthName} ${ist.year}"
            val ghatDelay = if (delay > 10) (delay * 0.4).toInt() else (delay * 0.2).toInt()
            val slack = if (delay > 5) 3 else 5
            val reason = if (delay < 5) reasons[0] else reasons[(index % (reasons.size - 1)) + 1]

            HistoricalTrainRunRecord(
                trainNumber = cleanNo,
                dateEpochMillis = runEpoch,
                scheduledArrivalMinutes = 1200,
                actualArrivalMinutes = 1200 + delay,
                delayMinutes = delay,
                thalGhatDelayMinutes = ghatDelay,
                slackRecoveredMinutes = slack,
                runDateFormatted = dateLabel,
                status = if (delay < 5) "ON_TIME" else if (delay <= 15) "MINOR_DELAY" else "SEVERE_DELAY",
                primaryDelayReason = reason
            )
        }
    }

    fun recordRun(record: HistoricalTrainRunRecord) {
        sampleRunHistory.add(record)
    }

    fun generatePunctualityReport(division: String = "Bhusawal (BSL)"): PunctualityReport {
        val total = sampleRunHistory.size
        if (total == 0) {
            return PunctualityReport(
                division = division,
                totalRunsRecorded = 0,
                onTimeRuns = 0,
                onTimePercentage = 100.0,
                minorDelayPercentage = 0.0,
                severeDelayPercentage = 0.0,
                averageDelayMinutes = 0.0,
                thalGhatAverageDelayMinutes = 0.0,
                sectionSlackAbsorptionAvgMinutes = 0.0,
                keyBottleneckSection = "Kasara - Igatpuri (Thal Ghat)"
            )
        }

        val onTimeCount = sampleRunHistory.count { it.delayMinutes < 5 }
        val minorDelayCount = sampleRunHistory.count { it.delayMinutes in 5..15 }
        val severeDelayCount = sampleRunHistory.count { it.delayMinutes > 15 }

        val avgDelay = sampleRunHistory.map { it.delayMinutes }.average()
        val avgGhatDelay = sampleRunHistory.map { it.thalGhatDelayMinutes }.average()
        val avgSlackAbsorption = sampleRunHistory.map { it.slackRecoveredMinutes }.average()

        return PunctualityReport(
            division = division,
            totalRunsRecorded = total,
            onTimeRuns = onTimeCount,
            onTimePercentage = (onTimeCount.toDouble() / total) * 100.0,
            minorDelayPercentage = (minorDelayCount.toDouble() / total) * 100.0,
            severeDelayPercentage = (severeDelayCount.toDouble() / total) * 100.0,
            averageDelayMinutes = avgDelay,
            thalGhatAverageDelayMinutes = avgGhatDelay,
            sectionSlackAbsorptionAvgMinutes = avgSlackAbsorption,
            keyBottleneckSection = "Kasara - Igatpuri (Thal Ghat 1:37 Severe Incline)"
        )
    }
}
