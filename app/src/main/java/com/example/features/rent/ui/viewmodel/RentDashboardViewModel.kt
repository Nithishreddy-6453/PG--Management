package com.example.features.rent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.domain.repository.RentSummary
import com.example.features.rent.domain.usecase.GenerateMonthRentUseCase
import com.example.features.rent.domain.usecase.GetMonthPreviewUseCase
import com.example.features.rent.domain.usecase.GetRentSummaryUseCase
import com.example.features.rent.domain.util.RentBillingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

sealed class RentDashboardUiState {
    object Loading : RentDashboardUiState()
    data class Success(val summary: RentSummary) : RentDashboardUiState()
    data class Error(val message: String) : RentDashboardUiState()
}

@HiltViewModel
class RentDashboardViewModel @Inject constructor(
    private val getRentSummaryUseCase: GetRentSummaryUseCase,
    private val getMonthPreviewUseCase: GetMonthPreviewUseCase,
    private val generateMonthRentUseCase: GenerateMonthRentUseCase
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

    private val _previewData = MutableStateFlow<MonthGenerationPreview?>(null)
    val previewData = _previewData.asStateFlow()

    fun requestMonthPreview(targetMonth: String? = null) {
        val monthToPreview = targetMonth ?: RentBillingEngine.formatCanonicalBillingMonth(Date())
        viewModelScope.launch {
            try {
                val preview = getMonthPreviewUseCase(monthToPreview)
                _previewData.value = preview
            } catch (e: Exception) {
                _invoiceGenerationState.value = "Error preparing preview: ${e.message}"
            }
        }
    }

    fun dismissPreview() {
        _previewData.value = null
    }

    fun confirmAndGenerateMonth(targetMonth: String, dueDate: String = "", backupPreviousMonth: Boolean = true) {
        viewModelScope.launch {
            try {
                _invoiceGenerationState.value = "Generating..."
                val result = generateMonthRentUseCase(targetMonth, dueDate, backupPreviousMonth)
                _previewData.value = null
                _invoiceGenerationState.value = "${result.billingMonth} rent generated successfully."
            } catch (e: Exception) {
                _invoiceGenerationState.value = "Error: ${e.message}"
            }
        }
    }

    fun generateInvoices() {
        requestMonthPreview()
    }
    
    fun clearInvoiceGenerationState() {
        _invoiceGenerationState.value = null
    }
}
