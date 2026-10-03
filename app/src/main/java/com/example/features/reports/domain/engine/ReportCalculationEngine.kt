package com.example.features.reports.domain.engine

import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
import com.example.data.database.ExpenseEntity
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.reports.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * UNIFIED REPORT CALCULATION ENGINE
 *
 * Single source of business logic for calculating owner-friendly reports,
 * occupancy metrics, financial balances, tenant statements, and monthly history.
 *
 * Driven exclusively by Room + Firestore source-of-truth data.
 */
object ReportCalculationEngine {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.US)
    private val DISPLAY_DATE_FORMAT = SimpleDateFormat("MMM d, yyyy", Locale.US)

    fun calculateReport(
        property: PropertyEntity?,
        period: ReportPeriod,
        rooms: List<RoomEntity>,
        beds: List<BedEntity>,
        tenants: List<TenantEntity>,
        assignments: List<BedAssignmentEntity>,
        payments: List<RentPaymentEntity>,
        expenses: List<ExpenseEntity>,
        lastBackupTimeStr: String = "Complete",
        lastSyncTimeStr: String = "Complete"
    ): CompleteOwnerReport {
        val now = Date()
        val generatedAtStr = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(now)
        val propertyName = property?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

        val activeRooms = rooms.filter { !it.deleted }
        val activeTenants = tenants.filter { !it.deleted && it.roomNumber.isNotBlank() }
        val activeTenantsCount = activeTenants.size

        // 1. PG CAPACITY CALCULATION (Total usable beds)
        val totalPgCapacity = activeRooms.sumOf { room ->
            val rBeds = beds.filter { it.roomNumber == room.roomNumber && !it.deleted }
            if (rBeds.isNotEmpty()) {
                rBeds.count { it.status != "BLOCKED" }
            } else {
                room.capacity
            }
        }
        val vacanciesCount = (totalPgCapacity - activeTenantsCount).coerceAtLeast(0)
        val homeOccupancyPercentage = if (totalPgCapacity > 0) {
            roundToTwoDecimals((activeTenantsCount.toDouble() / totalPgCapacity.toDouble()) * 100.0)
        } else 0.0

        val tenantsRatioText = "$activeTenantsCount / $totalPgCapacity Tenants"
        val occupancySummaryText = "${String.format(Locale.US, "%.1f", homeOccupancyPercentage)}% Occupancy • $vacanciesCount Vacanc${if (vacanciesCount == 1) "y" else "ies"}"

        // 2. PERIOD RESOLUTION
        val (canonicalMonth, periodRangeLabel, startDateStr, endDateStr) = when (period) {
            is ReportPeriod.Monthly -> {
                val parsed = RentBillingEngine.parseBillingMonth(period.billingMonth)
                val start = String.format("%04d-%02d-01", parsed.year, parsed.month1Based)
                val end = String.format("%04d-%02d-%02d", parsed.year, parsed.month1Based, parsed.daysInMonth)
                val monthName = parsed.canonicalName.substringBefore(" ")
                val rangeLabel = "$monthName 1–${parsed.daysInMonth}, ${parsed.year}"
                Tuple4(parsed.canonicalName, rangeLabel, start, end)
            }
            is ReportPeriod.CustomRange -> {
                val startCal = RentBillingEngine.parseDate(period.startDate)
                val monthLabel = if (startCal != null) RentBillingEngine.formatCanonicalBillingMonth(startCal.time) else "Custom Range"
                Tuple4(monthLabel, "${period.startDate} → ${period.endDate}", period.startDate, period.endDate)
            }
        }

        // 3. RENT METRICS CALCULATION
        val activePayments = payments.filter { !it.deleted }
        val currentMonthInvoices = if (period is ReportPeriod.Monthly) {
            activePayments.filter { it.billingMonth.trim().equals(canonicalMonth.trim(), ignoreCase = true) }
        } else {
            // For custom range: Invoices with due dates or billing months intersecting the range
            activePayments.filter { p ->
                val pDate = p.paymentDate ?: p.dueDate
                pDate in startDateStr..endDateStr
            }
        }

        val rentDue = roundToTwoDecimals(currentMonthInvoices.sumOf { it.amount })
        val rentCollectedCurrentMonth = roundToTwoDecimals(currentMonthInvoices.sumOf { minOf(it.amountPaid, it.amount) })
        val rentStillToCollect = roundToTwoDecimals(currentMonthInvoices.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) })
        val advanceCredit = roundToTwoDecimals(currentMonthInvoices.sumOf { (it.amountPaid - it.amount).coerceAtLeast(0.0) })

        // Previous Dues Received: payments for PRIOR billing months collected within this report period
        val previousDuesPayments = if (period is ReportPeriod.Monthly) {
            activePayments.filter { p ->
                !p.billingMonth.trim().equals(canonicalMonth.trim(), ignoreCase = true) &&
                p.amountPaid > 0 &&
                p.paymentDate != null &&
                p.paymentDate in startDateStr..endDateStr
            }
        } else {
            activePayments.filter { p ->
                p.paymentDate != null &&
                p.paymentDate in startDateStr..endDateStr &&
                !currentMonthInvoices.contains(p) &&
                p.amountPaid > 0
            }
        }
        val previousDuesReceived = roundToTwoDecimals(previousDuesPayments.sumOf { minOf(it.amountPaid, it.amount) })
        val totalRentCollected = roundToTwoDecimals(rentCollectedCurrentMonth + previousDuesReceived)
        val collectionRate = if (rentDue > 0.0) roundToTwoDecimals((rentCollectedCurrentMonth / rentDue) * 100.0) else 0.0

        val paidTenantsCount = currentMonthInvoices.count { it.status == "Paid" || (it.amountPaid >= it.amount && it.amount > 0) }
        val partiallyPaidTenantsCount = currentMonthInvoices.count { (it.status == "Partially Paid" || it.status == "Partial") || (it.amountPaid > 0 && it.amountPaid < it.amount) }
        val unpaidTenantsCount = currentMonthInvoices.count { (it.status == "Pending" || it.status == "Overdue") && it.amountPaid <= 0 }
        val overpaidTenantsCount = currentMonthInvoices.count { it.status == "Overpaid" || it.amountPaid > it.amount }

        // Prorated tenants calculation
        val tenantRentRecords = currentMonthInvoices.map { invoice ->
            val tenant = tenants.find { it.id == invoice.tenantId }
            val moveIn = tenant?.moveInDate.orEmpty()
            val leaving = tenant?.leavingDate.orEmpty()
            val proration = RentBillingEngine.calculateProrationDetails(moveIn, leaving, canonicalMonth)
            val isProrated = proration.isProrated
            val explanation = when {
                !isProrated -> "Full Monthly Rent"
                moveIn.isNotBlank() && proration.startDay > 1 -> "Prorated — Joined ${formatDateShort(moveIn)}"
                leaving.isNotBlank() && proration.endDay < proration.daysInMonth -> "Prorated — Vacating ${formatDateShort(leaving)}"
                else -> proration.prorationReason
            }

            val invDue = invoice.amount
            val invCollected = minOf(invoice.amountPaid, invDue)
            val invRemaining = (invDue - invoice.amountPaid).coerceAtLeast(0.0)
            val invAdvance = (invoice.amountPaid - invDue).coerceAtLeast(0.0)

            TenantRentRecord(
                tenantId = invoice.tenantId,
                tenantName = invoice.tenantName,
                roomNumber = invoice.roomNumber,
                bedId = tenant?.bedId ?: "",
                standardMonthlyRent = tenant?.monthlyRent ?: invDue,
                rentDue = roundToTwoDecimals(invDue),
                rentCollected = roundToTwoDecimals(invCollected),
                rentStillToCollect = roundToTwoDecimals(invRemaining),
                advanceCredit = roundToTwoDecimals(invAdvance),
                status = invoice.status,
                dueDate = invoice.dueDate,
                paymentDate = invoice.paymentDate,
                paymentMode = invoice.paymentMode,
                isProrated = isProrated,
                basisExplanation = explanation,
                applicableDays = proration.applicableDays,
                daysInMonth = proration.daysInMonth
            )
        }

        val proratedRecords = tenantRentRecords.filter { it.isProrated }
        val proratedTenantsCount = proratedRecords.size
        val totalProratedRent = roundToTwoDecimals(proratedRecords.sumOf { it.rentDue })

        val rentMetrics = RentMetrics(
            rentDue = rentDue,
            rentCollectedCurrentMonth = rentCollectedCurrentMonth,
            rentStillToCollect = rentStillToCollect,
            previousDuesReceived = previousDuesReceived,
            advanceCredit = advanceCredit,
            collectionRate = collectionRate,
            paidTenantsCount = paidTenantsCount,
            partiallyPaidTenantsCount = partiallyPaidTenantsCount,
            unpaidTenantsCount = unpaidTenantsCount,
            overpaidTenantsCount = overpaidTenantsCount,
            proratedTenantsCount = proratedTenantsCount,
            totalProratedRent = totalProratedRent,
            tenantRentRecords = tenantRentRecords
        )

        // 4. EXPENSE METRICS (Expense Date determines the month!)
        val activeExpenses = expenses.filter { !it.deleted }
        val periodExpenses = activeExpenses.filter { e ->
            if (period is ReportPeriod.Monthly) {
                e.date in startDateStr..endDateStr || e.date.startsWith(startDateStr.substring(0, 7))
            } else {
                e.date in startDateStr..endDateStr
            }
        }

        val totalExpenses = roundToTwoDecimals(periodExpenses.sumOf { it.amount })
        val paidExpenses = roundToTwoDecimals(periodExpenses.filter { !it.paymentMethod.equals("Pending", true) && !it.paymentMethod.equals("Unpaid", true) }.sumOf { it.amount })
        val unpaidExpenses = roundToTwoDecimals(periodExpenses.filter { it.paymentMethod.equals("Pending", true) || it.paymentMethod.equals("Unpaid", true) }.sumOf { it.amount })
        val outstandingExpenseAmount = unpaidExpenses
        val expenseCount = periodExpenses.size

        val categoryGroups = periodExpenses.groupBy { it.category.ifBlank { "Other" } }
        val categoryBreakdown = categoryGroups.map { (cat, list) ->
            val sum = roundToTwoDecimals(list.sumOf { it.amount })
            val pct = if (totalExpenses > 0) roundToTwoDecimals((sum / totalExpenses) * 100.0) else 0.0
            ExpenseCategoryBreakdown(cat, sum, pct, list.size)
        }.sortedByDescending { it.amount }

        val expenseReport = ExpenseReportMetrics(
            totalExpenses = totalExpenses,
            paidExpenses = paidExpenses,
            unpaidExpenses = unpaidExpenses,
            partiallyPaidExpenses = 0.0,
            outstandingExpenseAmount = outstandingExpenseAmount,
            expenseCount = expenseCount,
            categoryBreakdown = categoryBreakdown,
            expenseRecords = periodExpenses
        )

        // 5. MONEY LEFT AFTER EXPENSES
        val moneyLeftAfterExpenses = roundToTwoDecimals(totalRentCollected - totalExpenses)

        // 6. OWNER OVERVIEW
        val overview = OwnerOverviewReport(
            propertyName = propertyName,
            reportMonth = canonicalMonth,
            reportPeriod = periodRangeLabel,
            generatedAt = generatedAtStr,
            lastUpdated = generatedAtStr,
            activeTenantsCount = activeTenantsCount,
            totalPgCapacity = totalPgCapacity,
            occupancyPercentage = homeOccupancyPercentage,
            vacanciesCount = vacanciesCount,
            tenantsCapacityRatioText = tenantsRatioText,
            occupancySummaryText = occupancySummaryText,
            rentDue = rentDue,
            rentReceivedThisMonth = rentCollectedCurrentMonth,
            rentStillToCollect = rentStillToCollect,
            previousDuesReceived = previousDuesReceived,
            advanceCredit = advanceCredit,
            totalRentCollected = totalRentCollected,
            expenses = totalExpenses,
            paidExpenses = paidExpenses,
            unpaidExpenses = unpaidExpenses,
            moneyLeftAfterExpenses = moneyLeftAfterExpenses
        )

        // 7. BED-LEVEL OCCUPANCY & ROOM STATUS
        val todayStr = DATE_FORMAT.format(now)
        val roomDetails = activeRooms.map { room ->
            val rBeds = beds.filter { it.roomNumber == room.roomNumber && !it.deleted }
            val roomTenants = activeTenants.filter { it.roomNumber == room.roomNumber }
            val usable = if (rBeds.isNotEmpty()) rBeds.count { it.status != "BLOCKED" } else room.capacity
            val blocked = if (rBeds.isNotEmpty()) rBeds.count { it.status == "BLOCKED" } else 0
            val occupied = roomTenants.size.coerceAtMost(usable)
            val available = (usable - occupied).coerceAtLeast(0)
            val occPct = if (usable > 0) roundToTwoDecimals((occupied.toDouble() / usable.toDouble()) * 100.0) else 0.0

            val isVacatingSoon = roomTenants.any { t ->
                val l = t.leavingDate
                l.isNotBlank() && l >= todayStr && daysBetween(todayStr, l) <= 30
            }

            val status = when {
                occupied >= usable && isVacatingSoon -> "Full / Vacating Soon"
                occupied >= usable -> "Full"
                occupied > 0 && isVacatingSoon -> "Partially Occupied / Vacating Soon"
                occupied > 0 -> "Partially Occupied"
                else -> "Vacant"
            }

            RoomBedStatusDetail(
                roomNumber = room.roomNumber,
                floor = room.floor,
                capacity = room.capacity,
                usableBeds = usable,
                occupiedBeds = occupied,
                availableBeds = available,
                blockedBeds = blocked,
                occupancyPercentage = occPct,
                status = status,
                isVacatingSoon = isVacatingSoon,
                activeTenants = roomTenants.map { it.name },
                beds = rBeds
            )
        }

        val totalUsableBeds = roomDetails.sumOf { it.usableBeds }
        val totalOccupiedBeds = roomDetails.sumOf { it.occupiedBeds }
        val totalAvailableBeds = roomDetails.sumOf { it.availableBeds }
        val totalBlockedBeds = roomDetails.sumOf { it.blockedBeds }
        val bedOccupancyRate = if (totalUsableBeds > 0) roundToTwoDecimals((totalOccupiedBeds.toDouble() / totalUsableBeds.toDouble()) * 100.0) else 0.0

        val bedOccupancyReport = BedOccupancyReport(
            totalRooms = activeRooms.size,
            totalUsableBeds = totalUsableBeds,
            occupiedBeds = totalOccupiedBeds,
            availableBeds = totalAvailableBeds,
            blockedBeds = totalBlockedBeds,
            occupancyPercentage = bedOccupancyRate,
            roomsDetail = roomDetails
        )

        // 8. VACANCY REPORT
        val emptyRoomsCount = roomDetails.count { it.occupiedBeds == 0 }
        val partiallyOccupiedRoomsCount = roomDetails.count { it.occupiedBeds in 1 until it.usableBeds }
        val upcomingVacancies = activeTenants.filter { t ->
            val l = t.leavingDate
            l.isNotBlank() && l >= todayStr
        }.map { t ->
            UpcomingVacancyDetail(
                tenantId = t.id,
                tenantName = t.name,
                roomNumber = t.roomNumber,
                bedId = t.bedId,
                leavingDate = t.leavingDate,
                daysRemaining = daysBetween(todayStr, t.leavingDate).coerceAtLeast(0)
            )
        }.sortedBy { it.daysRemaining }

        val vacancyReport = VacancyReport(
            availableBedsCount = totalAvailableBeds,
            emptyRoomsCount = emptyRoomsCount,
            partiallyOccupiedRoomsCount = partiallyOccupiedRoomsCount,
            upcomingVacancies = upcomingVacancies
        )

        // 9. PAYMENT HISTORY
        val paymentHistoryItems = activePayments.filter { it.amountPaid > 0 }.map { p ->
            val t = tenants.find { it.id == p.tenantId }
            val allocation = when {
                p.billingMonth.trim().equals(canonicalMonth.trim(), ignoreCase = true) && p.amountPaid > p.amount -> "Advance / Credit"
                p.billingMonth.trim().equals(canonicalMonth.trim(), ignoreCase = true) -> "Current Month"
                else -> "Previous Dues"
            }
            PaymentHistoryItem(
                paymentId = p.id,
                paymentDate = p.paymentDate ?: p.dueDate,
                tenantId = p.tenantId,
                tenantName = p.tenantName,
                roomNumber = p.roomNumber,
                bedId = t?.bedId ?: "",
                billingMonth = p.billingMonth,
                amount = p.amountPaid,
                paymentMethod = p.paymentMode ?: "UPI",
                reference = p.transactionReference.orEmpty(),
                allocationType = allocation,
                notes = p.remarks.orEmpty()
            )
        }.sortedByDescending { it.paymentDate }

        // 10. TENANT STATEMENTS
        val tenantStatements = tenants.map { t ->
            val tPayments = activePayments.filter { it.tenantId == t.id }.sortedWith { p1, p2 ->
                val m1 = RentBillingEngine.parseBillingMonth(p1.billingMonth)
                val m2 = RentBillingEngine.parseBillingMonth(p2.billingMonth)
                (m1.year * 100 + m1.month1Based).compareTo(m2.year * 100 + m2.month1Based)
            }

            var prevOutstanding = 0.0
            var currMonthBal = 0.0
            var advCredit = 0.0

            val monthEntries = tPayments.map { p ->
                val due = p.amount
                val paid = p.amountPaid
                val bal = (due - paid).coerceAtLeast(0.0)
                val adv = (paid - due).coerceAtLeast(0.0)

                if (p.billingMonth.trim().equals(canonicalMonth.trim(), ignoreCase = true)) {
                    currMonthBal = bal
                    advCredit += adv
                } else {
                    prevOutstanding += bal
                    advCredit += adv
                }

                TenantMonthEntry(
                    month = p.billingMonth,
                    rentDue = roundToTwoDecimals(due),
                    paid = roundToTwoDecimals(paid),
                    balance = roundToTwoDecimals(bal),
                    status = p.status
                )
            }

            val totalDue = roundToTwoDecimals(prevOutstanding + currMonthBal)

            TenantStatementItem(
                tenantId = t.id,
                tenantName = t.name,
                roomNumber = t.roomNumber,
                bedId = t.bedId,
                phone = t.phone,
                monthlyHistory = monthEntries,
                previousOutstanding = roundToTwoDecimals(prevOutstanding),
                currentMonthBalance = roundToTwoDecimals(currMonthBal),
                totalCurrentlyDue = totalDue,
                advanceCredit = roundToTwoDecimals(advCredit)
            )
        }

        // 11. MONTHLY HISTORY (Historical reproducible monthly summary)
        val allMonthStrings = (activePayments.map { it.billingMonth } + listOf(canonicalMonth))
            .filter { it.isNotBlank() }
            .distinct()

        val monthlyHistory = allMonthStrings.map { mStr ->
            val parsedM = RentBillingEngine.parseBillingMonth(mStr)
            val mStart = String.format("%04d-%02d-01", parsedM.year, parsedM.month1Based)
            val mEnd = String.format("%04d-%02d-%02d", parsedM.year, parsedM.month1Based, parsedM.daysInMonth)

            val mPayments = activePayments.filter { it.billingMonth.trim().equals(parsedM.canonicalName.trim(), ignoreCase = true) }
            val mRentDue = roundToTwoDecimals(mPayments.sumOf { it.amount })
            val mRentColl = roundToTwoDecimals(mPayments.sumOf { minOf(it.amountPaid, it.amount) })
            val mRentStill = roundToTwoDecimals(mPayments.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) })
            val mAdv = roundToTwoDecimals(mPayments.sumOf { (it.amountPaid - it.amount).coerceAtLeast(0.0) })

            val mPrevDues = roundToTwoDecimals(activePayments.filter { p ->
                !p.billingMonth.trim().equals(parsedM.canonicalName.trim(), ignoreCase = true) &&
                p.paymentDate != null && p.paymentDate in mStart..mEnd
            }.sumOf { minOf(it.amountPaid, it.amount) })

            val mExpenses = roundToTwoDecimals(activeExpenses.filter { e ->
                e.date in mStart..mEnd || e.date.startsWith(mStart.substring(0, 7))
            }.sumOf { it.amount })

            val mMoneyLeft = roundToTwoDecimals((mRentColl + mPrevDues) - mExpenses)

            val mActiveTenants = mPayments.map { it.tenantId }.distinct().size.let { if (it > 0) it else activeTenantsCount }
            val mOccPct = if (totalPgCapacity > 0) roundToTwoDecimals((mActiveTenants.toDouble() / totalPgCapacity.toDouble()) * 100.0) else 0.0

            MonthlyHistorySummary(
                month = parsedM.canonicalName,
                rentDue = mRentDue,
                rentCollected = mRentColl,
                previousDuesReceived = mPrevDues,
                advanceCredit = mAdv,
                rentStillToCollect = mRentStill,
                expenses = mExpenses,
                moneyLeftAfterExpenses = mMoneyLeft,
                activeTenants = mActiveTenants,
                totalCapacity = totalPgCapacity,
                occupancyPercentage = mOccPct,
                availableBeds = (totalPgCapacity - mActiveTenants).coerceAtLeast(0)
            )
        }.sortedWith { h1, h2 ->
            val p1 = RentBillingEngine.parseBillingMonth(h1.month)
            val p2 = RentBillingEngine.parseBillingMonth(h2.month)
            (p2.year * 100 + p2.month1Based).compareTo(p1.year * 100 + p1.month1Based)
        }

        // 12. WHAT NEEDS ATTENTION
        val attentionList = mutableListOf<AttentionItem>()

        // A. Tenants with rent remaining
        val tenantsWithDue = currentMonthInvoices.filter { (it.amount - it.amountPaid) > 0.0 }
        if (tenantsWithDue.isNotEmpty()) {
            val dueCount = tenantsWithDue.size
            val topTenant = tenantsWithDue.first()
            val remainingAmt = topTenant.amount - topTenant.amountPaid
            val subtitleText = if (dueCount == 1) {
                "${topTenant.tenantName} — ₹${String.format(Locale.US, "%,.0f", remainingAmt)} remaining"
            } else {
                "${topTenant.tenantName} (₹${String.format(Locale.US, "%,.0f", remainingAmt)}) and ${dueCount - 1} other${if (dueCount > 2) "s" else ""}"
            }
            attentionList.add(
                AttentionItem(
                    type = AttentionType.RENT_REMAINING,
                    title = "$dueCount tenant${if (dueCount == 1) "" else "s"} ha${if (dueCount == 1) "s" else "ve"} rent remaining",
                    subtitle = subtitleText,
                    severity = "WARNING",
                    actionRoute = "rent_ledger"
                )
            )
        }

        // B. Vacancy Alert
        if (totalAvailableBeds > 0) {
            val sampleVacantRoom = roomDetails.firstOrNull { it.availableBeds > 0 }
            val roomDesc = if (sampleVacantRoom != null) "Room ${sampleVacantRoom.roomNumber} (${sampleVacantRoom.availableBeds} bed${if (sampleVacantRoom.availableBeds == 1) "" else "s"} available)" else "$totalAvailableBeds beds available"
            attentionList.add(
                AttentionItem(
                    type = AttentionType.VACANCY,
                    title = "$totalAvailableBeds vacanc${if (totalAvailableBeds == 1) "y" else "ies"}",
                    subtitle = roomDesc,
                    severity = "INFO",
                    actionRoute = "rooms"
                )
            )
        }

        // C. Tenants vacating soon
        if (upcomingVacancies.isNotEmpty()) {
            val vacCount = upcomingVacancies.size
            val nextVac = upcomingVacancies.first()
            val vacSubtitle = "${nextVac.tenantName} — ${formatDateShort(nextVac.leavingDate)} (${nextVac.daysRemaining} days left in Room ${nextVac.roomNumber})"
            attentionList.add(
                AttentionItem(
                    type = AttentionType.VACATING_SOON,
                    title = "$vacCount tenant${if (vacCount == 1) "" else "s"} vacating soon",
                    subtitle = vacSubtitle,
                    severity = "ALERT",
                    actionRoute = "tenants"
                )
            )
        }

        // D. Unpaid Expenses
        val unpaidList = periodExpenses.filter { it.paymentMethod.equals("Pending", true) || it.paymentMethod.equals("Unpaid", true) }
        if (unpaidList.isNotEmpty()) {
            val unCount = unpaidList.size
            val topUnpaid = unpaidList.first()
            val totalUnpaidAmt = unpaidList.sumOf { it.amount }
            val subtitle = "${topUnpaid.title.ifBlank { topUnpaid.category }} — ₹${String.format(Locale.US, "%,.0f", topUnpaid.amount)}"
            attentionList.add(
                AttentionItem(
                    type = AttentionType.UNPAID_EXPENSE,
                    title = "$unCount unpaid expense${if (unCount == 1) "" else "s"} (Total ₹${String.format(Locale.US, "%,.0f", totalUnpaidAmt)})",
                    subtitle = subtitle,
                    severity = "WARNING",
                    actionRoute = "expenses"
                )
            )
        }

        // 13. DATA FRESHNESS
        val dataFreshness = DataFreshness(
            lastSynchronized = lastSyncTimeStr,
            lastReportUpdate = TIME_FORMAT.format(now),
            lastDriveBackup = lastBackupTimeStr,
            backupStatus = if (lastBackupTimeStr.isNotBlank() && !lastBackupTimeStr.contains("Failed", true)) "Complete" else "Pending"
        )

        return CompleteOwnerReport(
            period = period,
            overview = overview,
            rentMetrics = rentMetrics,
            paymentHistory = paymentHistoryItems,
            expenseReport = expenseReport,
            bedOccupancy = bedOccupancyReport,
            vacancyReport = vacancyReport,
            tenantStatements = tenantStatements,
            monthlyHistory = monthlyHistory,
            attentionItems = attentionList,
            dataFreshness = dataFreshness
        )
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return (value * 100.0).roundToLong() / 100.0
    }

    private fun formatDateShort(dateStr: String): String {
        return try {
            val d = DATE_FORMAT.parse(dateStr)
            if (d != null) DISPLAY_DATE_FORMAT.format(d) else dateStr
        } catch (_: Exception) {
            dateStr
        }
    }

    private fun daysBetween(startStr: String, endStr: String): Int {
        return try {
            val d1 = DATE_FORMAT.parse(startStr)?.time ?: return 0
            val d2 = DATE_FORMAT.parse(endStr)?.time ?: return 0
            val diff = d2 - d1
            (diff / (1000L * 60 * 60 * 24)).toInt()
        } catch (_: Exception) {
            0
        }
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
