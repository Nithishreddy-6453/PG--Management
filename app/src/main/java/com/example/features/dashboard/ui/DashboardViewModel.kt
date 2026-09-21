package com.example.features.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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
}

sealed interface DashboardUiEffect {
    data class ShowToast(val message: String) : DashboardUiEffect
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardSummaryUseCase: DashboardSummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<DashboardUiEffect>()
    val uiEffect: SharedFlow<DashboardUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadDashboardSummary()
    }

    fun handleEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.LoadSummary -> loadDashboardSummary()
            is DashboardUiEvent.Retry -> loadDashboardSummary()
        }
    }

    private fun loadDashboardSummary() {
        dashboardSummaryUseCase()
            .onStart {
                _uiState.value = DashboardUiState.Loading
            }
            .onEach { summary ->
                if (summary.occupancy.totalRooms == 0) {
                    _uiState.value = DashboardUiState.Empty
                } else {
                    _uiState.value = DashboardUiState.Success(summary)
                }
            }
            .catch { error ->
                _uiState.value = DashboardUiState.Error(error.localizedMessage ?: "Unknown error occurred")
            }
            .launchIn(viewModelScope)
    }
}
