package com.example.features.rent.domain.util

import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.rent.domain.model.MonthlyRentStats
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.domain.model.ProrationDetails
import com.example.features.rent.domain.model.TenantMonthPreview
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

object RentBillingEngine {

    private val MONTH_YEAR_FORMATS = listOf(
        SimpleDateFormat("MMMM yyyy", Locale.US),
        SimpleDateFormat("MMM yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM", Locale.US),
        SimpleDateFormat("MM/yyyy", Locale.US)
    )

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("yyyy/MM/dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US)
    )

    /**
     * Formats a Date to canonical billing month string: "October 2026".
     */
    fun formatCanonicalBillingMonth(date: Date): String {
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
    }

    /**
     * Formats a calendar year and 1-based month (1=Jan, 12=Dec) to "October 2026".
     */
    fun formatCanonicalBillingMonth(year: Int, month1Based: Int): String {
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    /**
     * Returns the immediately preceding calendar billing month name (e.g. "October 2026" -> "September 2026").
     */
    fun getPreviousBillingMonth(monthStr: String): String {
        val parsed = parseBillingMonth(monthStr)
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, parsed.year)
            set(Calendar.MONTH, parsed.month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return formatCanonicalBillingMonth(cal.time)
    }

    /**
     * Returns the immediately succeeding calendar billing month name (e.g. "October 2026" -> "November 2026").
     */
    fun getNextBillingMonth(monthStr: String): String {
        val parsed = parseBillingMonth(monthStr)
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, parsed.year)
            set(Calendar.MONTH, parsed.month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        return formatCanonicalBillingMonth(cal.time)
    }

    /**
     * Parses any supported billing month string into (Year, Month 1-12, DaysInMonth).
     * If invalid or blank, defaults to the current month.
     */
    fun parseBillingMonth(monthStr: String): ParsedBillingMonth {
        val trimmed = monthStr.trim()
        if (trimmed.isNotBlank()) {
            for (format in MONTH_YEAR_FORMATS) {
                try {
                    format.isLenient = false
                    val parsed = format.parse(trimmed)
                    if (parsed != null) {
                        val cal = Calendar.getInstance(Locale.US).apply { time = parsed }
                        val year = cal.get(Calendar.YEAR)
                        val month1Based = cal.get(Calendar.MONTH) + 1
                        val daysInMonth = getDaysInMonth(year, month1Based)
                        val canonicalName = formatCanonicalBillingMonth(year, month1Based)
                        return ParsedBillingMonth(year, month1Based, daysInMonth, canonicalName)
                    }
                } catch (_: Exception) {}
            }
        }

        // Default to current calendar month
        val nowCal = Calendar.getInstance(Locale.US)
        val year = nowCal.get(Calendar.YEAR)
        val month1Based = nowCal.get(Calendar.MONTH) + 1
        val daysInMonth = getDaysInMonth(year, month1Based)
        val canonicalName = formatCanonicalBillingMonth(year, month1Based)
        return ParsedBillingMonth(year, month1Based, daysInMonth, canonicalName)
    }

    /**
     * Computes the exact number of days in a specific calendar month.
     * Accurately handles leap years for February (28 vs 29), 30-day, and 31-day months.
     */
    fun getDaysInMonth(year: Int, month1Based: Int): Int {
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /**
     * Parses a date string ("yyyy-MM-dd", etc.) into a Calendar instance.
     */
    fun parseDate(dateStr: String?): Calendar? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        for (fmt in DATE_FORMATS) {
            try {
                fmt.isLenient = false
                val d = fmt.parse(trimmed)
                if (d != null) {
                    val cal = Calendar.getInstance(Locale.US).apply { time = d }
                    return cal
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Calculates the applicable stay days and proration details for a tenant
     * for a given calendar billing month.
     */
    fun calculateProrationDetails(
        moveInDateStr: String,
        leavingDateStr: String?,
        billingMonthStr: String
    ): ProrationDetails {
        val parsedMonth = parseBillingMonth(billingMonthStr)
        val year = parsedMonth.year
        val month1Based = parsedMonth.month1Based
        val daysInMonth = parsedMonth.daysInMonth

        val moveInCal = parseDate(moveInDateStr)
        val leavingCal = parseDate(leavingDateStr)

        // Month start & end dates in YYYYMMDD integer for fast comparison
        val monthStartInt = year * 10000 + month1Based * 100 + 1
        val monthEndInt = year * 10000 + month1Based * 100 + daysInMonth

        val moveInInt = if (moveInCal != null) {
            moveInCal.get(Calendar.YEAR) * 10000 + (moveInCal.get(Calendar.MONTH) + 1) * 100 + moveInCal.get(Calendar.DAY_OF_MONTH)
        } else {
            0
        }

        val leavingInt = if (leavingCal != null) {
            leavingCal.get(Calendar.YEAR) * 10000 + (leavingCal.get(Calendar.MONTH) + 1) * 100 + leavingCal.get(Calendar.DAY_OF_MONTH)
        } else {
            Int.MAX_VALUE
        }

        // Case 1: Tenant joins AFTER this month ends
        if (moveInInt > monthEndInt) {
            return ProrationDetails(
                moveInDate = moveInDateStr,
                leavingDate = leavingDateStr,
                billingMonth = parsedMonth.canonicalName,
                daysInMonth = daysInMonth,
                startDay = 0,
                endDay = 0,
                applicableDays = 0,
                isProrated = false,
                prorationReason = "Joins in future month"
            )
        }

        // Case 2: Tenant left BEFORE this month starts
        if (leavingInt < monthStartInt) {
            return ProrationDetails(
                moveInDate = moveInDateStr,
                leavingDate = leavingDateStr,
                billingMonth = parsedMonth.canonicalName,
                daysInMonth = daysInMonth,
                startDay = 0,
                endDay = 0,
                applicableDays = 0,
                isProrated = false,
                prorationReason = "Vacated before this month"
            )
        }

        // Determine Start Day
        val startDay = if (moveInCal != null && moveInCal.get(Calendar.YEAR) == year && (moveInCal.get(Calendar.MONTH) + 1) == month1Based) {
            moveInCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
        } else {
            1
        }

        // Determine End Day
        val endDay = if (leavingCal != null && leavingCal.get(Calendar.YEAR) == year && (leavingCal.get(Calendar.MONTH) + 1) == month1Based) {
            leavingCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
        } else {
            daysInMonth
        }

        val applicableDays = if (endDay >= startDay) {
            endDay - startDay + 1
        } else {
            0
        }

        val isJoinedMidMonth = startDay > 1
        val isLeavingMidMonth = endDay < daysInMonth
        val isProrated = applicableDays in 1 until daysInMonth

        val prorationReason = when {
            applicableDays <= 0 -> "Not staying this month"
            isJoinedMidMonth && isLeavingMidMonth -> "Joined day $startDay, leaving day $endDay ($applicableDays days)"
            isJoinedMidMonth -> "Joined day $startDay ($applicableDays/$daysInMonth days)"
            isLeavingMidMonth -> "Leaving day $endDay ($applicableDays/$daysInMonth days)"
            else -> "Full month ($daysInMonth days)"
        }

        return ProrationDetails(
            moveInDate = moveInDateStr,
            leavingDate = leavingDateStr,
            billingMonth = parsedMonth.canonicalName,
            daysInMonth = daysInMonth,
            startDay = startDay,
            endDay = endDay,
            applicableDays = applicableDays,
            isProrated = isProrated,
            prorationReason = prorationReason
        )
    }

    /**
     * Calculates the expected rent using safe currency arithmetic.
     *
     * Daily Rent = Monthly Rent / DaysInMonth
     * Prorated Rent = Monthly Rent * ApplicableDays / DaysInMonth
     *
     * Example: ₹6,000, 17 applicable days in 31-day month:
     * 6000 * 17 / 31 = 3290.32
     */
    fun calculateExpectedRent(monthlyRent: Double, applicableDays: Int, daysInMonth: Int): Double {
        if (applicableDays <= 0 || monthlyRent <= 0 || daysInMonth <= 0) return 0.0
        if (applicableDays >= daysInMonth) return monthlyRent

        // Currency arithmetic rounded to 2 decimal places (paise)
        val rawAmount = (monthlyRent * applicableDays) / daysInMonth.toDouble()
        return (rawAmount * 100.0).roundToLong() / 100.0
    }

    /**
     * Generates a stable, deterministic cloudId for a rent record to enforce
     * ONE PROPERTY + ONE TENANT + ONE CALENDAR MONTH = ONE RENT RECORD.
     */
    fun generateDeterministicCloudId(propertyId: String, tenantId: Int, billingMonth: String): String {
        val parsed = parseBillingMonth(billingMonth)
        val cleanProp = if (propertyId.isNotBlank()) propertyId.replace("[^a-zA-Z0-9_]".toRegex(), "") else "default"
        return "payment_${cleanProp}_${tenantId}_${parsed.year}_${parsed.month1Based}"
    }

    /**
     * Determines payment status based on expected amount, amount paid, and due date.
     */
    fun calculatePaymentStatus(
        expectedAmount: Double,
        amountPaid: Double,
        dueDateStr: String? = null
    ): String {
        if (amountPaid <= 0.0) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return if (!dueDateStr.isNullOrBlank() && dueDateStr < todayStr) {
                "Overdue"
            } else {
                "Pending"
            }
        }
        if (amountPaid >= expectedAmount && expectedAmount > 0.0) {
            return if (amountPaid > expectedAmount) "Overpaid" else "Paid"
        }
        if (amountPaid > 0.0 && amountPaid < expectedAmount) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return if (!dueDateStr.isNullOrBlank() && dueDateStr < todayStr) {
                "Overdue"
            } else {
                "Partially Paid"
            }
        }
        return "Paid"
    }

    /**
     * Generates a preview for generating a new month's rent for a property.
     */
    fun generateMonthPreview(
        propertyId: String,
        propertyName: String,
        billingMonthStr: String,
        allTenants: List<TenantEntity>,
        existingMonthPayments: List<RentPaymentEntity>
    ): MonthGenerationPreview {
        val parsedMonth = parseBillingMonth(billingMonthStr)
        val activeTenants = allTenants.filter { !it.deleted && it.roomNumber.isNotBlank() }

        val existingTenantIds = existingMonthPayments
            .filter { !it.deleted }
            .map { it.tenantId }
            .toSet()

        val previews = mutableListOf<TenantMonthPreview>()
        var fullMonthCount = 0
        var proratedCount = 0
        var leavingCount = 0
        var expectedTotal = 0.0

        for (tenant in activeTenants) {
            val proration = calculateProrationDetails(
                moveInDateStr = tenant.moveInDate,
                leavingDateStr = tenant.leavingDate,
                billingMonthStr = parsedMonth.canonicalName
            )

            if (proration.applicableDays > 0) {
                val expectedRent = calculateExpectedRent(
                    monthlyRent = tenant.monthlyRent,
                    applicableDays = proration.applicableDays,
                    daysInMonth = proration.daysInMonth
                )

                val alreadyExists = existingTenantIds.contains(tenant.id)
                if (proration.isProrated) {
                    proratedCount++
                } else {
                    fullMonthCount++
                }
                if (!tenant.leavingDate.isNullOrBlank() && proration.endDay < proration.daysInMonth) {
                    leavingCount++
                }

                expectedTotal += expectedRent

                previews.add(
                    TenantMonthPreview(
                        tenantId = tenant.id,
                        tenantCloudId = tenant.cloudId,
                        tenantName = tenant.name,
                        roomNumber = tenant.roomNumber,
                        monthlyRent = tenant.monthlyRent,
                        applicableDays = proration.applicableDays,
                        daysInMonth = proration.daysInMonth,
                        expectedRent = expectedRent,
                        isProrated = proration.isProrated,
                        prorationReason = proration.prorationReason,
                        alreadyExists = alreadyExists
                    )
                )
            }
        }

        val isAlreadyGenerated = existingMonthPayments.any { !it.deleted }

        return MonthGenerationPreview(
            propertyId = propertyId,
            propertyName = propertyName,
            billingMonth = parsedMonth.canonicalName,
            daysInMonth = parsedMonth.daysInMonth,
            totalApplicableTenants = previews.size,
            fullMonthTenants = fullMonthCount,
            proratedTenants = proratedCount,
            leavingTenants = leavingCount,
            expectedTotalRent = (expectedTotal * 100.0).roundToLong() / 100.0,
            isAlreadyGenerated = isAlreadyGenerated,
            existingRecordsCount = existingMonthPayments.count { !it.deleted },
            tenantPreviews = previews
        )
    }

    /**
     * Computes unified financial metrics for any collection of RentPaymentEntity records.
     */
    fun computeMonthlyRentStats(
        billingMonth: String,
        payments: List<RentPaymentEntity>
    ): MonthlyRentStats {
        val activePayments = payments.filter { !it.deleted }
        val totalExpected = activePayments.sumOf { it.amount }
        val totalPaid = activePayments.sumOf { it.amountPaid }
        val totalOutstanding = activePayments.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) }

        val paidTenants = activePayments.count { it.status.equals("Paid", ignoreCase = true) || it.status.equals("Overpaid", ignoreCase = true) }
        val partialTenants = activePayments.count { it.status.equals("Partially Paid", ignoreCase = true) || it.status.equals("Partial", ignoreCase = true) }
        val pendingTenants = activePayments.count { it.status.equals("Pending", ignoreCase = true) || it.status.equals("Overdue", ignoreCase = true) }

        val collectionPercentage = if (totalExpected > 0) {
            ((totalPaid / totalExpected) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        return MonthlyRentStats(
            billingMonth = billingMonth,
            totalExpectedRent = (totalExpected * 100.0).roundToLong() / 100.0,
            totalPaid = (totalPaid * 100.0).roundToLong() / 100.0,
            totalOutstanding = (totalOutstanding * 100.0).roundToLong() / 100.0,
            totalTenantsCount = activePayments.size,
            paidTenantsCount = paidTenants,
            partiallyPaidTenantsCount = partialTenants,
            pendingTenantsCount = pendingTenants,
            proratedTenantsCount = 0,
            collectionPercentage = (collectionPercentage * 10.0).roundToLong() / 10.0
        )
    }
}

data class ParsedBillingMonth(
    val year: Int,
    val month1Based: Int,
    val daysInMonth: Int,
    val canonicalName: String
)
