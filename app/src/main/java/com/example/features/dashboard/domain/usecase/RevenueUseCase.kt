package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToLong

data class RevenueStats(
    val monthlyRevenue: Double = 0.0,
    val pendingRent: Double = 0.0,
    val totalExpected: Double = 0.0,
    val billingMonth: String = "",
    val previousDuesReceived: Double = 0.0,
    val advanceCredit: Double = 0.0,
    val rentStillToCollect: Double = 0.0
)

class RevenueUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<RevenueStats> {
        return repository.getPaymentsForCurrentBillingMonthFlow().map { payments ->
            val active = payments.filter { !it.deleted }
            val paid = active.sumOf { it.amountPaid }
            val pending = active.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) }
            val totalExpected = active.sumOf { it.amount }
            val month = active.firstOrNull()?.billingMonth.orEmpty()
            RevenueStats(
                monthlyRevenue = (paid * 100.0).roundToLong() / 100.0,
                pendingRent = (pending * 100.0).roundToLong() / 100.0,
                totalExpected = (totalExpected * 100.0).roundToLong() / 100.0,
                billingMonth = month
            )
        }
    }
}
