package com.example.features.rooms.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.usecase.AddRoomUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddRoomUiState(
    val roomNumber: String = "",
    val floor: String = "",
    val capacity: String = "2",
    val ratePerBed: String = "6000",
    val roomType: String = "Non-AC",
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddRoomViewModel @Inject constructor(
    private val addRoomUseCase: AddRoomUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRoomUiState())
    val uiState: StateFlow<AddRoomUiState> = _uiState.asStateFlow()

    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent: SharedFlow<Unit> = _saveSuccessEvent.asSharedFlow()

    fun onRoomNumberChanged(roomNumber: String) {
        _uiState.value = _uiState.value.copy(roomNumber = roomNumber, error = null)
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
            val result = addRoomUseCase(
                roomNumber = state.roomNumber,
                floor = state.floor,
                capacity = capacityInt,
                ratePerBed = rateDouble,
                roomType = state.roomType,
                notes = state.notes
            )
            
            when (result) {
                is RoomValidationResult.Success -> {
                    _uiState.value = AddRoomUiState() // reset form
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
