package com.example.features.rent.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.usecase.RecordPaymentUseCase
import com.example.features.rent.domain.usecase.UpdatePaymentUseCase
import com.example.features.rent.domain.repository.RentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = false,
    val paymentId: Int? = null,
    val tenantId: Int = 0,
    val tenantName: String = "",
    val roomNumber: String = "",
    val billingMonth: String = "",
    val expectedAmount: Double = 0.0,
    val amountPaid: String = "",
    val dueDate: String = "",
    val paymentDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
    val paymentMode: String = "UPI",
    val transactionReference: String = "",
    val remarks: String = "",
    val error: String? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val recordPaymentUseCase: RecordPaymentUseCase,
    private val updatePaymentUseCase: UpdatePaymentUseCase,
    private val rentRepository: RentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val paymentId: Int? = savedStateHandle.get<String>("paymentId")?.toIntOrNull()
        ?: savedStateHandle.get<Int>("paymentId")

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _actionEvent = MutableSharedFlow<Boolean>()
    val actionEvent = _actionEvent.asSharedFlow()

    init {
        loadPaymentDetails()
    }

    private fun loadPaymentDetails() {
        if (paymentId != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val payment = rentRepository.getLedger().firstOrNull()?.find { it.id == paymentId }
                if (payment != null) {
                    _uiState.value = PaymentUiState(
                        paymentId = payment.id,
                        tenantId = payment.tenantId,
                        tenantName = payment.tenantName,
                        roomNumber = payment.roomNumber,
                        billingMonth = payment.billingMonth,
                        expectedAmount = payment.amount,
                        amountPaid = payment.amountPaid.toString(),
                        dueDate = payment.dueDate,
                        paymentDate = payment.paymentDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                        paymentMode = payment.paymentMode ?: "UPI",
                        transactionReference = payment.transactionReference ?: "",
                        remarks = payment.remarks ?: ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Payment not found")
                }
            }
        }
    }

    fun onAmountPaidChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amountPaid = amount, error = null)
    }

    fun onPaymentDateChanged(date: String) {
        _uiState.value = _uiState.value.copy(paymentDate = date, error = null)
    }

    fun onPaymentModeChanged(mode: String) {
        _uiState.value = _uiState.value.copy(paymentMode = mode, error = null)
    }

    fun onTransactionReferenceChanged(ref: String) {
        _uiState.value = _uiState.value.copy(transactionReference = ref, error = null)
    }

    fun onRemarksChanged(remarks: String) {
        _uiState.value = _uiState.value.copy(remarks = remarks, error = null)
    }

    fun savePayment() {
        val state = _uiState.value
        val amountPaidDouble = state.amountPaid.toDoubleOrNull()
        
        if (amountPaidDouble == null || amountPaidDouble < 0) {
            _uiState.value = state.copy(error = "Invalid amount paid")
            return
        }

        if (amountPaidDouble > state.expectedAmount) {
            _uiState.value = state.copy(error = "Amount paid cannot exceed expected amount")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoading = true)
                val updatedPayment = RentPaymentEntity(
                    id = state.paymentId ?: 0,
                    tenantId = state.tenantId,
                    tenantName = state.tenantName,
                    roomNumber = state.roomNumber,
                    billingMonth = state.billingMonth,
                    amount = state.expectedAmount,
                    amountPaid = amountPaidDouble,
                    dueDate = state.dueDate,
                    paymentDate = state.paymentDate,
                    paymentMode = state.paymentMode,
                    transactionReference = state.transactionReference.takeIf { it.isNotBlank() },
                    remarks = state.remarks.takeIf { it.isNotBlank() },
                    status = "Pending" // Will be recalculated in use case
                )
                
                if (state.paymentId != null) {
                    updatePaymentUseCase(updatedPayment)
                } else {
                    recordPaymentUseCase(updatedPayment)
                }
                _actionEvent.emit(true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}
