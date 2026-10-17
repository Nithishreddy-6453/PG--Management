package com.example.features.expenses.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.PropertyDao
import com.example.features.expenses.data.ExpenseReceiptManager
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.model.ExpenseAttachment
import com.example.features.expenses.domain.model.ExpensePayment
import com.example.features.expenses.domain.repository.ExpenseRepository
import com.example.features.expenses.domain.usecase.*
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.RentBillingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ==========================================
// 1. EXPENSE LIST VIEWMODEL
// ==========================================

enum class ExpenseTimeRange {
    THIS_MONTH,
    LAST_3M,
    ALL_TIME
}

data class ExpenseListUiState(
    val isLoading: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val selectedStatus: String = "All", // "All", "Paid", "Partially Paid", "Unpaid"
    val selectedPaymentMethod: String = "All",
    val selectedBillingMonth: String = "", // e.g. "October 2026"
    val timeRange: ExpenseTimeRange = ExpenseTimeRange.THIS_MONTH,
    val propertyName: String = "",
    val sortBy: SortType = SortType.DATE_DESC,
    val error: String? = null,
    
    // Financial Summary for Selected Range
    val totalExpenses: Double = 0.0,
    val paidExpenses: Double = 0.0,
    val outstandingExpenses: Double = 0.0,
    val expenseCount: Int = 0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    
    // Tab selection: Expenses vs Recurring Templates
    val showRecurringTab: Boolean = false
)

sealed class ExpenseListUiEvent {
    data class SearchChanged(val query: String) : ExpenseListUiEvent()
    data class CategoryFilterChanged(val category: String) : ExpenseListUiEvent()
    data class StatusFilterChanged(val status: String) : ExpenseListUiEvent()
    data class MonthFilterChanged(val month: String) : ExpenseListUiEvent()
    data class PaymentMethodFilterChanged(val method: String) : ExpenseListUiEvent()
    data class TimeRangeChanged(val timeRange: ExpenseTimeRange) : ExpenseListUiEvent()
    data class SortOrderChanged(val sortType: SortType) : ExpenseListUiEvent()
    data class TabChanged(val showRecurring: Boolean) : ExpenseListUiEvent()
    object NextMonth : ExpenseListUiEvent()
    object PreviousMonth : ExpenseListUiEvent()
    object CurrentMonth : ExpenseListUiEvent()
    object Retry : ExpenseListUiEvent()
}

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val getExpensesUseCase: GetExpensesUseCase,
    private val searchExpensesUseCase: SearchExpensesUseCase,
    private val filterExpensesUseCase: FilterExpensesUseCase,
    private val currentPropertyManager: CurrentPropertyManager,
    private val propertyDao: PropertyDao
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseListUiState())
    val state: StateFlow<ExpenseListUiState> = _state.asStateFlow()

    init {
        val currentBillingMonth = RentBillingEngine.formatCanonicalBillingMonth(Date())
        _state.update { it.copy(selectedBillingMonth = currentBillingMonth) }
        loadPropertyName()
        loadExpenses()
    }

    private fun loadPropertyName() {
        viewModelScope.launch {
            try {
                currentPropertyManager.currentPropertyIdFlow.collect { propId ->
                    val effectiveId = if (propId.isNotBlank() && propId != CurrentPropertyManager.DEFAULT_PROPERTY_ID) {
                        propId
                    } else {
                        currentPropertyManager.getCurrentPropertyId()
                    }
                    val prop = if (effectiveId.isNotBlank()) propertyDao.getProperty(effectiveId) else null
                    val name = prop?.propertyName?.takeIf { it.isNotBlank() } ?: ""
                    _state.update { it.copy(propertyName = name) }
                }
            } catch (_: Exception) {}
        }
    }

    fun onEvent(event: ExpenseListUiEvent) {
        when (event) {
            is ExpenseListUiEvent.SearchChanged -> {
                _state.update { it.copy(searchQuery = event.query) }
                applyFilters()
            }
            is ExpenseListUiEvent.CategoryFilterChanged -> {
                _state.update { it.copy(selectedCategory = event.category) }
                applyFilters()
            }
            is ExpenseListUiEvent.StatusFilterChanged -> {
                _state.update { it.copy(selectedStatus = event.status) }
                applyFilters()
            }
            is ExpenseListUiEvent.MonthFilterChanged -> {
                _state.update { it.copy(selectedBillingMonth = event.month, timeRange = ExpenseTimeRange.THIS_MONTH) }
                applyFilters()
            }
            is ExpenseListUiEvent.PaymentMethodFilterChanged -> {
                _state.update { it.copy(selectedPaymentMethod = event.method) }
                applyFilters()
            }
            is ExpenseListUiEvent.TimeRangeChanged -> {
                _state.update { it.copy(timeRange = event.timeRange) }
                applyFilters()
            }
            is ExpenseListUiEvent.SortOrderChanged -> {
                _state.update { it.copy(sortBy = event.sortType) }
                applyFilters()
            }
            is ExpenseListUiEvent.TabChanged -> {
                _state.update { it.copy(showRecurringTab = event.showRecurring) }
                applyFilters()
            }
            ExpenseListUiEvent.NextMonth -> {
                val current = _state.value.selectedBillingMonth
                val next = RentBillingEngine.getNextBillingMonth(current)
                _state.update { it.copy(selectedBillingMonth = next, timeRange = ExpenseTimeRange.THIS_MONTH) }
                applyFilters()
            }
            ExpenseListUiEvent.PreviousMonth -> {
                val current = _state.value.selectedBillingMonth
                val prev = RentBillingEngine.getPreviousBillingMonth(current)
                _state.update { it.copy(selectedBillingMonth = prev, timeRange = ExpenseTimeRange.THIS_MONTH) }
                applyFilters()
            }
            ExpenseListUiEvent.CurrentMonth -> {
                val now = RentBillingEngine.formatCanonicalBillingMonth(Date())
                _state.update { it.copy(selectedBillingMonth = now, timeRange = ExpenseTimeRange.THIS_MONTH) }
                applyFilters()
            }
            ExpenseListUiEvent.Retry -> {
                loadExpenses()
            }
        }
    }

    private fun loadExpenses() {
        _state.update { it.copy(isLoading = true, error = null) }
        getExpensesUseCase()
            .onEach { expensesList ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        expenses = expensesList
                    )
                }
                applyFilters()
            }
            .catch { t ->
                _state.update { it.copy(isLoading = false, error = t.message ?: "Unknown error occurred") }
            }
            .launchIn(viewModelScope)
    }

    private fun applyFilters() {
        val currentList = _state.value.expenses
        val timeRange = _state.value.timeRange
        val billingMonth = _state.value.selectedBillingMonth
        val parsedMonth = RentBillingEngine.parseBillingMonth(billingMonth)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", parsedMonth.year, parsedMonth.month1Based)

        val timeFilteredExpenses = when (timeRange) {
            ExpenseTimeRange.THIS_MONTH -> {
                currentList.filter {
                    it.date.startsWith(monthPrefix) || it.date.contains(parsedMonth.canonicalName, ignoreCase = true)
                }
            }
            ExpenseTimeRange.LAST_3M -> {
                val m1 = billingMonth
                val m2 = RentBillingEngine.getPreviousBillingMonth(m1)
                val m3 = RentBillingEngine.getPreviousBillingMonth(m2)
                val p1 = RentBillingEngine.parseBillingMonth(m1)
                val p2 = RentBillingEngine.parseBillingMonth(m2)
                val p3 = RentBillingEngine.parseBillingMonth(m3)
                val prefixes = listOf(
                    String.format(Locale.US, "%04d-%02d", p1.year, p1.month1Based),
                    String.format(Locale.US, "%04d-%02d", p2.year, p2.month1Based),
                    String.format(Locale.US, "%04d-%02d", p3.year, p3.month1Based)
                )
                val names = listOf(p1.canonicalName, p2.canonicalName, p3.canonicalName)
                currentList.filter { exp ->
                    prefixes.any { exp.date.startsWith(it) } || names.any { exp.date.contains(it, ignoreCase = true) }
                }
            }
            ExpenseTimeRange.ALL_TIME -> currentList
        }

        // Compute Financial Totals for this selected time window
        val totalAmount = timeFilteredExpenses.sumOf { it.amount }
        val paidAmount = timeFilteredExpenses.sumOf { it.paidAmount }
        val outstandingAmount = timeFilteredExpenses.sumOf { it.remainingAmount }
        val count = timeFilteredExpenses.size
        val breakdown = timeFilteredExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val listToFilter = if (_state.value.showRecurringTab) {
            currentList.filter { it.isRecurring }
        } else {
            timeFilteredExpenses
        }

        val filtered = filterExpensesUseCase(
            expenses = listToFilter,
            category = _state.value.selectedCategory,
            month = null, // Already filtered by time window
            paymentMethod = _state.value.selectedPaymentMethod,
            paymentStatus = _state.value.selectedStatus,
            sortBy = _state.value.sortBy
        )

        val searched = searchExpensesUseCase(filtered, _state.value.searchQuery)

        _state.update {
            it.copy(
                filteredExpenses = searched,
                totalExpenses = totalAmount,
                paidExpenses = paidAmount,
                outstandingExpenses = outstandingAmount,
                expenseCount = count,
                categoryBreakdown = breakdown
            )
        }
    }
}


// ==========================================
// 2. EXPENSE DETAILS VIEWMODEL
// ==========================================

data class ExpenseDetailsUiState(
    val isLoading: Boolean = false,
    val expense: Expense? = null,
    val error: String? = null,
    val isRecordingPayment: Boolean = false,
    val isRetryingUpload: Boolean = false
)

sealed class ExpenseDetailsUiEvent {
    object DeleteExpense : ExpenseDetailsUiEvent()
    object RetryReceiptUpload : ExpenseDetailsUiEvent()
    data class RecordPayment(
        val amount: Double,
        val paymentDate: String,
        val paymentMethod: String,
        val reference: String,
        val notes: String
    ) : ExpenseDetailsUiEvent()
}

sealed class ExpenseDetailsUiEffect {
    object NavigateBack : ExpenseDetailsUiEffect()
    data class ShowToast(val message: String) : ExpenseDetailsUiEffect()
}

@HiltViewModel
class ExpenseDetailsViewModel @Inject constructor(
    private val getExpenseDetailsUseCase: GetExpenseDetailsUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val recordExpensePaymentUseCase: RecordExpensePaymentUseCase,
    private val expenseRepository: ExpenseRepository,
    val expenseReceiptManager: ExpenseReceiptManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseDetailsUiState())
    val state: StateFlow<ExpenseDetailsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseDetailsUiEffect>()
    val effects: SharedFlow<ExpenseDetailsUiEffect> = _effects.asSharedFlow()

    private var expenseId: Int = -1

    init {
        expenseId = savedStateHandle.get<Any>("expenseId")?.let { raw ->
            when (raw) {
                is Int -> raw
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull() ?: -1
                else -> -1
            }
        } ?: -1
        if (expenseId != -1) {
            loadExpenseDetails(expenseId)
        } else {
            _state.update { it.copy(error = "Invalid Expense ID") }
        }
    }

    fun loadExpenseDetails(id: Int = expenseId) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val expense = getExpenseDetailsUseCase(id)
                if (expense != null) {
                    _state.update { it.copy(isLoading = false, expense = expense) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Expense not found") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load details") }
            }
        }
    }

    fun onEvent(event: ExpenseDetailsUiEvent) {
        when (event) {
            ExpenseDetailsUiEvent.DeleteExpense -> {
                viewModelScope.launch {
                    try {
                        deleteExpenseUseCase(expenseId)
                        _effects.emit(ExpenseDetailsUiEffect.ShowToast("Expense deleted successfully"))
                        _effects.emit(ExpenseDetailsUiEffect.NavigateBack)
                    } catch (e: Exception) {
                        _effects.emit(ExpenseDetailsUiEffect.ShowToast("Failed to delete expense: ${e.message}"))
                    }
                }
            }
            ExpenseDetailsUiEvent.RetryReceiptUpload -> {
                viewModelScope.launch {
                    try {
                        _state.update { it.copy(isRetryingUpload = true) }
                        val res = expenseRepository.retryReceiptUpload(expenseId)
                        _state.update { it.copy(isRetryingUpload = false) }
                        if (res is PgResult.Success) {
                            _state.update { it.copy(expense = res.data) }
                            _effects.emit(ExpenseDetailsUiEffect.ShowToast("Receipt uploaded successfully to Google Drive"))
                        } else if (res is PgResult.Failure) {
                            _effects.emit(ExpenseDetailsUiEffect.ShowToast("Upload failed: ${res.error.message}"))
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(isRetryingUpload = false) }
                        _effects.emit(ExpenseDetailsUiEffect.ShowToast("Upload retry error: ${e.message}"))
                    }
                }
            }
            is ExpenseDetailsUiEvent.RecordPayment -> {
                viewModelScope.launch {
                    try {
                        _state.update { it.copy(isRecordingPayment = true) }
                        val payment = ExpensePayment(
                            id = UUID.randomUUID().toString(),
                            amount = event.amount,
                            paymentDate = event.paymentDate,
                            paymentMethod = event.paymentMethod,
                            reference = event.reference,
                            notes = event.notes
                        )
                        recordExpensePaymentUseCase(expenseId, payment)
                        loadExpenseDetails(expenseId)
                        _state.update { it.copy(isRecordingPayment = false) }
                        _effects.emit(ExpenseDetailsUiEffect.ShowToast("Payment recorded successfully"))
                    } catch (e: Exception) {
                        _state.update { it.copy(isRecordingPayment = false) }
                        _effects.emit(ExpenseDetailsUiEffect.ShowToast("Failed to record payment: ${e.message}"))
                    }
                }
            }
        }
    }
}


// ==========================================
// 3. ADD EXPENSE VIEWMODEL
// ==========================================

data class AddExpenseUiState(
    val title: String = "",
    val category: String = "Electricity Bill",
    val amount: String = "",
    val date: String = "",
    val propertyName: String = "Sri Lakshmi Residency",
    val room: String = "Select room",
    val vendor: String = "",
    val notes: String = "",
    
    // Payment Status & Details
    val paymentStatus: String = "Paid", // "Paid", "Pending", "Partially Paid"
    val paymentDate: String = "",
    val paymentMethod: String = "UPI",
    val paymentReference: String = "",
    
    // Recurring Options
    val isRecurring: Boolean = false,
    val recurringFrequency: String = "Monthly", // "Monthly" or "Yearly"
    val recurringStartDate: String = "",
    val recurringEndDate: String = "",
    val endType: String = "No end date", // "No end date", "End on date", "End after a number of times"
    val selectedAttachment: ExpenseAttachment? = null,
    val isPreparingAttachment: Boolean = false,
    val receiptUploadError: String? = null,
    
    val isLoading: Boolean = false,
    val validationError: String? = null,
    val isSuccess: Boolean = false
)

sealed class AddExpenseUiEvent {
    data class TitleChanged(val title: String) : AddExpenseUiEvent()
    data class CategoryChanged(val category: String) : AddExpenseUiEvent()
    data class AmountChanged(val amount: String) : AddExpenseUiEvent()
    data class DateChanged(val date: String) : AddExpenseUiEvent()
    data class PropertyChanged(val property: String) : AddExpenseUiEvent()
    data class RoomChanged(val room: String) : AddExpenseUiEvent()
    data class VendorChanged(val vendor: String) : AddExpenseUiEvent()
    data class NotesChanged(val notes: String) : AddExpenseUiEvent()
    data class PaymentStatusChanged(val status: String) : AddExpenseUiEvent()
    data class PaymentDateChanged(val date: String) : AddExpenseUiEvent()
    data class PaymentMethodChanged(val method: String) : AddExpenseUiEvent()
    data class PaymentReferenceChanged(val ref: String) : AddExpenseUiEvent()
    data class RecurringToggled(val isRecurring: Boolean) : AddExpenseUiEvent()
    data class RecurringFrequencyChanged(val freq: String) : AddExpenseUiEvent()
    data class RecurringEndDateChanged(val date: String) : AddExpenseUiEvent()
    data class EndTypeChanged(val endType: String) : AddExpenseUiEvent()
    data class AttachReceiptUri(val uri: Uri, val mimeType: String? = null, val source: String = "UPLOAD") : AddExpenseUiEvent()
    object RemoveReceipt : AddExpenseUiEvent()
    object SaveExpense : AddExpenseUiEvent()
    object ResetSuccess : AddExpenseUiEvent()
}

sealed class AddExpenseUiEffect {
    object NavigateBack : AddExpenseUiEffect()
    data class ShowMessage(val message: String) : AddExpenseUiEffect()
}

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase: AddExpenseUseCase,
    private val validateExpenseUseCase: ValidateExpenseUseCase,
    private val expenseReceiptManager: ExpenseReceiptManager
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseUiState())
    val state: StateFlow<AddExpenseUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AddExpenseUiEffect>()
    val effects: SharedFlow<AddExpenseUiEffect> = _effects.asSharedFlow()

    init {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        _state.update {
            it.copy(
                date = todayStr,
                paymentDate = todayStr,
                recurringStartDate = todayStr
            )
        }
    }

    fun onEvent(event: AddExpenseUiEvent) {
        when (event) {
            is AddExpenseUiEvent.TitleChanged -> _state.update { it.copy(title = event.title) }
            is AddExpenseUiEvent.CategoryChanged -> _state.update { it.copy(category = event.category) }
            is AddExpenseUiEvent.AmountChanged -> _state.update { it.copy(amount = event.amount) }
            is AddExpenseUiEvent.DateChanged -> _state.update { it.copy(date = event.date) }
            is AddExpenseUiEvent.PropertyChanged -> _state.update { it.copy(propertyName = event.property) }
            is AddExpenseUiEvent.RoomChanged -> _state.update { it.copy(room = event.room) }
            is AddExpenseUiEvent.VendorChanged -> _state.update { it.copy(vendor = event.vendor) }
            is AddExpenseUiEvent.NotesChanged -> _state.update { it.copy(notes = event.notes) }
            is AddExpenseUiEvent.PaymentStatusChanged -> _state.update { it.copy(paymentStatus = event.status) }
            is AddExpenseUiEvent.PaymentDateChanged -> _state.update { it.copy(paymentDate = event.date) }
            is AddExpenseUiEvent.PaymentMethodChanged -> _state.update { it.copy(paymentMethod = event.method) }
            is AddExpenseUiEvent.PaymentReferenceChanged -> _state.update { it.copy(paymentReference = event.ref) }
            is AddExpenseUiEvent.RecurringToggled -> _state.update { it.copy(isRecurring = event.isRecurring) }
            is AddExpenseUiEvent.RecurringFrequencyChanged -> _state.update { it.copy(recurringFrequency = event.freq) }
            is AddExpenseUiEvent.RecurringEndDateChanged -> _state.update { it.copy(recurringEndDate = event.date) }
            is AddExpenseUiEvent.EndTypeChanged -> _state.update { it.copy(endType = event.endType) }
            is AddExpenseUiEvent.AttachReceiptUri -> {
                _state.update { it.copy(isPreparingAttachment = true, receiptUploadError = null) }
                viewModelScope.launch {
                    val res = expenseReceiptManager.prepareLocalAttachment(
                        uri = event.uri,
                        category = _state.value.category,
                        mimeTypeInput = event.mimeType,
                        source = event.source
                    )
                    if (res is PgResult.Success) {
                        _state.update { it.copy(selectedAttachment = res.data, isPreparingAttachment = false) }
                    } else if (res is PgResult.Failure) {
                        _state.update { it.copy(isPreparingAttachment = false, receiptUploadError = res.error.message) }
                    }
                }
            }
            AddExpenseUiEvent.RemoveReceipt -> {
                _state.update { it.copy(selectedAttachment = null, receiptUploadError = null) }
            }
            AddExpenseUiEvent.ResetSuccess -> {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                _state.value = AddExpenseUiState(date = todayStr, paymentDate = todayStr, recurringStartDate = todayStr)
            }
            AddExpenseUiEvent.SaveExpense -> saveExpense()
        }
    }

    private fun saveExpense() {
        val currentState = _state.value
        val validationResult = validateExpenseUseCase(
            title = currentState.title.ifBlank { currentState.category },
            category = currentState.category,
            amount = currentState.amount,
            date = currentState.date
        )

        when (validationResult) {
            is ValidationResult.Error -> {
                _state.update { it.copy(validationError = validationResult.message) }
            }
            ValidationResult.Success -> {
                _state.update { it.copy(isLoading = true, validationError = null) }
                viewModelScope.launch {
                    try {
                        val parsedAmount = currentState.amount.toDoubleOrNull() ?: 0.0
                        val isPaid = currentState.paymentStatus.equals("Paid", ignoreCase = true)
                        val paidAmount = if (isPaid) parsedAmount else 0.0
                        val remainingAmount = if (isPaid) 0.0 else parsedAmount
                        val status = if (isPaid) "Paid" else if (currentState.paymentStatus.equals("Partially Paid", ignoreCase = true)) "Partially Paid" else "Unpaid"

                        val initialPayments = if (isPaid) {
                            listOf(
                                ExpensePayment(
                                    id = UUID.randomUUID().toString(),
                                    amount = parsedAmount,
                                    paymentDate = currentState.paymentDate.ifBlank { currentState.date },
                                    paymentMethod = currentState.paymentMethod,
                                    reference = currentState.paymentReference,
                                    notes = "Initial Payment"
                                )
                            )
                        } else emptyList()

                        val newExpense = Expense(
                            title = currentState.title.trim().ifBlank { currentState.category },
                            category = currentState.category,
                            amount = parsedAmount,
                            paidAmount = paidAmount,
                            remainingAmount = remainingAmount,
                            status = status,
                            date = currentState.date,
                            paymentDate = if (isPaid) currentState.paymentDate else null,
                            paymentMethod = currentState.paymentMethod,
                            vendor = currentState.vendor.trim().ifBlank { null },
                            notes = currentState.notes.trim(),
                            isRecurring = currentState.isRecurring,
                            recurringFrequency = if (currentState.isRecurring) currentState.recurringFrequency else null,
                            recurringStartDate = if (currentState.isRecurring) currentState.date else null,
                            recurringEndDate = if (currentState.isRecurring) currentState.recurringEndDate.ifBlank { null } else null,
                            recurringExpenseId = if (currentState.isRecurring) "rec_${UUID.randomUUID()}" else null,
                            payments = initialPayments,
                            receiptAttachment = currentState.selectedAttachment
                        )

                        addExpenseUseCase(newExpense)
                        _state.update { it.copy(isLoading = false, isSuccess = true) }
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, validationError = e.message) }
                    }
                }
            }
        }
    }
}


// ==========================================
// 4. EDIT EXPENSE VIEWMODEL
// ==========================================

data class EditExpenseUiState(
    val expenseId: Int = -1,
    val title: String = "",
    val category: String = "",
    val amount: String = "",
    val date: String = "",
    val paymentMethod: String = "",
    val vendor: String = "",
    val notes: String = "",
    val status: String = "Paid",
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val selectedAttachment: ExpenseAttachment? = null,
    val isPreparingAttachment: Boolean = false,
    val receiptUploadError: String? = null,
    val isLoading: Boolean = false,
    val isFetching: Boolean = false,
    val validationError: String? = null,
    val isSuccess: Boolean = false,
    val fetchError: String? = null,
    val existingPayments: List<ExpensePayment> = emptyList()
)

sealed class EditExpenseUiEvent {
    data class TitleChanged(val title: String) : EditExpenseUiEvent()
    data class CategoryChanged(val category: String) : EditExpenseUiEvent()
    data class AmountChanged(val amount: String) : EditExpenseUiEvent()
    data class DateChanged(val date: String) : EditExpenseUiEvent()
    data class PaymentMethodChanged(val method: String) : EditExpenseUiEvent()
    data class VendorChanged(val vendor: String) : EditExpenseUiEvent()
    data class NotesChanged(val notes: String) : EditExpenseUiEvent()
    data class AttachReceiptUri(val uri: Uri, val mimeType: String? = null, val source: String = "UPLOAD") : EditExpenseUiEvent()
    object RemoveReceipt : EditExpenseUiEvent()
    object SaveExpense : EditExpenseUiEvent()
}

sealed class EditExpenseUiEffect {
    object NavigateBack : EditExpenseUiEffect()
    data class ShowMessage(val message: String) : EditExpenseUiEffect()
}

@HiltViewModel
class EditExpenseViewModel @Inject constructor(
    private val getExpenseDetailsUseCase: GetExpenseDetailsUseCase,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val validateExpenseUseCase: ValidateExpenseUseCase,
    private val expenseReceiptManager: ExpenseReceiptManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditExpenseUiState())
    val state: StateFlow<EditExpenseUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<EditExpenseUiEffect>()
    val effects: SharedFlow<EditExpenseUiEffect> = _effects.asSharedFlow()

    private var originalExpense: Expense? = null

    init {
        val id = savedStateHandle.get<Any>("expenseId")?.let { raw ->
            when (raw) {
                is Int -> raw
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull() ?: -1
                else -> -1
            }
        } ?: -1
        if (id != -1) {
            _state.update { it.copy(expenseId = id) }
            fetchExpenseDetails(id)
        } else {
            _state.update { it.copy(fetchError = "Invalid Expense ID") }
        }
    }

    private fun fetchExpenseDetails(id: Int) {
        _state.update { it.copy(isFetching = true, fetchError = null) }
        viewModelScope.launch {
            try {
                val expense = getExpenseDetailsUseCase(id)
                if (expense != null) {
                    originalExpense = expense
                    _state.update {
                        it.copy(
                            isFetching = false,
                            title = expense.title,
                            category = expense.category,
                            amount = expense.amount.toString(),
                            date = expense.date,
                            paymentMethod = expense.paymentMethod,
                            vendor = expense.vendor ?: "",
                            notes = expense.notes,
                            status = expense.status,
                            paidAmount = expense.paidAmount,
                            remainingAmount = expense.remainingAmount,
                            selectedAttachment = expense.receiptAttachment,
                            existingPayments = expense.payments
                        )
                    }
                } else {
                    _state.update { it.copy(isFetching = false, fetchError = "Expense not found") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isFetching = false, fetchError = e.message ?: "Failed to load expense") }
            }
        }
    }

    fun onEvent(event: EditExpenseUiEvent) {
        when (event) {
            is EditExpenseUiEvent.TitleChanged -> _state.update { it.copy(title = event.title) }
            is EditExpenseUiEvent.CategoryChanged -> _state.update { it.copy(category = event.category) }
            is EditExpenseUiEvent.AmountChanged -> _state.update { it.copy(amount = event.amount) }
            is EditExpenseUiEvent.DateChanged -> _state.update { it.copy(date = event.date) }
            is EditExpenseUiEvent.PaymentMethodChanged -> _state.update { it.copy(paymentMethod = event.method) }
            is EditExpenseUiEvent.VendorChanged -> _state.update { it.copy(vendor = event.vendor) }
            is EditExpenseUiEvent.NotesChanged -> _state.update { it.copy(notes = event.notes) }
            is EditExpenseUiEvent.AttachReceiptUri -> {
                _state.update { it.copy(isPreparingAttachment = true, receiptUploadError = null) }
                viewModelScope.launch {
                    val res = expenseReceiptManager.prepareLocalAttachment(
                        uri = event.uri,
                        category = _state.value.category,
                        mimeTypeInput = event.mimeType,
                        source = event.source
                    )
                    if (res is PgResult.Success) {
                        _state.update { it.copy(selectedAttachment = res.data, isPreparingAttachment = false) }
                    } else if (res is PgResult.Failure) {
                        _state.update { it.copy(isPreparingAttachment = false, receiptUploadError = res.error.message) }
                    }
                }
            }
            EditExpenseUiEvent.RemoveReceipt -> {
                _state.update { it.copy(selectedAttachment = null, receiptUploadError = null) }
            }
            EditExpenseUiEvent.SaveExpense -> saveExpense()
        }
    }

    private fun saveExpense() {
        val currentState = _state.value
        val validationResult = validateExpenseUseCase(
            title = currentState.title,
            category = currentState.category,
            amount = currentState.amount,
            date = currentState.date
        )

        when (validationResult) {
            is ValidationResult.Error -> {
                _state.update { it.copy(validationError = validationResult.message) }
            }
            ValidationResult.Success -> {
                _state.update { it.copy(isLoading = true, validationError = null) }
                viewModelScope.launch {
                    try {
                        val parsedAmount = currentState.amount.toDouble()
                        val currentPaid = currentState.paidAmount
                        val newRemaining = (parsedAmount - currentPaid).coerceAtLeast(0.0)
                        val newStatus = when {
                            newRemaining <= 0.001 && currentPaid > 0 -> "Paid"
                            currentPaid > 0.001 -> "Partially Paid"
                            else -> "Unpaid"
                        }

                        val updated = (originalExpense ?: Expense(
                            id = currentState.expenseId,
                            title = currentState.title,
                            category = currentState.category,
                            amount = parsedAmount,
                            date = currentState.date
                        )).copy(
                            title = currentState.title.trim(),
                            category = currentState.category,
                            amount = parsedAmount,
                            remainingAmount = newRemaining,
                            status = newStatus,
                            date = currentState.date,
                            paymentMethod = currentState.paymentMethod,
                            vendor = currentState.vendor.trim().ifBlank { null },
                            notes = currentState.notes.trim(),
                            receiptAttachment = currentState.selectedAttachment
                        )

                        updateExpenseUseCase(updated)
                        _state.update { it.copy(isLoading = false, isSuccess = true) }
                        _effects.emit(EditExpenseUiEffect.ShowMessage("Expense updated successfully"))
                        _effects.emit(EditExpenseUiEffect.NavigateBack)
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, validationError = e.message) }
                    }
                }
            }
        }
    }
}
