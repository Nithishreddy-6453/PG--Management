package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToLong

data class ExpensesStats(
    val totalExpenses: Double = 0.0
)

class ExpensesUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<ExpensesStats> {
        return repository.getExpensesForCurrentBillingMonthFlow().map { expenses ->
            val total = expenses.filter { !it.deleted }.sumOf { it.amount }
            ExpensesStats(totalExpenses = (total * 100.0).roundToLong() / 100.0)
        }
    }
}
