package com.example.features.rent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rent.domain.repository.RentSummary
import com.example.features.rent.domain.usecase.GenerateMonthlyInvoicesUseCase
import com.example.features.rent.domain.usecase.GetRentSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RentDashboardUiState {
    object Loading : RentDashboardUiState()
    data class Success(val summary: RentSummary) : RentDashboardUiState()
    data class Error(val message: String) : RentDashboardUiState()
}

@HiltViewModel
class RentDashboardViewModel @Inject constructor(
    private val getRentSummaryUseCase: GetRentSummaryUseCase,
    private val generateMonthlyInvoicesUseCase: GenerateMonthlyInvoicesUseCase
) : ViewModel() {

    val uiState: StateFlow<RentDashboardUiState> = getRentSummaryUseCase()
        .map { summary -> RentDashboardUiState.Success(summary) as RentDashboardUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RentDashboardUiState.Loading
        )

    private val _invoiceGenerationState = MutableStateFlow<String?>(null)
    val invoiceGenerationState = _invoiceGenerationState.asStateFlow()

    fun generateInvoices() {
        viewModelScope.launch {
            try {
                _invoiceGenerationState.value = "Generating..."
                val currentTime = System.currentTimeMillis()
                val dueDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(currentTime + 5L * 24 * 60 * 60 * 1000))
                val billingMonthStr = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(java.util.Date(currentTime))
                generateMonthlyInvoicesUseCase(dueDateStr, billingMonthStr)
                _invoiceGenerationState.value = "Success"
            } catch (e: Exception) {
                _invoiceGenerationState.value = "Error: ${e.message}"
            }
        }
    }
    
    fun clearInvoiceGenerationState() {
        _invoiceGenerationState.value = null
    }
}
