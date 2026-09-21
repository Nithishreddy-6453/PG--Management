package com.example.features.dashboard.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class DashboardSummary(
    val occupancy: OccupancyStats = OccupancyStats(),
    val revenue: RevenueStats = RevenueStats(),
    val expenses: ExpensesStats = ExpensesStats(),
    val profit: ProfitStats = ProfitStats(),
    val recentActivities: List<RecentActivity> = emptyList()
)

class DashboardSummaryUseCase @Inject constructor(
    private val occupancyUseCase: OccupancyUseCase,
    private val revenueUseCase: RevenueUseCase,
    private val expensesUseCase: ExpensesUseCase,
    private val profitUseCase: ProfitUseCase,
    private val recentActivityUseCase: RecentActivityUseCase
) {
    operator fun invoke(): Flow<DashboardSummary> {
        return combine(
            occupancyUseCase(),
            revenueUseCase(),
            expensesUseCase(),
            profitUseCase(),
            recentActivityUseCase()
        ) { occupancy, revenue, expenses, profit, activities ->
            DashboardSummary(
                occupancy = occupancy,
                revenue = revenue,
                expenses = expenses,
                profit = profit,
                recentActivities = activities
            )
        }
    }
}
