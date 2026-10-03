package org.example.project.model

data class IstTime(
    val year: Int,
    val month: Int,
    val monthName: String,
    val day: Int,
    val hour: Int,        // 0..23
    val minute: Int,      // 0..59
    val second: Int,      // 0..59
    val epochMillis: Long
) {
    val isPm: Boolean get() = hour >= 12
    val hour12: Int get() = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPmStr: String get() = if (isPm) "PM" else "AM"

    val time24: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}"
    val time24Short: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    val time12: String get() = "$hour12:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')} $amPmStr"
    val time12Short: String get() = "$hour12:${minute.toString().padStart(2, '0')} $amPmStr"
    val time12Standard: String get() = "$hour12:${minute.toString().padStart(2, '0')} $amPmStr"
    val dateStr: String get() = "$day $monthName $year"
    val fullFormatted: String get() = "$dateStr • $time12 IST"
    val compactBadge: String get() = "$time12Standard IST"

    /**
     * Computes remaining seconds until target scheduled arrival in IST (supports both 24h "18:30" and 12h "6:30 PM").
     */
    fun secondsUntil(targetTimeStr: String): Long {
        val clean = targetTimeStr.trim()
        val isPm = clean.endsWith("PM", ignoreCase = true)
        val isAm = clean.endsWith("AM", ignoreCase = true)
        val timeOnly = clean.replace("AM", "", ignoreCase = true).replace("PM", "", ignoreCase = true).trim()
        val parts = timeOnly.split(":")
        if (parts.size < 2) return 0L
        var targetH = parts[0].toIntOrNull() ?: return 0L
        val targetM = parts[1].filter { it.isDigit() }.take(2).toIntOrNull() ?: return 0L

        if (isPm && targetH < 12) targetH += 12
        if (isAm && targetH == 12) targetH = 0

        val currentTotalSeconds = hour * 3600 + minute * 60 + second
        var targetTotalSeconds = targetH * 3600 + targetM * 60

        // If target is earlier than current time by > 12h, assume cross-midnight (next day)
        if (targetTotalSeconds < currentTotalSeconds - 12 * 3600) {
            targetTotalSeconds += 24 * 3600
        }

        return (targetTotalSeconds - currentTotalSeconds).toLong()
    }

    /**
     * Plain-English live countdown relative to IST:
     * e.g. "14m 22s", "1h 12m", "Departed 4m ago"
     */
    fun formatCountdownTo(targetTimeStr: String): String {
        val diffSec = secondsUntil(targetTimeStr)
        if (diffSec < 0) {
            val pastMin = (-diffSec / 60)
            return if (pastMin == 0L) "Departed just now" else "Departed ${pastMin}m ago"
        }
        val h = diffSec / 3600
        val m = (diffSec % 3600) / 60
        val s = diffSec % 60

        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}

/**
 * Formats any 24-hour or 12-hour time string into clean standard 12-hour AM/PM format.
 * E.g., "09:30" -> "9:30 AM", "09:30 AM" -> "9:30 AM", "18:42" -> "6:42 PM", "00:15" -> "12:15 AM".
 */
fun String.toStandard12Hour(): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    val isPm = trimmed.endsWith("PM", ignoreCase = true)
    val isAm = trimmed.endsWith("AM", ignoreCase = true)
    val clean = trimmed.replace("(?i)AM".toRegex(), "").replace("(?i)PM".toRegex(), "").trim()
    val parts = clean.split(":")
    if (parts.size < 2) return trimmed
    var h = parts[0].toIntOrNull() ?: return trimmed
    val mDigits = parts[1].filter { it.isDigit() }.take(2)
    val m = mDigits.toIntOrNull() ?: return trimmed

    val finalAmPm = when {
        isPm -> "PM"
        isAm -> "AM"
        h >= 12 -> "PM"
        else -> "AM"
    }
    val h12 = when {
        isPm || isAm -> {
            if (h == 0) 12 else if (h > 12) h - 12 else h
        }
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $finalAmPm"
}

/**
 * Parses any time string ("06:20", "18:15", "6:20 AM", "10:50 PM") into minutes from midnight (0..1439).
 */
fun parseTimeToMinutes(timeStr: String): Int {
    val clean = timeStr.trim()
    if (clean.isEmpty()) return 0
    val isPm = clean.endsWith("PM", ignoreCase = true)
    val isAm = clean.endsWith("AM", ignoreCase = true)
    val timeOnly = clean.replace("(?i)AM".toRegex(), "").replace("(?i)PM".toRegex(), "").trim()
    val parts = timeOnly.split(":")
    if (parts.size < 2) return 0
    var h = parts[0].toIntOrNull() ?: 0
    val m = parts[1].filter { it.isDigit() }.take(2).toIntOrNull() ?: 0

    if (isPm && h < 12) h += 12
    if (isAm && h == 12) h = 0
    return ((h * 60 + m) % 1440 + 1440) % 1440
}

/**
 * Calculates exact dynamic clock time in 12-hour AM/PM format by adding dynamic delay minutes
 * to a scheduled time string (e.g. "09:30" + 18m -> "9:48 AM").
 */
fun calculateExactDynamicEta(scheduledTime: String, delayMinutes: Int): String {
    val clean = scheduledTime.trim()
    if (clean.isEmpty()) return ""
    val baseMinutes = parseTimeToMinutes(clean)
    val totalMinutes = ((baseMinutes + delayMinutes) % 1440 + 1440) % 1440

    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val amPm = if (h >= 12) "PM" else "AM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $amPm"
}

enum class OfficialRunStatus(val label: String, val colorHex: Long) {
    ACTIVE_NOW("RUNNING NOW", 0xFF059669),            // Green
    UPCOMING_TODAY("SCHEDULED TODAY", 0xFF2563EB),     // Blue
    COMPLETED_TODAY("RUN COMPLETED", 0xFF64748B)       // Gray
}

data class TrainScheduleActiveStatus(
    val status: OfficialRunStatus,
    val badgeText: String,
    val isCurrentlyActive: Boolean,
    val scheduledStartFormatted: String,
    val scheduledEndFormatted: String,
    val exactDynamicArrivalAtNashik: String,
    val scheduledArrivalAtNashik: String,
    val delayMinutes: Int,
    val statusNote: String
)

/**
 * Evaluates whether a train is actively on tracks right now based on its official schedule
 * (start departure and end arrival) and current IST clock time.
 */
fun evaluateTrainScheduleStatus(
    startDepTime: String,
    endArrTime: String,
    delayMinutes: Int,
    currentIst: IstTime,
    scheduledAtNashik: String = ""
): TrainScheduleActiveStatus {
    val startMin = parseTimeToMinutes(startDepTime)
    val scheduledEndMin = parseTimeToMinutes(endArrTime)
    val effectiveEndMin = ((scheduledEndMin + delayMinutes.coerceAtLeast(0)) % 1440 + 1440) % 1440
    val currentMin = currentIst.hour * 60 + currentIst.minute

    val isActive: Boolean
    val runStatus: OfficialRunStatus
    val note: String

    val isOvernight = effectiveEndMin < startMin

    if (!isOvernight) {
        when {
            currentMin in startMin..effectiveEndMin -> {
                isActive = true
                runStatus = OfficialRunStatus.ACTIVE_NOW
                note = "Active on corridor • Scheduled run from ${startDepTime.toStandard12Hour()} to ${endArrTime.toStandard12Hour()}"
            }
            currentMin < startMin -> {
                isActive = false
                runStatus = OfficialRunStatus.UPCOMING_TODAY
                val diffMin = startMin - currentMin
                val hours = diffMin / 60
                val mins = diffMin % 60
                val timeRemaining = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                note = "Starts journey at ${startDepTime.toStandard12Hour()} (in $timeRemaining)"
            }
            else -> {
                isActive = false
                runStatus = OfficialRunStatus.COMPLETED_TODAY
                note = "Journey completed at ${endArrTime.toStandard12Hour()}"
            }
        }
    } else {
        // Overnight journey spanning past midnight (e.g. 19:35 to 02:30 next morning)
        if (currentMin >= startMin || currentMin <= effectiveEndMin) {
            isActive = true
            runStatus = OfficialRunStatus.ACTIVE_NOW
            note = "Active overnight service • Dep: ${startDepTime.toStandard12Hour()} ➔ Arr: ${endArrTime.toStandard12Hour()}"
        } else {
            // Outside active window (e.g. morning/afternoon for an evening train)
            val diffToStart = if (currentMin < startMin) startMin - currentMin else (1440 - currentMin) + startMin
            val hours = diffToStart / 60
            val mins = diffToStart % 60
            val timeRemaining = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            // If it completed recently (within 3 hours after arrival)
            val minsSinceArr = currentMin - effectiveEndMin
            if (minsSinceArr in 1..180) {
                isActive = false
                runStatus = OfficialRunStatus.COMPLETED_TODAY
                note = "Overnight run completed at ${endArrTime.toStandard12Hour()} today"
            } else {
                isActive = false
                runStatus = OfficialRunStatus.UPCOMING_TODAY
                note = "Upcoming tonight: Departs at ${startDepTime.toStandard12Hour()} (in $timeRemaining)"
            }
        }
    }

    val exactEtaNashik = if (scheduledAtNashik.isNotBlank()) {
        calculateExactDynamicEta(scheduledAtNashik, delayMinutes)
    } else ""

    val badge = when (runStatus) {
        OfficialRunStatus.ACTIVE_NOW -> "🟢 ACTIVE NOW"
        OfficialRunStatus.UPCOMING_TODAY -> "🕒 SCHEDULED (${startDepTime.toStandard12Hour()})"
        OfficialRunStatus.COMPLETED_TODAY -> "🏁 COMPLETED"
    }

    return TrainScheduleActiveStatus(
        status = runStatus,
        badgeText = badge,
        isCurrentlyActive = isActive,
        scheduledStartFormatted = startDepTime.toStandard12Hour(),
        scheduledEndFormatted = endArrTime.toStandard12Hour(),
        exactDynamicArrivalAtNashik = exactEtaNashik,
        scheduledArrivalAtNashik = scheduledAtNashik.toStandard12Hour(),
        delayMinutes = delayMinutes,
        statusNote = note
    )
}

object IndianStandardTime {
    // Indian Standard Time is fixed UTC + 05:30 (+19,800,000 milliseconds)
    const val IST_OFFSET_MILLIS = 19_800_000L

    /**
     * Converts UTC epoch milliseconds to exact Indian Standard Time (IST).
     */
    fun fromEpochMillis(epochMillis: Long): IstTime {
        val istMillis = epochMillis + IST_OFFSET_MILLIS
        val totalSeconds = istMillis / 1000
        val second = (totalSeconds % 60).toInt().let { if (it < 0) it + 60 else it }
        val totalMinutes = totalSeconds / 60
        val minute = (totalMinutes % 60).toInt().let { if (it < 0) it + 60 else it }
        val totalHours = totalMinutes / 60
        val hour = (totalHours % 24).toInt().let { if (it < 0) it + 24 else it }

        // Civil date algorithm (Howard Hinnant Gregorian algorithm)
        val days = (totalHours / 24).toInt()
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + (if (mp < 10) 3 else -9)
        val year = y + (if (m <= 2) 1 else 0)

        val monthName = when (m) {
            1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
            7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
        }

        return IstTime(
            year = year,
            month = m,
            monthName = monthName,
            day = d,
            hour = hour,
            minute = minute,
            second = second,
            epochMillis = epochMillis
        )
    }
}
