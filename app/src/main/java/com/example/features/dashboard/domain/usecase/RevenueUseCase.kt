package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class RevenueStats(
    val monthlyRevenue: Double = 0.0,
    val pendingRent: Double = 0.0
)

class RevenueUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<RevenueStats> {
        return repository.getPaymentsFlow().map { payments ->
            val paid = payments.sumOf { it.amountPaid }
            val pending = payments.filter { it.status == "Pending" || it.status == "Partial" || it.status == "Overdue" }.sumOf { it.amount - it.amountPaid }
            RevenueStats(monthlyRevenue = paid, pendingRent = pending)
        }
    }
}
