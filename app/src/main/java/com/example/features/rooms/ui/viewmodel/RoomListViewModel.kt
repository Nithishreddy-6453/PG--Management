package com.example.features.rooms.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.usecase.DeleteRoomUseCase
import com.example.features.rooms.domain.usecase.FilterRoomsUseCase
import com.example.features.rooms.domain.usecase.GetRoomsUseCase
import com.example.features.rooms.domain.usecase.OccupancyCalculationUseCase
import com.example.features.rooms.domain.usecase.OverallOccupancy
import com.example.features.rooms.domain.usecase.SearchRoomsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoomListUiState {
    object Loading : RoomListUiState
    data class Success(
        val rooms: List<RoomSummary>,
        val overallOccupancy: OverallOccupancy,
        val availableFloors: List<String>,
        val availableRoomTypes: List<String>,
        val searchQuery: String,
        val selectedFloor: String,
        val selectedRoomType: String,
        val selectedStatus: String,
        val sortBy: String
    ) : RoomListUiState
    object Empty : RoomListUiState
}

data class RoomFilterState(
    val searchQuery: String = "",
    val selectedFloor: String = "All",
    val selectedRoomType: String = "All",
    val selectedStatus: String = "All",
    val sortBy: String = "Room Number"
)

@HiltViewModel
class RoomListViewModel @Inject constructor(
    getRoomsUseCase: GetRoomsUseCase,
    private val searchRoomsUseCase: SearchRoomsUseCase,
    private val filterRoomsUseCase: FilterRoomsUseCase,
    private val deleteRoomUseCase: DeleteRoomUseCase,
    private val occupancyCalculationUseCase: OccupancyCalculationUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFloor = MutableStateFlow("All")
    private val _selectedRoomType = MutableStateFlow("All")
    private val _selectedStatus = MutableStateFlow("All")
    private val _sortBy = MutableStateFlow("Room Number") // "Room Number", "Rent Asc", "Rent Desc", "Beds Available"

    private val _actionEvent = MutableSharedFlow<RoomValidationResult>()
    val actionEvent: SharedFlow<RoomValidationResult> = _actionEvent.asSharedFlow()

    private val filterState: StateFlow<RoomFilterState> = combine(
        _searchQuery,
        _selectedFloor,
        _selectedRoomType,
        _selectedStatus,
        _sortBy
    ) { search, floor, roomType, status, sort ->
        RoomFilterState(
            searchQuery = search,
            selectedFloor = floor,
            selectedRoomType = roomType,
            selectedStatus = status,
            sortBy = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RoomFilterState()
    )

    val uiState: StateFlow<RoomListUiState> = combine(
        getRoomsUseCase(),
        filterState
    ) { rooms, filters ->
        if (rooms.isEmpty()) {
            RoomListUiState.Empty
        } else {
            // Find floors and types dynamically for filters
            val floors = listOf("All") + rooms.map { it.floor }.distinct().sorted()
            val types = listOf("All") + rooms.map { it.roomType }.distinct().sorted()
            
            // Apply search
            var processedRooms = searchRoomsUseCase(rooms, filters.searchQuery)
            
            // Apply filter
            processedRooms = filterRoomsUseCase(
                rooms = processedRooms,
                floor = filters.selectedFloor,
                roomType = filters.selectedRoomType,
                occupancyStatus = filters.selectedStatus
            )
            
            // Apply sorting
            processedRooms = when (filters.sortBy) {
                "Room Number" -> processedRooms.sortedBy { it.roomNumber }
                "Rent Asc" -> processedRooms.sortedBy { it.ratePerBed }
                "Rent Desc" -> processedRooms.sortedByDescending { it.ratePerBed }
                "Beds Available" -> processedRooms.sortedByDescending { it.availableBeds }
                else -> processedRooms.sortedBy { it.roomNumber }
            }
            
            val overall = occupancyCalculationUseCase(processedRooms)
            
            RoomListUiState.Success(
                rooms = processedRooms,
                overallOccupancy = overall,
                availableFloors = floors,
                availableRoomTypes = types,
                searchQuery = filters.searchQuery,
                selectedFloor = filters.selectedFloor,
                selectedRoomType = filters.selectedRoomType,
                selectedStatus = filters.selectedStatus,
                sortBy = filters.sortBy
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RoomListUiState.Loading
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFloorChanged(floor: String) {
        _selectedFloor.value = floor
    }

    fun onRoomTypeChanged(type: String) {
        _selectedRoomType.value = type
    }

    fun onStatusChanged(status: String) {
        _selectedStatus.value = status
    }

    fun onSortByChanged(sort: String) {
        _sortBy.value = sort
    }

    fun deleteRoom(roomNumber: String) {
        viewModelScope.launch {
            val result = deleteRoomUseCase(roomNumber)
            _actionEvent.emit(result)
        }
    }
}
