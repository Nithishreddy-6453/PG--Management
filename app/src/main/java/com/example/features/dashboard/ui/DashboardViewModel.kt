package com.example.features.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    object Empty : DashboardUiState
    data class Success(val data: DashboardSummary) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

sealed interface DashboardUiEvent {
    object LoadSummary : DashboardUiEvent
    object Retry : DashboardUiEvent
    data class SelectMonth(val month: String) : DashboardUiEvent
}

sealed interface DashboardUiEffect {
    data class ShowToast(val message: String) : DashboardUiEffect
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardSummaryUseCase: DashboardSummaryUseCase,
    private val currentBillingMonthManager: CurrentBillingMonthManager,
    private val currentPropertyManager: CurrentPropertyManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val currentBillingMonth: StateFlow<String> = currentBillingMonthManager.currentBillingMonthState

    private val _uiEffect = MutableSharedFlow<DashboardUiEffect>()
    val uiEffect: SharedFlow<DashboardUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadDashboardSummary()
    }

    fun handleEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.LoadSummary -> loadDashboardSummary()
            is DashboardUiEvent.Retry -> loadDashboardSummary()
            is DashboardUiEvent.SelectMonth -> selectBillingMonth(event.month)
        }
    }

    fun selectBillingMonth(month: String) {
        currentBillingMonthManager.setCurrentBillingMonth(month)
    }

    private fun loadDashboardSummary() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            currentPropertyManager?.restoreActiveProperty()
            dashboardSummaryUseCase()
                .onEach { summary ->
                    if (summary.occupancy.totalRooms == 0 && summary.occupancy.totalBeds == 0 && summary.occupancy.activeTenantsCount == 0) {
                        _uiState.value = DashboardUiState.Empty
                    } else {
                        _uiState.value = DashboardUiState.Success(summary)
                    }
                }
                .catch { error ->
                    _uiState.value = DashboardUiState.Error(error.localizedMessage ?: "Unknown error occurred")
                }
                .collect()
        }
    }
}
