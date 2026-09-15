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
    val time12: String get() = "${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')} $amPmStr"
    val time12Short: String get() = "${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPmStr"
    val dateStr: String get() = "$day $monthName $year"
    val fullFormatted: String get() = "$dateStr • $time12 IST"
    val compactBadge: String get() = "$time24 IST"

    /**
     * Computes remaining seconds until target scheduled arrival "HH:mm" in IST.
     */
    fun secondsUntil(targetHHmm: String): Long {
        val parts = targetHHmm.split(":")
        if (parts.size < 2) return 0L
        val targetH = parts[0].toIntOrNull() ?: return 0L
        val targetM = parts[1].toIntOrNull() ?: return 0L

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
    fun formatCountdownTo(targetHHmm: String): String {
        val diffSec = secondsUntil(targetHHmm)
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
