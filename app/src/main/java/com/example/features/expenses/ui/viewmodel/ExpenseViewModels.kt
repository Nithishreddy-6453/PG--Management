package com.example.features.expenses.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ==========================================
// 1. EXPENSE LIST VIEWMODEL
// ==========================================

data class ExpenseListUiState(
    val isLoading: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val selectedMonth: String = "", // e.g. "2026-07"
    val selectedPaymentMethod: String = "All",
    val sortBy: SortType = SortType.DATE_DESC,
    val error: String? = null,
    
    // Stats
    val totalMonthlyExpenses: Double = 0.0,
    val todaysExpenses: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val budgetLimit: Double = 50000.0,
    val budgetUtilization: Double = 0.0
)

sealed class ExpenseListUiEvent {
    data class SearchChanged(val query: String) : ExpenseListUiEvent()
    data class CategoryFilterChanged(val category: String) : ExpenseListUiEvent()
    data class MonthFilterChanged(val month: String) : ExpenseListUiEvent()
    data class PaymentMethodFilterChanged(val method: String) : ExpenseListUiEvent()
    data class SortOrderChanged(val sortType: SortType) : ExpenseListUiEvent()
    object Retry : ExpenseListUiEvent()
}

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val getExpensesUseCase: GetExpensesUseCase,
    private val searchExpensesUseCase: SearchExpensesUseCase,
    private val filterExpensesUseCase: FilterExpensesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseListUiState())
    val state: StateFlow<ExpenseListUiState> = _state.asStateFlow()

    init {
        // Set current month as default filter
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        _state.update { it.copy(selectedMonth = currentMonth) }
        loadExpenses()
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
            is ExpenseListUiEvent.MonthFilterChanged -> {
                _state.update { it.copy(selectedMonth = event.month) }
                applyFilters()
            }
            is ExpenseListUiEvent.PaymentMethodFilterChanged -> {
                _state.update { it.copy(selectedPaymentMethod = event.method) }
                applyFilters()
            }
            is ExpenseListUiEvent.SortOrderChanged -> {
                _state.update { it.copy(sortBy = event.sortType) }
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
        val filtered = filterExpensesUseCase(
            expenses = currentList,
            category = _state.value.selectedCategory,
            month = _state.value.selectedMonth,
            paymentMethod = _state.value.selectedPaymentMethod,
            sortBy = _state.value.sortBy
        )
        
        val searched = searchExpensesUseCase(filtered, _state.value.searchQuery)
        
        // Compute statistics on the entire list of expenses for the selected month to get accurate dashboard metrics
        val currentMonthStr = _state.value.selectedMonth.ifEmpty {
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        }
        val monthlyExpenses = currentList.filter { it.date.startsWith(currentMonthStr) }
        val totalMonthly = monthlyExpenses.sumOf { it.amount }
        
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todaysExpenses = currentList.filter { it.date == todayStr }.sumOf { it.amount }
        
        val breakdown = monthlyExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            
        val budgetLimit = _state.value.budgetLimit
        val budgetUtil = if (budgetLimit > 0) (totalMonthly / budgetLimit) * 100.0 else 0.0

        _state.update {
            it.copy(
                filteredExpenses = searched,
                totalMonthlyExpenses = totalMonthly,
                todaysExpenses = todaysExpenses,
                categoryBreakdown = breakdown,
                budgetUtilization = budgetUtil
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
    val error: String? = null
)

sealed class ExpenseDetailsUiEvent {
    object DeleteExpense : ExpenseDetailsUiEvent()
}

sealed class ExpenseDetailsUiEffect {
    object NavigateBack : ExpenseDetailsUiEffect()
    data class ShowToast(val message: String) : ExpenseDetailsUiEffect()
}

@HiltViewModel
class ExpenseDetailsViewModel @Inject constructor(
    private val getExpenseDetailsUseCase: GetExpenseDetailsUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseDetailsUiState())
    val state: StateFlow<ExpenseDetailsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseDetailsUiEffect>()
    val effects: SharedFlow<ExpenseDetailsUiEffect> = _effects.asSharedFlow()

    private var expenseId: Int = -1

    init {
        expenseId = savedStateHandle.get<String>("expenseId")?.toIntOrNull() ?: -1
        if (expenseId != -1) {
            loadExpenseDetails(expenseId)
        } else {
            _state.update { it.copy(error = "Invalid Expense ID") }
        }
    }

    private fun loadExpenseDetails(id: Int) {
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
        }
    }
}


// ==========================================
// 3. ADD EXPENSE VIEWMODEL
// ==========================================

data class AddExpenseUiState(
    val title: String = "",
    val category: String = "Miscellaneous",
    val amount: String = "",
    val date: String = "",
    val paymentMethod: String = "Cash",
    val vendor: String = "",
    val notes: String = "",
    val isLoading: Boolean = false,
    val validationError: String? = null,
    val isSuccess: Boolean = false
)

sealed class AddExpenseUiEvent {
    data class TitleChanged(val title: String) : AddExpenseUiEvent()
    data class CategoryChanged(val category: String) : AddExpenseUiEvent()
    data class AmountChanged(val amount: String) : AddExpenseUiEvent()
    data class DateChanged(val date: String) : AddExpenseUiEvent()
    data class PaymentMethodChanged(val method: String) : AddExpenseUiEvent()
    data class VendorChanged(val vendor: String) : AddExpenseUiEvent()
    data class NotesChanged(val notes: String) : AddExpenseUiEvent()
    object SaveExpense : AddExpenseUiEvent()
}

sealed class AddExpenseUiEffect {
    object NavigateBack : AddExpenseUiEffect()
    data class ShowMessage(val message: String) : AddExpenseUiEffect()
}

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase: AddExpenseUseCase,
    private val validateExpenseUseCase: ValidateExpenseUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseUiState())
    val state: StateFlow<AddExpenseUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AddExpenseUiEffect>()
    val effects: SharedFlow<AddExpenseUiEffect> = _effects.asSharedFlow()

    init {
        // Set today's date as default
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _state.update { it.copy(date = todayStr) }
    }

    fun onEvent(event: AddExpenseUiEvent) {
        when (event) {
            is AddExpenseUiEvent.TitleChanged -> _state.update { it.copy(title = event.title) }
            is AddExpenseUiEvent.CategoryChanged -> _state.update { it.copy(category = event.category) }
            is AddExpenseUiEvent.AmountChanged -> _state.update { it.copy(amount = event.amount) }
            is AddExpenseUiEvent.DateChanged -> _state.update { it.copy(date = event.date) }
            is AddExpenseUiEvent.PaymentMethodChanged -> _state.update { it.copy(paymentMethod = event.method) }
            is AddExpenseUiEvent.VendorChanged -> _state.update { it.copy(vendor = event.vendor) }
            is AddExpenseUiEvent.NotesChanged -> _state.update { it.copy(notes = event.notes) }
            AddExpenseUiEvent.SaveExpense -> saveExpense()
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
                        val newExpense = Expense(
                            title = currentState.title,
                            category = currentState.category,
                            amount = currentState.amount.toDouble(),
                            date = currentState.date,
                            paymentMethod = currentState.paymentMethod,
                            vendor = currentState.vendor.ifBlank { null },
                            notes = currentState.notes
                        )
                        addExpenseUseCase(newExpense)
                        _state.update { it.copy(isLoading = false, isSuccess = true) }
                        _effects.emit(AddExpenseUiEffect.ShowMessage("Expense added successfully"))
                        _effects.emit(AddExpenseUiEffect.NavigateBack)
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
    val isLoading: Boolean = false,
    val isFetching: Boolean = false,
    val validationError: String? = null,
    val isSuccess: Boolean = false,
    val fetchError: String? = null
)

sealed class EditExpenseUiEvent {
    data class TitleChanged(val title: String) : EditExpenseUiEvent()
    data class CategoryChanged(val category: String) : EditExpenseUiEvent()
    data class AmountChanged(val amount: String) : EditExpenseUiEvent()
    data class DateChanged(val date: String) : EditExpenseUiEvent()
    data class PaymentMethodChanged(val method: String) : EditExpenseUiEvent()
    data class VendorChanged(val vendor: String) : EditExpenseUiEvent()
    data class NotesChanged(val notes: String) : EditExpenseUiEvent()
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditExpenseUiState())
    val state: StateFlow<EditExpenseUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<EditExpenseUiEffect>()
    val effects: SharedFlow<EditExpenseUiEffect> = _effects.asSharedFlow()

    init {
        val id = savedStateHandle.get<String>("expenseId")?.toIntOrNull() ?: -1
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
                    _state.update {
                        it.copy(
                            isFetching = false,
                            title = expense.title,
                            category = expense.category,
                            amount = expense.amount.toString(),
                            date = expense.date,
                            paymentMethod = expense.paymentMethod,
                            vendor = expense.vendor ?: "",
                            notes = expense.notes
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
                        val updatedExpense = Expense(
                            id = currentState.expenseId,
                            title = currentState.title,
                            category = currentState.category,
                            amount = currentState.amount.toDouble(),
                            date = currentState.date,
                            paymentMethod = currentState.paymentMethod,
                            vendor = currentState.vendor.ifBlank { null },
                            notes = currentState.notes
                        )
                        updateExpenseUseCase(updatedExpense)
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
