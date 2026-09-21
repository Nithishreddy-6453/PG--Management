package com.example.features.rent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.usecase.GetLedgerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RentLedgerFilters(
    val searchQuery: String = "",
    val selectedStatus: String = "All",
    val selectedMonth: String = "All"
)

sealed class RentLedgerUiState {
    object Loading : RentLedgerUiState()
    data class Success(
        val payments: List<RentPaymentEntity>,
        val filters: RentLedgerFilters,
        val availableMonths: List<String>
    ) : RentLedgerUiState()
    data class Error(val message: String) : RentLedgerUiState()
    object Empty : RentLedgerUiState()
}

@HiltViewModel
class RentLedgerViewModel @Inject constructor(
    private val getLedgerUseCase: GetLedgerUseCase
) : ViewModel() {

    private val _filters = MutableStateFlow(RentLedgerFilters())

    val uiState: StateFlow<RentLedgerUiState> = combine(
        getLedgerUseCase(),
        _filters
    ) { allPayments, filters ->
        if (allPayments.isEmpty()) return@combine RentLedgerUiState.Empty

        val months = listOf("All") + allPayments.map { it.billingMonth }.distinct().sortedDescending()
        
        var filtered = allPayments
        
        if (filters.searchQuery.isNotBlank()) {
            filtered = filtered.filter { 
                it.tenantName.contains(filters.searchQuery, ignoreCase = true) ||
                it.roomNumber.contains(filters.searchQuery, ignoreCase = true)
            }
        }
        
        if (filters.selectedStatus != "All") {
            filtered = filtered.filter { it.status.equals(filters.selectedStatus, ignoreCase = true) }
        }
        
        if (filters.selectedMonth != "All") {
            filtered = filtered.filter { it.billingMonth == filters.selectedMonth }
        }
        
        RentLedgerUiState.Success(
            payments = filtered,
            filters = filters,
            availableMonths = months
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RentLedgerUiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun updateStatusFilter(status: String) {
        _filters.value = _filters.value.copy(selectedStatus = status)
    }

    fun updateMonthFilter(month: String) {
        _filters.value = _filters.value.copy(selectedMonth = month)
    }
}
