package com.example.features.expenses.domain.model

data class Expense(
    val id: Int = 0,
    val title: String,
    val category: String,
    val amount: Double,
    val date: String,
    val paymentMethod: String,
    val vendor: String?,
    val notes: String
)
