package com.example.features.expenses.domain.repository

import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.model.ExpensePayment
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: Int): Expense?
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(id: Int)
    suspend fun recordPayment(expenseId: Int, payment: ExpensePayment)
    suspend fun generateRecurringExpenses(expense: Expense, monthsCount: Int = 3)
}
