package com.example.features.dashboard.domain.usecase

import androidx.compose.runtime.Immutable
import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
import com.example.data.database.ExpenseEntity
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.reports.domain.engine.ReportCalculationEngine
import com.example.features.reports.domain.model.AttentionItem
import com.example.features.reports.domain.model.OwnerOverviewReport
import com.example.features.reports.domain.model.RentMetrics
import com.example.features.reports.domain.model.ReportPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@Immutable
data class DashboardSummary(
    val occupancy: OccupancyStats = OccupancyStats(),
    val revenue: RevenueStats = RevenueStats(),
    val expenses: ExpensesStats = ExpensesStats(),
    val profit: ProfitStats = ProfitStats(),
    val recentActivities: List<RecentActivity> = emptyList(),
    val upcomingVacancies: List<UpcomingVacancyItem> = emptyList(),
    val whatNeedsAttention: List<AttentionItem> = emptyList(),
    val ownerOverview: OwnerOverviewReport? = null,
    val rentMetrics: RentMetrics? = null
)

class DashboardSummaryUseCase @Inject constructor(
    private val repository: DashboardRepository,
    private val recentActivityUseCase: RecentActivityUseCase
) {
    @OptIn(FlowPreview::class)
    operator fun invoke(): Flow<DashboardSummary> {
        return combine(
            repository.getCurrentPropertyFlow(),
            repository.getRoomsFlow(),
            repository.getBedsFlow(),
            repository.getTenantsFlow(),
            repository.getAssignmentsFlow(),
            repository.getPaymentsFlow(),
            repository.getExpensesFlow(),
            repository.getCurrentBillingMonthFlow()
        ) { args: Array<Any?> ->
            val property = args[0] as? PropertyEntity
            @Suppress("UNCHECKED_CAST")
            val rooms = args[1] as List<RoomEntity>
            @Suppress("UNCHECKED_CAST")
            val beds = args[2] as List<BedEntity>
            @Suppress("UNCHECKED_CAST")
            val tenants = args[3] as List<TenantEntity>
            @Suppress("UNCHECKED_CAST")
            val assignments = args[4] as List<BedAssignmentEntity>
            @Suppress("UNCHECKED_CAST")
            val payments = args[5] as List<RentPaymentEntity>
            @Suppress("UNCHECKED_CAST")
            val expenses = args[6] as List<ExpenseEntity>
            val billingMonth = args[7] as String

            val report = ReportCalculationEngine.calculateReport(
                property = property,
                period = ReportPeriod.Monthly(billingMonth),
                rooms = rooms,
                beds = beds,
                tenants = tenants,
                assignments = assignments,
                payments = payments,
                expenses = expenses
            )

            val activities = recentActivityUseCase.computeActivities(tenants, payments, expenses)

            val occ = OccupancyStats(
                totalRooms = report.bedOccupancy.totalRooms,
                occupiedRooms = report.bedOccupancy.occupiedBeds,
                vacantRooms = report.bedOccupancy.availableBeds,
                occupancyPercentage = report.overview.occupancyPercentage,
                totalBeds = report.overview.totalPgCapacity,
                occupiedBeds = report.overview.activeTenantsCount,
                vacantBeds = report.overview.vacanciesCount,
                bedOccupancyPercentage = report.overview.occupancyPercentage,
                activeTenantsCount = report.overview.activeTenantsCount,
                totalPgCapacity = report.overview.totalPgCapacity,
                homeOccupancyPercentage = report.overview.occupancyPercentage,
                vacanciesCount = report.overview.vacanciesCount,
                tenantsCapacityRatioText = report.overview.tenantsCapacityRatioText,
                capacityDisplayText = "${report.overview.activeTenantsCount} Tenants / ${report.overview.totalPgCapacity} Capacity"
            )

            val rev = RevenueStats(
                monthlyRevenue = report.overview.rentReceivedThisMonth,
                pendingRent = report.overview.rentStillToCollect,
                totalExpected = report.overview.rentDue,
                billingMonth = report.overview.reportMonth,
                previousDuesReceived = report.overview.previousDuesReceived,
                advanceCredit = report.overview.advanceCredit,
                rentStillToCollect = report.overview.rentStillToCollect
            )

            val exp = ExpensesStats(totalExpenses = report.overview.expenses)
            val prof = ProfitStats(netProfit = report.overview.moneyLeftAfterExpenses)

            val vacancies = report.vacancyReport.upcomingVacancies.map { v ->
                UpcomingVacancyItem(
                    tenantId = v.tenantId,
                    tenantName = v.tenantName,
                    roomNumber = v.roomNumber,
                    leavingDate = v.leavingDate,
                    daysRemaining = v.daysRemaining
                )
            }

            DashboardSummary(
                occupancy = occ,
                revenue = rev,
                expenses = exp,
                profit = prof,
                recentActivities = activities,
                upcomingVacancies = vacancies,
                whatNeedsAttention = report.attentionItems,
                ownerOverview = report.overview,
                rentMetrics = report.rentMetrics
            )
        }
        .debounce(50L)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    }
}
