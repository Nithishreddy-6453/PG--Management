package com.example.features.rooms.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.rooms.domain.model.RoomSummary
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

sealed interface RoomDetailsUiState {
    object Loading : RoomDetailsUiState
    data class Success(
        val roomSummary: RoomSummary,
        val recentPayments: List<RentPaymentEntity>,
        val currentBillingMonth: String = RentBillingEngine.formatCanonicalBillingMonth(Date())
    ) : RoomDetailsUiState
    data class Error(val message: String) : RoomDetailsUiState
}

@HiltViewModel
class RoomDetailsViewModel @Inject constructor(
    private val repository: RoomRepository,
    getRoomDetailsUseCase: GetRoomDetailsUseCase,
    private val deleteRoomUseCase: DeleteRoomUseCase,
    private val currentBillingMonthManager: CurrentBillingMonthManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val roomNumber: String = savedStateHandle.get<Any>("roomId")?.toString()
        ?: savedStateHandle.get<Any>("roomNumber")?.toString()
        ?: ""

    private val _deleteEvent = MutableSharedFlow<RoomValidationResult>()
    val deleteEvent: SharedFlow<RoomValidationResult> = _deleteEvent.asSharedFlow()

    val uiState: StateFlow<RoomDetailsUiState> = combine(
        getRoomDetailsUseCase(roomNumber),
        repository.getPaymentsForRoomFlow(roomNumber),
        currentBillingMonthManager.currentBillingMonthFlow
    ) { summary, payments, billingMonth ->
        if (summary != null) {
            RoomDetailsUiState.Success(
                roomSummary = summary,
                recentPayments = payments,
                currentBillingMonth = billingMonth
            )
        } else {
            RoomDetailsUiState.Error("Room $roomNumber not found.")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomDetailsUiState.Loading
    )

    private val _actionEvent = MutableSharedFlow<RoomValidationResult>()
    val actionEvent: SharedFlow<RoomValidationResult> = _actionEvent.asSharedFlow()

    fun deleteRoom() {
        viewModelScope.launch {
            val result = deleteRoomUseCase(roomNumber)
            _deleteEvent.emit(result)
        }
    }

    fun addBed() {
        viewModelScope.launch {
            val currentSummary = (uiState.value as? RoomDetailsUiState.Success)?.roomSummary
            if (currentSummary == null) {
                _actionEvent.emit(RoomValidationResult.Error("Room details not loaded."))
                return@launch
            }
            if (currentSummary.totalBeds >= 10) {
                _actionEvent.emit(RoomValidationResult.Error("Maximum capacity of 10 beds reached."))
                return@launch
            }
            val newCapacity = currentSummary.totalBeds + 1
            val updatedRoom = currentSummary.room.copy(capacity = newCapacity)
            repository.insertRoom(updatedRoom)
            _actionEvent.emit(RoomValidationResult.Success)
        }
    }

    fun toggleBedBlock(bedId: String, currentStatus: String) {
        viewModelScope.launch {
            val isCurrentlyBlocked = currentStatus.equals("BLOCKED", ignoreCase = true)
            val result = repository.blockBed(roomNumber, bedId, !isCurrentlyBlocked)
            _actionEvent.emit(result)
        }
    }
}
