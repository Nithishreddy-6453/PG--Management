package com.example.features.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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

    val currentBillingMonth: StateFlow<String> = currentBillingMonthManager.currentBillingMonthState

    private val _uiEffect = MutableSharedFlow<DashboardUiEffect>()
    val uiEffect: SharedFlow<DashboardUiEffect> = _uiEffect.asSharedFlow()

    val uiState: StateFlow<DashboardUiState> = flow {
        currentPropertyManager?.restoreActiveProperty()
        emitAll(dashboardSummaryUseCase())
    }
        .map { summary ->
            if (summary.occupancy.totalRooms == 0 && summary.occupancy.totalBeds == 0 && summary.occupancy.activeTenantsCount == 0) {
                DashboardUiState.Empty
            } else {
                DashboardUiState.Success(summary)
            }
        }
        .catch { error ->
            emit(DashboardUiState.Error(error.localizedMessage ?: "Unknown error occurred"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    fun handleEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.LoadSummary,
            is DashboardUiEvent.Retry -> {
                // stateIn automatically manages reactivation
            }
            is DashboardUiEvent.SelectMonth -> selectBillingMonth(event.month)
        }
    }

    fun selectBillingMonth(month: String) {
        currentBillingMonthManager.setCurrentBillingMonth(month)
    }
}

