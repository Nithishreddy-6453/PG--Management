package com.example.features.reports.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ExpenseEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.reports.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

sealed interface ReportsUiState {
    object Loading : ReportsUiState
    data class Success(
        val metrics: FinancialMetrics,
        val revenueData: RevenueReportData,
        val expenseData: ExpenseReportData,
        val rentData: RentAnalyticsData,
        val occupancyData: OccupancyAnalyticsData,
        val filterMonth: String,
        val filterYear: String
    ) : ReportsUiState
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: DashboardRepository
) : ViewModel() {

    private val _filterMonth = MutableStateFlow("All")
    private val _filterYear = MutableStateFlow("All")

    fun setFilterMonth(month: String) {
        _filterMonth.value = month
    }

    fun setFilterYear(year: String) {
        _filterYear.value = year
    }

    private val filterState = combine(_filterMonth, _filterYear) { month, year ->
        Pair(month, year)
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        repository.getRoomsFlow(),
        repository.getTenantsFlow(),
        repository.getPaymentsFlow(),
        repository.getExpensesFlow(),
        filterState
    ) { rooms, tenants, payments, expenses, filters ->
        val month = filters.first
        val year = filters.second
        
        // --- 1. Filter Data (Simple implementation, in a real app would be robust) ---
        val filteredPayments = filterPayments(payments, month, year)
        val filteredExpenses = filterExpenses(expenses, month, year)

        // --- 2. Calculate Financial Metrics ---
        val totalRevenue = filteredPayments.filter { it.status == "Paid" }.sumOf { it.amount }
        val outstandingRent = filteredPayments.filter { it.status == "Pending" || it.status == "Overdue" }.sumOf { it.amount }
        val totalExpenses = filteredExpenses.sumOf { it.amount }
        val netProfit = totalRevenue - totalExpenses
        
        val expectedRevenue = totalRevenue + outstandingRent
        val collectionRate = if (expectedRevenue > 0) (totalRevenue / expectedRevenue) else 0.0

        val occupiedRoomNumbers = tenants.map { it.roomNumber }.distinct()
        val totalRoomsCount = rooms.size
        val occupiedRoomsCount = occupiedRoomNumbers.size
        val vacantRoomsCount = (totalRoomsCount - occupiedRoomsCount).coerceAtLeast(0)
        val occupancyRate = if (totalRoomsCount > 0) (occupiedRoomsCount.toDouble() / totalRoomsCount) else 0.0
        val vacancyRate = 1.0 - occupancyRate

        val metrics = FinancialMetrics(
            totalRevenue = totalRevenue,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            outstandingRent = outstandingRent,
            collectionRate = collectionRate,
            totalRooms = totalRoomsCount,
            occupiedRooms = occupiedRoomsCount,
            vacantRooms = vacantRoomsCount,
            occupancyRate = occupancyRate,
            vacancyRate = vacancyRate
        )

        // --- 3. Calculate Revenue Report Data ---
        // Group by billing month for trend
        val monthlyRevMap = filteredPayments.filter { it.status == "Paid" }
            .groupBy { it.billingMonth }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        
        val monthlyRevPoints = monthlyRevMap.map { ChartPoint(it.key, it.value) }.sortedBy { it.label } // Simple sort

        val revenueData = RevenueReportData(
            monthlyRevenue = monthlyRevPoints
        )

        // --- 4. Calculate Expense Report Data ---
        val categoryBreakdownMap = filteredExpenses
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val categoryPoints = categoryBreakdownMap.map { ChartPoint(it.key, it.value) }.sortedByDescending { it.value }

        val expenseData = ExpenseReportData(
            categoryBreakdown = categoryPoints,
            highestCategories = categoryPoints.take(3)
        )

        // --- 5. Calculate Rent Analytics Data ---
        val paidRent = totalRevenue
        val pendingRent = outstandingRent
        
        val rentData = RentAnalyticsData(
            paidRent = paidRent,
            pendingRent = pendingRent,
            collectionPercentage = collectionRate
        )

        // --- 6. Calculate Occupancy Analytics Data ---
        val occupancyData = OccupancyAnalyticsData(
            totalRooms = totalRoomsCount,
            occupiedRooms = occupiedRoomsCount,
            vacantRooms = vacantRoomsCount,
            occupancyPercentage = occupancyRate
        )

        ReportsUiState.Success(
            metrics = metrics,
            revenueData = revenueData,
            expenseData = expenseData,
            rentData = rentData,
            occupancyData = occupancyData,
            filterMonth = month,
            filterYear = year
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState.Loading
    )

    private fun filterPayments(payments: List<RentPaymentEntity>, month: String, year: String): List<RentPaymentEntity> {
        // Mock filtering logic for now. Actual implementation would parse dates.
        return payments
    }

    private fun filterExpenses(expenses: List<ExpenseEntity>, month: String, year: String): List<ExpenseEntity> {
         // Mock filtering logic for now.
         return expenses
    }
}
