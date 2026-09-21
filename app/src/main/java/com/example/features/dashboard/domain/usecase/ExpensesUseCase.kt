package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ExpensesStats(
    val totalExpenses: Double = 0.0
)

class ExpensesUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<ExpensesStats> {
        return repository.getExpensesFlow().map { expenses ->
            ExpensesStats(totalExpenses = expenses.sumOf { it.amount })
        }
    }
}
