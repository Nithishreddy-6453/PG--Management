package com.example.features.reports.domain.model

import com.example.data.database.BedEntity
import com.example.data.database.ExpenseEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity

// ==========================================
// 1. REPORT PERIOD
// ==========================================
sealed class ReportPeriod {
    data class Monthly(val billingMonth: String) : ReportPeriod()
    data class CustomRange(val startDate: String, val endDate: String) : ReportPeriod()

    fun displayLabel(language: String = "EN"): String = when (this) {
        is Monthly -> billingMonth
        is CustomRange -> if (language == "తెలుగు") "$startDate నుండి $endDate" else "$startDate → $endDate"
    }

    val isMonthly: Boolean get() = this is Monthly
}

// ==========================================
// 2. OWNER OVERVIEW
// ==========================================
data class OwnerOverviewReport(
    val propertyName: String,
    val reportMonth: String,
    val reportPeriod: String,
    val generatedAt: String,
    val lastUpdated: String,
    // Occupancy
    val activeTenantsCount: Int,
    val totalPgCapacity: Int,
    val occupancyPercentage: Double,
    val vacanciesCount: Int,
    val tenantsCapacityRatioText: String,
    val occupancySummaryText: String,
    // Money
    val rentDue: Double,
    val rentReceivedThisMonth: Double,
    val rentStillToCollect: Double,
    val previousDuesReceived: Double,
    val advanceCredit: Double,
    val totalRentCollected: Double,
    val expenses: Double,
    val paidExpenses: Double,
    val unpaidExpenses: Double,
    val moneyLeftAfterExpenses: Double
)

// ==========================================
// 3. RENT COLLECTION & METRICS
// ==========================================
data class RentMetrics(
    val rentDue: Double,
    val rentCollectedCurrentMonth: Double,
    val rentStillToCollect: Double,
    val previousDuesReceived: Double,
    val advanceCredit: Double,
    val collectionRate: Double,
    val paidTenantsCount: Int,
    val partiallyPaidTenantsCount: Int,
    val unpaidTenantsCount: Int,
    val overpaidTenantsCount: Int,
    val proratedTenantsCount: Int,
    val totalProratedRent: Double,
    val tenantRentRecords: List<TenantRentRecord>
)

data class TenantRentRecord(
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val bedId: String,
    val standardMonthlyRent: Double,
    val rentDue: Double,
    val rentCollected: Double,
    val rentStillToCollect: Double,
    val advanceCredit: Double,
    val status: String, // Paid, Partially Paid, Pending, Overpaid, Overdue
    val dueDate: String,
    val paymentDate: String?,
    val paymentMode: String?,
    val isProrated: Boolean,
    val basisExplanation: String, // e.g. "Prorated — Joined Oct 15", "Full Monthly Rent"
    val applicableDays: Int,
    val daysInMonth: Int
)

// ==========================================
// 4. PAYMENT HISTORY
// ==========================================
data class PaymentHistoryItem(
    val paymentId: Int,
    val paymentDate: String,
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val bedId: String,
    val billingMonth: String,
    val amount: Double,
    val paymentMethod: String,
    val reference: String,
    val allocationType: String, // "Current Month", "Previous Dues", "Advance / Credit", "Adjustment"
    val notes: String
)

// ==========================================
// 5. EXPENSE REPORT METRICS
// ==========================================
data class ExpenseReportMetrics(
    val totalExpenses: Double,
    val paidExpenses: Double,
    val unpaidExpenses: Double,
    val partiallyPaidExpenses: Double,
    val outstandingExpenseAmount: Double,
    val expenseCount: Int,
    val categoryBreakdown: List<ExpenseCategoryBreakdown>,
    val expenseRecords: List<ExpenseEntity>
)

data class ExpenseCategoryBreakdown(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val count: Int
)

// ==========================================
// 6. BED-LEVEL OCCUPANCY & ROOM STATUS
// ==========================================
data class BedOccupancyReport(
    val totalRooms: Int,
    val totalUsableBeds: Int,
    val occupiedBeds: Int,
    val availableBeds: Int,
    val blockedBeds: Int,
    val occupancyPercentage: Double,
    val roomsDetail: List<RoomBedStatusDetail>
)

data class RoomBedStatusDetail(
    val roomNumber: String,
    val floor: String,
    val capacity: Int,
    val usableBeds: Int,
    val occupiedBeds: Int,
    val availableBeds: Int,
    val blockedBeds: Int,
    val occupancyPercentage: Double,
    val status: String, // "Full", "Partially Occupied", "Vacant", "Full / Vacating Soon", "Partially Occupied / Vacating Soon"
    val isVacatingSoon: Boolean,
    val activeTenants: List<String>,
    val beds: List<BedEntity>
)

// ==========================================
// 7. VACANCY REPORT
// ==========================================
data class VacancyReport(
    val availableBedsCount: Int,
    val emptyRoomsCount: Int,
    val partiallyOccupiedRoomsCount: Int,
    val upcomingVacancies: List<UpcomingVacancyDetail>
)

data class UpcomingVacancyDetail(
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val bedId: String,
    val leavingDate: String,
    val daysRemaining: Int
)

// ==========================================
// 8. TENANT STATEMENTS
// ==========================================
data class TenantStatementItem(
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val bedId: String,
    val phone: String,
    val monthlyHistory: List<TenantMonthEntry>,
    val previousOutstanding: Double,
    val currentMonthBalance: Double,
    val totalCurrentlyDue: Double,
    val advanceCredit: Double
)

data class TenantMonthEntry(
    val month: String,
    val rentDue: Double,
    val paid: Double,
    val balance: Double,
    val status: String
)

// ==========================================
// 9. MONTHLY HISTORY
// ==========================================
data class MonthlyHistorySummary(
    val month: String,
    val rentDue: Double,
    val rentCollected: Double,
    val previousDuesReceived: Double,
    val advanceCredit: Double,
    val rentStillToCollect: Double,
    val expenses: Double,
    val moneyLeftAfterExpenses: Double,
    val activeTenants: Int,
    val totalCapacity: Int,
    val occupancyPercentage: Double,
    val availableBeds: Int
)

// ==========================================
// 10. WHAT NEEDS ATTENTION
// ==========================================
enum class AttentionType {
    RENT_REMAINING,
    VACANCY,
    VACATING_SOON,
    UNPAID_EXPENSE
}

data class AttentionItem(
    val type: AttentionType,
    val title: String,
    val subtitle: String,
    val severity: String = "WARNING", // "INFO", "WARNING", "ALERT"
    val actionRoute: String? = null
)

// ==========================================
// 11. DATA FRESHNESS
// ==========================================
data class DataFreshness(
    val lastSynchronized: String,
    val lastReportUpdate: String,
    val lastDriveBackup: String,
    val backupStatus: String
)

// ==========================================
// 12. UNIFIED COMPLETE OWNER REPORT MODEL
// ==========================================
data class CompleteOwnerReport(
    val period: ReportPeriod,
    val overview: OwnerOverviewReport,
    val rentMetrics: RentMetrics,
    val paymentHistory: List<PaymentHistoryItem>,
    val expenseReport: ExpenseReportMetrics,
    val bedOccupancy: BedOccupancyReport,
    val vacancyReport: VacancyReport,
    val tenantStatements: List<TenantStatementItem>,
    val monthlyHistory: List<MonthlyHistorySummary>,
    val attentionItems: List<AttentionItem>,
    val dataFreshness: DataFreshness
)

// ==========================================
// BACKWARD COMPATIBILITY MODELS
// ==========================================
data class FinancialMetrics(
    val totalRevenue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val outstandingRent: Double = 0.0,
    val collectionRate: Double = 0.0,
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyRate: Double = 0.0,
    val vacancyRate: Double = 0.0
)

data class ChartPoint(
    val label: String,
    val value: Double
)

data class RevenueReportData(
    val dailyRevenue: List<ChartPoint> = emptyList(),
    val monthlyRevenue: List<ChartPoint> = emptyList(),
    val yearlyRevenue: List<ChartPoint> = emptyList()
)

data class ExpenseReportData(
    val categoryBreakdown: List<ChartPoint> = emptyList(),
    val monthlyTrend: List<ChartPoint> = emptyList(),
    val highestCategories: List<ChartPoint> = emptyList()
)

data class RentAnalyticsData(
    val paidRent: Double = 0.0,
    val pendingRent: Double = 0.0,
    val partialPayments: Double = 0.0,
    val overduePayments: Double = 0.0,
    val collectionPercentage: Double = 0.0
)

data class OccupancyAnalyticsData(
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyPercentage: Double = 0.0,
    val roomTypeBreakdown: List<ChartPoint> = emptyList()
)
