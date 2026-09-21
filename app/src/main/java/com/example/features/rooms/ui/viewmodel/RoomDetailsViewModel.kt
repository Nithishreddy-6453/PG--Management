package com.example.features.rooms.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.rooms.domain.usecase.DeleteRoomUseCase
import com.example.features.rooms.domain.usecase.GetRoomDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoomDetailsUiState {
    object Loading : RoomDetailsUiState
    data class Success(
        val roomSummary: com.example.features.rooms.domain.model.RoomSummary,
        val recentPayments: List<com.example.data.database.RentPaymentEntity>
    ) : RoomDetailsUiState
    data class Error(val message: String) : RoomDetailsUiState
}

@HiltViewModel
class RoomDetailsViewModel @Inject constructor(
    private val repository: RoomRepository,
    getRoomDetailsUseCase: GetRoomDetailsUseCase,
    private val deleteRoomUseCase: DeleteRoomUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val roomNumber: String = savedStateHandle["roomNumber"] ?: ""

    private val _deleteEvent = MutableSharedFlow<RoomValidationResult>()
    val deleteEvent: SharedFlow<RoomValidationResult> = _deleteEvent.asSharedFlow()

    val uiState: StateFlow<RoomDetailsUiState> = combine(
        getRoomDetailsUseCase(roomNumber),
        repository.getPaymentsForRoomFlow(roomNumber)
    ) { summary, payments ->
        if (summary != null) {
            RoomDetailsUiState.Success(
                roomSummary = summary,
                recentPayments = payments.take(10) // Display up to 10 most recent payments
            )
        } else {
            RoomDetailsUiState.Error("Room $roomNumber not found.")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomDetailsUiState.Loading
    )

    fun deleteRoom() {
        viewModelScope.launch {
            val result = deleteRoomUseCase(roomNumber)
            _deleteEvent.emit(result)
        }
    }
}
