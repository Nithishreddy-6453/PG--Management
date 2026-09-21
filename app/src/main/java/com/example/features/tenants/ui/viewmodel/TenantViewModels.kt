package com.example.features.tenants.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.tenants.domain.repository.TenantRepository
import com.example.features.tenants.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ==================================================
// 1. TENANT LIST VIEW MODEL
// ==================================================

sealed interface TenantListUiState {
    object Loading : TenantListUiState
    object Empty : TenantListUiState
    data class Success(
        val tenants: List<TenantEntity>,
        val roomsList: List<String>,
        val searchQuery: String = "",
        val selectedRoomFilter: String = "All",
        val selectedOccupancyFilter: String = "Active",
        val sortBy: String = "Alphabetical"
    ) : TenantListUiState
}

@HiltViewModel
class TenantListViewModel @Inject constructor(
    private val getTenantsUseCase: GetTenantsUseCase,
    private val searchTenantUseCase: SearchTenantUseCase,
    private val repository: TenantRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _roomFilter = MutableStateFlow("All")
    private val _occupancyFilter = MutableStateFlow("Active")
    private val _sortBy = MutableStateFlow("Alphabetical")

    private data class TenantFilterState(
        val query: String = "",
        val room: String = "All",
        val occupancy: String = "Active",
        val sort: String = "Alphabetical"
    )

    private val filterState = combine(
        _searchQuery,
        _roomFilter,
        _occupancyFilter,
        _sortBy
    ) { query, room, occupancy, sort ->
        TenantFilterState(query, room, occupancy, sort)
    }

    val uiState: StateFlow<TenantListUiState> = combine(
        getTenantsUseCase.execute(),
        repository.getAllRoomsFlow(),
        filterState
    ) { tenants, rooms, filter ->
        if (tenants.isEmpty()) {
            TenantListUiState.Empty
        } else {
            val filteredList = searchTenantUseCase(tenants, filter.query, filter.room, filter.occupancy, filter.sort)
            val uniqueRooms = rooms.map { it.roomNumber }
            
            if (filteredList.isEmpty() && filter.query.isBlank() && filter.room == "All" && filter.occupancy == "All") {
                TenantListUiState.Empty
            } else {
                TenantListUiState.Success(
                    tenants = filteredList,
                    roomsList = uniqueRooms,
                    searchQuery = filter.query,
                    selectedRoomFilter = filter.room,
                    selectedOccupancyFilter = filter.occupancy,
                    sortBy = filter.sort
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TenantListUiState.Loading)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onRoomFilterChanged(room: String) {
        _roomFilter.value = room
    }

    fun onOccupancyFilterChanged(occupancy: String) {
        _occupancyFilter.value = occupancy
    }

    fun onSortByChanged(sort: String) {
        _sortBy.value = sort
    }

    fun deleteTenant(id: Int) {
        viewModelScope.launch {
            repository.deleteTenant(id)
        }
    }
}

// ==================================================
// 2. TENANT DETAILS VIEW MODEL
// ==================================================

sealed interface TenantDetailsUiState {
    object Loading : TenantDetailsUiState
    data class Error(val message: String) : TenantDetailsUiState
    data class Success(
        val tenant: TenantEntity,
        val payments: List<RentPaymentEntity>,
        val isVacating: Boolean = false,
        val isDeleting: Boolean = false
    ) : TenantDetailsUiState
}

sealed interface TenantDetailsUiEffect {
    object NavigateBack : TenantDetailsUiEffect
    data class ShowToast(val message: String) : TenantDetailsUiEffect
}

@HiltViewModel
class TenantDetailsViewModel @Inject constructor(
    private val getTenantUseCase: GetTenantUseCase,
    private val vacateTenantUseCase: VacateTenantUseCase,
    private val deleteTenantUseCase: DeleteTenantUseCase,
    private val repository: TenantRepository,
    private val getTenantLedgerUseCase: com.example.features.rent.domain.usecase.GetTenantLedgerUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tenantId: Int? = savedStateHandle.get<String>("tenantId")?.toIntOrNull()
        ?: savedStateHandle.get<Int>("tenantId")

    private val _uiState = MutableStateFlow<TenantDetailsUiState>(TenantDetailsUiState.Loading)
    val uiState: StateFlow<TenantDetailsUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<TenantDetailsUiEffect>()
    val uiEffect: SharedFlow<TenantDetailsUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadTenantDetails()
    }

    fun loadTenantDetails() {
        if (tenantId == null || tenantId == 0) {
            _uiState.value = TenantDetailsUiState.Error("Invalid Tenant ID.")
            return
        }
        _uiState.value = TenantDetailsUiState.Loading
        viewModelScope.launch {
            val tenant = getTenantUseCase.execute(tenantId)
            if (tenant != null) {
                getTenantLedgerUseCase(tenantId).collectLatest { payments ->
                    _uiState.value = TenantDetailsUiState.Success(
                        tenant = tenant,
                        payments = payments
                    )
                }
            } else {
                _uiState.value = TenantDetailsUiState.Error("Tenant not found.")
            }
        }
    }

    fun vacateTenant() {
        val currentState = _uiState.value
        if (currentState is TenantDetailsUiState.Success) {
            _uiState.value = currentState.copy(isVacating = true)
            viewModelScope.launch {
                val result = vacateTenantUseCase.execute(currentState.tenant.id)
                if (result is TenantValidationResult.Success) {
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Tenant vacated successfully."))
                    loadTenantDetails()
                } else if (result is TenantValidationResult.Error) {
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast(result.message))
                    _uiState.value = currentState.copy(isVacating = false)
                }
            }
        }
    }

    fun deleteTenant() {
        val currentState = _uiState.value
        if (currentState is TenantDetailsUiState.Success) {
            _uiState.value = currentState.copy(isDeleting = true)
            viewModelScope.launch {
                deleteTenantUseCase.execute(currentState.tenant.id)
                _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Tenant deleted successfully."))
                _uiEffect.emit(TenantDetailsUiEffect.NavigateBack)
            }
        }
    }
}

// ==================================================
// 3. ADD TENANT VIEW MODEL
// ==================================================

data class AddTenantUiState(
    val name: String = "",
    val phone: String = "",
    val alternateContact: String = "",
    val emergencyContact: String = "",
    val email: String = "",
    val dob: String = "",
    val gender: String = "Male",
    val address: String = "",
    val occupation: String = "",
    val companyOrCollege: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val advancePaid: String = "0.0",
    val moveInDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val roomNumber: String = "",
    val bedId: String = "",
    val notes: String = "",
    val kycDocType: String = "Aadhaar Card",
    
    val rooms: List<RoomEntity> = emptyList(),
    val availableBeds: List<String> = emptyList(),
    
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AddTenantViewModel @Inject constructor(
    private val addTenantUseCase: AddTenantUseCase,
    private val repository: TenantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTenantUiState())
    val uiState: StateFlow<AddTenantUiState> = _uiState.asStateFlow()

    init {
        loadRooms()
    }

    private fun loadRooms() {
        viewModelScope.launch {
            val roomsList = repository.getAllRooms()
            _uiState.update { it.copy(rooms = roomsList) }
            if (roomsList.isNotEmpty() && _uiState.value.roomNumber.isBlank()) {
                onRoomChanged(roomsList.first().roomNumber)
            } else if (roomsList.isEmpty() && _uiState.value.roomNumber.isBlank()) {
                _uiState.update {
                    it.copy(
                        roomNumber = "101",
                        availableBeds = listOf("Bed A", "Bed B", "Bed C"),
                        bedId = "Bed A"
                    )
                }
            }
        }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onAlternateContactChanged(value: String) = _uiState.update { it.copy(alternateContact = value) }
    fun onEmergencyContactChanged(value: String) = _uiState.update { it.copy(emergencyContact = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onDobChanged(value: String) = _uiState.update { it.copy(dob = value) }
    fun onGenderChanged(value: String) = _uiState.update { it.copy(gender = value) }
    fun onAddressChanged(value: String) = _uiState.update { it.copy(address = value) }
    fun onOccupationChanged(value: String) = _uiState.update { it.copy(occupation = value) }
    fun onCompanyOrCollegeChanged(value: String) = _uiState.update { it.copy(companyOrCollege = value) }
    fun onMonthlyRentChanged(value: String) = _uiState.update { it.copy(monthlyRent = value) }
    fun onSecurityDepositChanged(value: String) = _uiState.update { it.copy(securityDeposit = value) }
    fun onAdvancePaidChanged(value: String) = _uiState.update { it.copy(advancePaid = value) }
    fun onMoveInDateChanged(value: String) = _uiState.update { it.copy(moveInDate = value) }
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value) }
    fun onKycDocTypeChanged(value: String) = _uiState.update { it.copy(kycDocType = value) }

    fun onRoomChanged(roomNo: String) {
        val trimmedRoom = roomNo.trim()
        viewModelScope.launch {
            val room = uiState.value.rooms.find { it.roomNumber.equals(trimmedRoom, ignoreCase = true) }
                ?: repository.getRoom(trimmedRoom)
            if (room != null) {
                val activeTenants = repository.getTenantsInRoom(room.roomNumber).filter { !it.deleted && it.roomNumber.isNotBlank() }
                val occupiedBeds = activeTenants.map { it.bedId.trim().lowercase() }
                
                // Generate beds Bed A, Bed B, etc.
                val beds = (1..maxOf(room.capacity, 1)).map { "Bed ${(64 + it).toChar()}" }
                val available = beds.filter { !occupiedBeds.contains(it.trim().lowercase()) }
                val finalAvailable = if (available.isNotEmpty()) available else listOf("Bed A", "Bed B")
                
                _uiState.update { 
                    it.copy(
                        roomNumber = room.roomNumber,
                        bedId = if (available.isNotEmpty()) available.first() else "Bed A",
                        availableBeds = finalAvailable,
                        monthlyRent = if (it.monthlyRent.isBlank() && room.ratePerBed > 0) room.ratePerBed.toString() else it.monthlyRent,
                        securityDeposit = if (it.securityDeposit.isBlank() && room.ratePerBed > 0) room.ratePerBed.toString() else it.securityDeposit
                    )
                }
            } else {
                val defaultBeds = listOf("Bed A", "Bed B", "Bed C")
                _uiState.update { 
                    it.copy(
                        roomNumber = trimmedRoom,
                        availableBeds = defaultBeds,
                        bedId = if (it.bedId.isBlank()) "Bed A" else it.bedId
                    )
                }
            }
        }
    }

    fun onBedChanged(value: String) = _uiState.update { it.copy(bedId = value) }
    
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun saveTenant() {
        val state = uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = addTenantUseCase.execute(
                name = state.name,
                phone = state.phone,
                email = state.email,
                emergencyContact = state.emergencyContact,
                roomNumber = state.roomNumber,
                bedId = state.bedId,
                monthlyRent = state.monthlyRent.toDoubleOrNull() ?: 0.0,
                securityDeposit = state.securityDeposit.toDoubleOrNull() ?: 0.0,
                advancePaid = state.advancePaid.toDoubleOrNull() ?: 0.0,
                moveInDate = state.moveInDate,
                kycDocType = state.kycDocType,
                alternateContact = state.alternateContact,
                dob = state.dob,
                gender = state.gender,
                address = state.address,
                occupation = state.occupation,
                companyOrCollege = state.companyOrCollege,
                notes = state.notes
            )
            
            when (result) {
                is TenantValidationResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is TenantValidationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}

// ==================================================
// 4. EDIT TENANT VIEW MODEL
// ==================================================

data class EditTenantUiState(
    val id: Int = 0,
    val name: String = "",
    val phone: String = "",
    val alternateContact: String = "",
    val emergencyContact: String = "",
    val email: String = "",
    val dob: String = "",
    val gender: String = "Male",
    val address: String = "",
    val occupation: String = "",
    val companyOrCollege: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val advancePaid: String = "0.0",
    val moveInDate: String = "",
    val roomNumber: String = "",
    val bedId: String = "",
    val notes: String = "",
    val kycDocType: String = "Aadhaar Card",
    
    val rooms: List<RoomEntity> = emptyList(),
    val availableBeds: List<String> = emptyList(),
    
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class EditTenantViewModel @Inject constructor(
    private val getTenantUseCase: GetTenantUseCase,
    private val updateTenantUseCase: UpdateTenantUseCase,
    private val repository: TenantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tenantId: Int? = savedStateHandle.get<String>("tenantId")?.toIntOrNull()
        ?: savedStateHandle.get<Int>("tenantId")

    private val _uiState = MutableStateFlow(EditTenantUiState())
    val uiState: StateFlow<EditTenantUiState> = _uiState.asStateFlow()

    init {
        loadRoomsAndTenant()
    }

    private fun loadRoomsAndTenant() {
        if (tenantId == null || tenantId == 0) {
            _uiState.update { it.copy(error = "Invalid Tenant ID") }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val roomsList = repository.getAllRooms()
            val tenant = getTenantUseCase.execute(tenantId)
            
            if (tenant != null) {
                // Populate available beds including current bed
                val room = roomsList.find { it.roomNumber == tenant.roomNumber }
                val beds = if (room != null) {
                    val activeTenants = repository.getTenantsInRoom(tenant.roomNumber).filter { it.id != tenant.id }
                    val occupiedBeds = activeTenants.map { it.bedId.lowercase() }
                    val allBeds = (1..room.capacity).map { "Bed ${(64 + it).toChar()}" }
                    allBeds.filter { !occupiedBeds.contains(it.lowercase()) || it.equals(tenant.bedId, ignoreCase = true) }
                } else {
                    listOf(tenant.bedId).filter { it.isNotBlank() }
                }

                _uiState.update {
                    it.copy(
                        id = tenant.id,
                        name = tenant.name,
                        phone = tenant.phone,
                        alternateContact = tenant.alternateContact,
                        emergencyContact = tenant.emergencyContact,
                        email = tenant.email,
                        dob = tenant.dob,
                        gender = tenant.gender,
                        address = tenant.address,
                        occupation = tenant.occupation,
                        companyOrCollege = tenant.companyOrCollege,
                        monthlyRent = tenant.monthlyRent.toString(),
                        securityDeposit = tenant.securityDeposit.toString(),
                        advancePaid = tenant.advancePaid.toString(),
                        moveInDate = tenant.moveInDate,
                        roomNumber = tenant.roomNumber,
                        bedId = tenant.bedId,
                        notes = tenant.notes,
                        kycDocType = if (tenant.kycDocType.isBlank()) "None" else tenant.kycDocType,
                        rooms = roomsList,
                        availableBeds = beds,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Tenant not found") }
            }
        }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onAlternateContactChanged(value: String) = _uiState.update { it.copy(alternateContact = value) }
    fun onEmergencyContactChanged(value: String) = _uiState.update { it.copy(emergencyContact = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onDobChanged(value: String) = _uiState.update { it.copy(dob = value) }
    fun onGenderChanged(value: String) = _uiState.update { it.copy(gender = value) }
    fun onAddressChanged(value: String) = _uiState.update { it.copy(address = value) }
    fun onOccupationChanged(value: String) = _uiState.update { it.copy(occupation = value) }
    fun onCompanyOrCollegeChanged(value: String) = _uiState.update { it.copy(companyOrCollege = value) }
    fun onMonthlyRentChanged(value: String) = _uiState.update { it.copy(monthlyRent = value) }
    fun onSecurityDepositChanged(value: String) = _uiState.update { it.copy(securityDeposit = value) }
    fun onAdvancePaidChanged(value: String) = _uiState.update { it.copy(advancePaid = value) }
    fun onMoveInDateChanged(value: String) = _uiState.update { it.copy(moveInDate = value) }
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value) }
    fun onKycDocTypeChanged(value: String) = _uiState.update { it.copy(kycDocType = value) }

    fun onRoomChanged(roomNo: String) {
        viewModelScope.launch {
            val room = uiState.value.rooms.find { it.roomNumber == roomNo }
            if (room != null) {
                val activeTenants = repository.getTenantsInRoom(roomNo).filter { it.id != uiState.value.id }
                val occupiedBeds = activeTenants.map { it.bedId.lowercase() }
                
                val beds = (1..room.capacity).map { "Bed ${(64 + it).toChar()}" }
                val available = beds.filter { !occupiedBeds.contains(it.lowercase()) }
                
                _uiState.update { 
                    it.copy(
                        roomNumber = roomNo,
                        bedId = if (available.isNotEmpty()) available.first() else "",
                        availableBeds = available,
                        monthlyRent = room.ratePerBed.toString()
                    )
                }
            } else {
                _uiState.update { it.copy(roomNumber = roomNo, availableBeds = emptyList(), bedId = "") }
            }
        }
    }

    fun onBedChanged(value: String) = _uiState.update { it.copy(bedId = value) }
    
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun saveTenant() {
        val state = uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = updateTenantUseCase.execute(
                id = state.id,
                name = state.name,
                phone = state.phone,
                email = state.email,
                emergencyContact = state.emergencyContact,
                roomNumber = state.roomNumber,
                bedId = state.bedId,
                monthlyRent = state.monthlyRent.toDoubleOrNull() ?: 0.0,
                securityDeposit = state.securityDeposit.toDoubleOrNull() ?: 0.0,
                advancePaid = state.advancePaid.toDoubleOrNull() ?: 0.0,
                moveInDate = state.moveInDate,
                kycDocType = state.kycDocType,
                alternateContact = state.alternateContact,
                dob = state.dob,
                gender = state.gender,
                address = state.address,
                occupation = state.occupation,
                companyOrCollege = state.companyOrCollege,
                notes = state.notes
            )
            
            when (result) {
                is TenantValidationResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is TenantValidationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}
