package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ProfitStats(
    val netProfit: Double = 0.0
)

class ProfitUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<ProfitStats> {
        return combine(repository.getPaymentsFlow(), repository.getExpensesFlow()) { payments, expenses ->
            val revenue = payments.filter { it.status == "Paid" }.sumOf { it.amount }
            val totalExpenses = expenses.sumOf { it.amount }
            ProfitStats(netProfit = revenue - totalExpenses)
        }
    }
}
