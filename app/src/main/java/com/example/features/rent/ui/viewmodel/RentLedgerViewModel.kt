package com.example.features.rent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.domain.model.MonthlyRentStats
import com.example.features.rent.domain.usecase.GenerateMonthRentUseCase
import com.example.features.rent.domain.usecase.GetLedgerUseCase
import com.example.features.rent.domain.usecase.GetMonthPreviewUseCase
import com.example.features.rent.domain.usecase.RecordPaymentTransactionUseCase
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class RentLedgerFilters(
    val searchQuery: String = "",
    val selectedStatus: String = "All",
    val selectedMonth: String = RentBillingEngine.formatCanonicalBillingMonth(Date())
)

sealed class RentLedgerUiState {
    object Loading : RentLedgerUiState()
    data class Success(
        val allPayments: List<RentPaymentEntity>,
        val filteredPayments: List<RentPaymentEntity>,
        val filters: RentLedgerFilters,
        val availableMonths: List<String>,
        val monthlyStats: MonthlyRentStats,
        val isSelectedMonthGenerated: Boolean,
        val previewData: MonthGenerationPreview? = null,
        val isGenerating: Boolean = false,
        val feedbackMessage: String? = null
    ) : RentLedgerUiState()
    data class Error(val message: String) : RentLedgerUiState()
}

@HiltViewModel
class RentLedgerViewModel @Inject constructor(
    private val getLedgerUseCase: GetLedgerUseCase,
    private val getMonthPreviewUseCase: GetMonthPreviewUseCase,
    private val generateMonthRentUseCase: GenerateMonthRentUseCase,
    private val recordPaymentTransactionUseCase: RecordPaymentTransactionUseCase,
    private val currentBillingMonthManager: CurrentBillingMonthManager
) : ViewModel() {

    private val currentMonthCanonical = currentBillingMonthManager.getCurrentBillingMonth()

    private val _filters = MutableStateFlow(RentLedgerFilters(selectedMonth = currentMonthCanonical))
    private val _previewData = MutableStateFlow<MonthGenerationPreview?>(null)
    private val _isGenerating = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    init {
        // Keep selected month in sync with global active billing month
        viewModelScope.launch {
            currentBillingMonthManager.currentBillingMonthFlow.collect { globalMonth ->
                if (_filters.value.selectedMonth != "All Months" && _filters.value.selectedMonth != globalMonth) {
                    _filters.value = _filters.value.copy(selectedMonth = globalMonth)
                }
            }
        }
    }

    val uiState: StateFlow<RentLedgerUiState> = combine(
        getLedgerUseCase(),
        _filters,
        _previewData,
        _isGenerating,
        _feedbackMessage
    ) { allPayments, filters, preview, generating, feedback ->
        // Generate chronological list of available months (including current and next 2 months)
        val existingMonths = allPayments.map { it.billingMonth }.distinct()
        val defaultMonths = generateDefaultMonthList()
        val combinedMonths = (listOf("All Months") + (existingMonths + defaultMonths).distinct().sortedWith { m1, m2 ->
            val p1 = RentBillingEngine.parseBillingMonth(m1)
            val p2 = RentBillingEngine.parseBillingMonth(m2)
            (p2.year * 100 + p2.month1Based).compareTo(p1.year * 100 + p1.month1Based)
        })

        // Check if selected month has any generated active records
        val isSelectedMonthGenerated = if (filters.selectedMonth == "All Months") {
            allPayments.any { !it.deleted }
        } else {
            allPayments.any { !it.deleted && it.billingMonth.trim().equals(filters.selectedMonth.trim(), ignoreCase = true) }
        }

        // Apply filters
        var filtered = allPayments.filter { !it.deleted }

        if (filters.selectedMonth != "All Months") {
            filtered = filtered.filter { it.billingMonth.trim().equals(filters.selectedMonth.trim(), ignoreCase = true) }
        }

        if (filters.searchQuery.isNotBlank()) {
            val q = filters.searchQuery.trim()
            filtered = filtered.filter {
                it.tenantName.contains(q, ignoreCase = true) ||
                it.roomNumber.contains(q, ignoreCase = true)
            }
        }

        if (filters.selectedStatus != "All") {
            filtered = when (filters.selectedStatus) {
                "Paid" -> filtered.filter { it.status.equals("Paid", true) || it.status.equals("Overpaid", true) }
                "Partial", "Partially Paid" -> filtered.filter { it.status.equals("Partially Paid", true) || it.status.equals("Partial", true) }
                "Pending" -> filtered.filter { it.status.equals("Pending", true) }
                "Overdue" -> filtered.filter { it.status.equals("Overdue", true) }
                else -> filtered.filter { it.status.equals(filters.selectedStatus, true) }
            }
        }

        val stats = RentBillingEngine.computeMonthlyRentStats(
            billingMonth = filters.selectedMonth,
            payments = if (filters.selectedMonth == "All Months") allPayments else allPayments.filter { it.billingMonth.trim().equals(filters.selectedMonth.trim(), ignoreCase = true) }
        )

        RentLedgerUiState.Success(
            allPayments = allPayments,
            filteredPayments = filtered,
            filters = filters,
            availableMonths = combinedMonths,
            monthlyStats = stats,
            isSelectedMonthGenerated = isSelectedMonthGenerated,
            previewData = preview,
            isGenerating = generating,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RentLedgerUiState.Loading
    )

    private fun generateDefaultMonthList(): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        // Past 3 months, current, next 2 months
        cal.add(Calendar.MONTH, -3)
        for (i in 0..6) {
            list.add(RentBillingEngine.formatCanonicalBillingMonth(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return list
    }

    fun updateSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun updateStatusFilter(status: String) {
        _filters.value = _filters.value.copy(selectedStatus = status)
    }

    fun updateMonthFilter(month: String) {
        _filters.value = _filters.value.copy(selectedMonth = month)
        if (month != "All Months") {
            currentBillingMonthManager.setCurrentBillingMonth(month)
        }
    }

    fun requestMonthPreview(targetMonth: String? = null) {
        val monthToPreview = targetMonth ?: _filters.value.selectedMonth.let { if (it == "All Months") currentBillingMonthManager.getCurrentBillingMonth() else it }
        viewModelScope.launch {
            try {
                _isGenerating.value = true
                val preview = getMonthPreviewUseCase(monthToPreview)
                _previewData.value = preview
            } catch (e: Exception) {
                _feedbackMessage.value = "Error preparing preview: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun dismissPreview() {
        _previewData.value = null
    }

    fun confirmAndGenerateMonth(targetMonth: String, dueDate: String = "", backupPreviousMonth: Boolean = true) {
        viewModelScope.launch {
            try {
                _isGenerating.value = true
                val result = generateMonthRentUseCase(targetMonth, dueDate, backupPreviousMonth)
                _previewData.value = null
                currentBillingMonthManager.setCurrentBillingMonth(result.billingMonth)
                _filters.value = _filters.value.copy(selectedMonth = result.billingMonth)
                _feedbackMessage.value = "${result.billingMonth} rent generated successfully."
            } catch (e: Exception) {
                _feedbackMessage.value = "Failed to generate rent: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun recordQuickPayment(paymentId: Int, amount: Double, mode: String = "UPI", ref: String? = null) {
        viewModelScope.launch {
            try {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())
                recordPaymentTransactionUseCase(
                    paymentId = paymentId,
                    paidAmountDelta = amount,
                    paymentDate = today,
                    paymentMode = mode,
                    transactionReference = ref,
                    remarks = "Payment recorded via Rent Ledger"
                )
                _feedbackMessage.value = "Payment recorded successfully."
            } catch (e: Exception) {
                _feedbackMessage.value = "Error recording payment: ${e.message}"
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}
