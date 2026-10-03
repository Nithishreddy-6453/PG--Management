package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import kotlin.math.roundToLong

data class ProfitStats(
    val netProfit: Double = 0.0
)

class ProfitUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<ProfitStats> {
        return combine(
            repository.getPaymentsForCurrentBillingMonthFlow(),
            repository.getExpensesForCurrentBillingMonthFlow()
        ) { payments, expenses ->
            val revenue = payments.filter { !it.deleted }.sumOf { it.amountPaid }
            val totalExpenses = expenses.filter { !it.deleted }.sumOf { it.amount }
            val profit = revenue - totalExpenses
            ProfitStats(netProfit = (profit * 100.0).roundToLong() / 100.0)
        }
    }
}
