package com.example.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Enterprise date utility providing canonical normalization and comparison
 * for dates in various formats (e.g., "2026-10-1", "2026-10-01", "2026/10/1", "01-10-2026").
 * Prevents lexicographical string comparison bugs where "2026-10-1" > "2026-10-06".
 */
object PgDateUtil {

    private val ISO_FORMAT_STRING = "yyyy-MM-dd"

    fun todayIso(): String {
        return SimpleDateFormat(ISO_FORMAT_STRING, Locale.US).format(Date())
    }

    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        val patterns = arrayOf(
            "yyyy-MM-dd",
            "yyyy-M-d",
            "yyyy-MM-d",
            "yyyy-M-dd",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "yyyy/MM/dd",
            "yyyy/M/d"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }

        // Fallback separator split
        return try {
            val parts = trimmed.split('-', '/', '.').mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (parts[0] > 1000) { // yyyy-M-d
                    cal.set(parts[0], parts[1] - 1, parts[2])
                } else { // d-M-yyyy
                    cal.set(parts[2], parts[1] - 1, parts[0])
                }
                cal.time
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes any supported date string to strict "yyyy-MM-dd" format.
     */
    fun normalizeDate(dateStr: String?): String? {
        val parsed = parseDate(dateStr) ?: return null
        return SimpleDateFormat(ISO_FORMAT_STRING, Locale.US).format(parsed)
    }

    /**
     * Returns true if date1 is strictly AFTER date2 (date1 > date2).
     */
    fun isDateAfter(dateStr1: String?, dateStr2: String?): Boolean {
        if (dateStr1.isNullOrBlank() || dateStr2.isNullOrBlank()) return false
        val d1 = parseDate(dateStr1) ?: return false
        val d2 = parseDate(dateStr2) ?: return false
        return d1.after(d2)
    }

    /**
     * Returns true if date1 is on or before date2 (date1 <= date2).
     */
    fun isDateOnOrBefore(dateStr1: String?, dateStr2: String?): Boolean {
        if (dateStr1.isNullOrBlank() || dateStr2.isNullOrBlank()) return false
        val d1 = parseDate(dateStr1) ?: return false
        val d2 = parseDate(dateStr2) ?: return false
        return !d1.after(d2)
    }

    /**
     * Returns true if dateStr is in the past or today (dateStr <= today).
     */
    fun isDatePastOrToday(dateStr: String?, todayReferenceStr: String? = null): Boolean {
        if (dateStr.isNullOrBlank()) return false
        val todayStr = todayReferenceStr ?: todayIso()
        return isDateOnOrBefore(dateStr, todayStr)
    }

    /**
     * Returns true if dateStr is strictly in the future (dateStr > today).
     */
    fun isDateFuture(dateStr: String?, todayReferenceStr: String? = null): Boolean {
        if (dateStr.isNullOrBlank()) return false
        val todayStr = todayReferenceStr ?: todayIso()
        return isDateAfter(dateStr, todayStr)
    }
}
