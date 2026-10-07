package com.example.features.rooms.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.rooms.domain.usecase.UpdateRoomUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditRoomUiState(
    val roomNumber: String = "",
    val floor: String = "",
    val capacity: String = "",
    val ratePerBed: String = "6000",
    val roomType: String = "Non-AC",
    val notes: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val activeTenantsCount: Int = 0
)

@HiltViewModel
class EditRoomViewModel @Inject constructor(
    private val repository: RoomRepository,
    private val updateRoomUseCase: UpdateRoomUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditRoomUiState())
    val uiState: StateFlow<EditRoomUiState> = _uiState.asStateFlow()

    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent: SharedFlow<Unit> = _saveSuccessEvent.asSharedFlow()

    private val roomNumberFromNav: String? = savedStateHandle.get<String>("roomId") ?: savedStateHandle.get<String>("roomNumber")

    init {
        roomNumberFromNav?.let { loadRoom(it) }
    }

    fun loadRoom(roomNumber: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val roomSummary = repository.getRoomSummaryFlow(roomNumber).first()
            val room = roomSummary?.room ?: repository.getRoom(roomNumber)
            val beds = roomSummary?.beds.orEmpty().ifEmpty { repository.getBedsForRoom(roomNumber) }
            val tenants = repository.getTenantsInRoom(roomNumber).filter { !it.deleted && it.roomNumber.isNotBlank() }
            
            if (room != null) {
                val actualStoredCapacity = if (beds.isNotEmpty()) maxOf(room.capacity, beds.size) else room.capacity
                _uiState.value = EditRoomUiState(
                    roomNumber = room.roomNumber,
                    floor = room.floor,
                    capacity = actualStoredCapacity.toString(),
                    ratePerBed = room.ratePerBed.toString(),
                    roomType = room.roomType,
                    notes = room.notes,
                    activeTenantsCount = tenants.size,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Room $roomNumber not found."
                )
            }
        }
    }

    fun onFloorChanged(floor: String) {
        _uiState.value = _uiState.value.copy(floor = floor, error = null)
    }

    fun onCapacityChanged(capacity: String) {
        val capInt = capacity.toIntOrNull()
        val activeCount = _uiState.value.activeTenantsCount
        val errorMsg = if (capInt != null && capInt < activeCount) {
            "Room ${_uiState.value.roomNumber} currently has $activeCount active tenants. Bed capacity cannot be less than $activeCount."
        } else null
        _uiState.value = _uiState.value.copy(capacity = capacity, error = errorMsg)
    }

    fun onRatePerBedChanged(rate: String) {
        _uiState.value = _uiState.value.copy(ratePerBed = rate, error = null)
    }

    fun onRoomTypeChanged(type: String) {
        _uiState.value = _uiState.value.copy(roomType = type, error = null)
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun saveRoom() {
        val state = _uiState.value
        val capacityInt = state.capacity.toIntOrNull() ?: 0
        val rateDouble = state.ratePerBed.toDoubleOrNull() ?: -1.0

        if (capacityInt < state.activeTenantsCount) {
            _uiState.value = state.copy(
                error = "Room ${state.roomNumber} currently has ${state.activeTenantsCount} active tenants. Bed capacity cannot be less than ${state.activeTenantsCount}."
            )
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)
        
        viewModelScope.launch {
            val result = updateRoomUseCase(
                roomNumber = state.roomNumber,
                floor = state.floor,
                capacity = capacityInt,
                ratePerBed = rateDouble,
                roomType = state.roomType,
                notes = state.notes
            )
            
            when (result) {
                is RoomValidationResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _saveSuccessEvent.emit(Unit)
                }
                is RoomValidationResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
