package com.example.features.expenses.domain.usecase

import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AddExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense) {
        repository.insertExpense(expense)
    }
}

class UpdateExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense) {
        repository.updateExpense(expense)
    }
}

class DeleteExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(id: Int) {
        repository.deleteExpense(id)
    }
}

class GetExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Expense>> {
        return repository.getAllExpenses()
    }
}

class GetExpenseDetailsUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(id: Int): Expense? {
        return repository.getExpenseById(id)
    }
}

class SearchExpensesUseCase @Inject constructor() {
    operator fun invoke(expenses: List<Expense>, query: String): List<Expense> {
        if (query.isBlank()) return expenses
        return expenses.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true) ||
            it.notes.contains(query, ignoreCase = true)
        }
    }
}

class FilterExpensesUseCase @Inject constructor() {
    operator fun invoke(
        expenses: List<Expense>,
        category: String?,
        month: String?, // e.g. "2026-07"
        paymentMethod: String?,
        sortBy: SortType = SortType.DATE_DESC
    ): List<Expense> {
        var result = expenses

        if (!category.isNullOrBlank() && category != "All") {
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (!month.isNullOrBlank()) {
            result = result.filter { it.date.startsWith(month) }
        }

        if (!paymentMethod.isNullOrBlank() && paymentMethod != "All") {
            result = result.filter { it.paymentMethod.equals(paymentMethod, ignoreCase = true) }
        }

        return when (sortBy) {
            SortType.DATE_DESC -> result.sortedByDescending { it.date }
            SortType.DATE_ASC -> result.sortedBy { it.date }
            SortType.AMOUNT_DESC -> result.sortedByDescending { it.amount }
            SortType.AMOUNT_ASC -> result.sortedBy { it.amount }
        }
    }
}

enum class SortType {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

class ValidateExpenseUseCase @Inject constructor() {
    operator fun invoke(
        title: String,
        category: String,
        amount: String,
        date: String
    ): ValidationResult {
        if (title.isBlank()) {
            return ValidationResult.Error("Expense title cannot be empty")
        }
        if (category.isBlank()) {
            return ValidationResult.Error("Please select a category")
        }
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null) {
            return ValidationResult.Error("Please enter a valid amount")
        }
        if (parsedAmount <= 0) {
            return ValidationResult.Error("Amount must be greater than zero")
        }
        if (date.isBlank() || !date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return ValidationResult.Error("Please select a valid date (YYYY-MM-DD)")
        }
        return ValidationResult.Success
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
