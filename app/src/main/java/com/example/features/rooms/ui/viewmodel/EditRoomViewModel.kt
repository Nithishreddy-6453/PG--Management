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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditRoomUiState(
    val roomNumber: String = "",
    val floor: String = "",
    val capacity: String = "2",
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

    private val roomNumberFromNav: String? = savedStateHandle["roomNumber"]

    init {
        roomNumberFromNav?.let { loadRoom(it) }
    }

    fun loadRoom(roomNumber: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val room = repository.getRoom(roomNumber)
            val tenants = repository.getTenantsInRoom(roomNumber).filter { !it.deleted && it.roomNumber.isNotBlank() }
            if (room != null) {
                _uiState.value = EditRoomUiState(
                    roomNumber = room.roomNumber,
                    floor = room.floor,
                    capacity = room.capacity.toString(),
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
        _uiState.value = _uiState.value.copy(capacity = capacity, error = null)
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

        _uiState.value = state.copy(isSaving = true)
        
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
                    _uiState.value = state.copy(isSaving = false)
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
