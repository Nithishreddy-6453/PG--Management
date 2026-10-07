package com.example.features.tenants.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.tenants.domain.model.TenantMedia
import com.example.features.tenants.domain.model.UploadPhotoProgress
import com.example.features.tenants.domain.repository.TenantRepository
import com.example.features.tenants.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
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
        val selectedOccupancyFilter: String = "All",
        val sortBy: String = "Name",
        val totalCount: Int = 0,
        val activeCount: Int = 0,
        val leavingSoonCount: Int = 0,
        val vacatedCount: Int = 0,
        val newCount: Int = 0,
        val pendingSubmissionsCount: Int = 0
    ) : TenantListUiState
}

@HiltViewModel
class TenantListViewModel @Inject constructor(
    private val getTenantsUseCase: GetTenantsUseCase,
    private val searchTenantUseCase: SearchTenantUseCase,
    private val repository: TenantRepository,
    private val getPendingCountUseCase: com.example.features.googleform.domain.usecase.GetPendingCountUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _roomFilter = MutableStateFlow("All")
    private val _occupancyFilter = MutableStateFlow("All")
    private val _sortBy = MutableStateFlow("Name")

    private data class TenantFilterState(
        val query: String = "",
        val room: String = "All",
        val occupancy: String = "All",
        val sort: String = "Name"
    )

    private val filterState = combine(
        _searchQuery,
        _roomFilter,
        _occupancyFilter,
        _sortBy
    ) { query, room, occupancy, sort ->
        TenantFilterState(query, room, occupancy, sort)
    }

    private val pendingCountFlow: Flow<Int> = flow {
        val propId = repository.getCurrentPropertyId()
        emitAll(getPendingCountUseCase(propId))
    }

    val uiState: StateFlow<TenantListUiState> = combine(
        getTenantsUseCase.execute(),
        repository.getAllRoomsFlow(),
        pendingCountFlow.onStart { emit(0) },
        filterState
    ) { tenants, rooms, pendingCount, filter ->
        if (tenants.isEmpty() && pendingCount == 0) {
            TenantListUiState.Empty
        } else {
            val filteredList = searchTenantUseCase(tenants, filter.query, filter.room, filter.occupancy, filter.sort)
            val uniqueRooms = rooms.map { it.roomNumber }.sorted()

            fun isRecentMoveIn(moveInDate: String?): Boolean {
                if (moveInDate.isNullOrBlank()) return false
                val d = com.example.core.util.PgDateUtil.parseDate(moveInDate) ?: return false
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -30)
                return d.after(cal.time)
            }

            val totalCount = tenants.size
            val activeCount = tenants.count { it.roomNumber.isNotBlank() && !it.deleted }
            val leavingSoonCount = tenants.count { it.roomNumber.isNotBlank() && !it.deleted && it.leavingDate.isNotBlank() }
            val vacatedCount = tenants.count { it.roomNumber.isBlank() || it.deleted }
            val newCount = tenants.count { it.roomNumber.isNotBlank() && !it.deleted && isRecentMoveIn(it.moveInDate) }

            TenantListUiState.Success(
                tenants = filteredList,
                roomsList = uniqueRooms,
                searchQuery = filter.query,
                selectedRoomFilter = filter.room,
                selectedOccupancyFilter = filter.occupancy,
                sortBy = filter.sort,
                totalCount = totalCount,
                activeCount = activeCount,
                leavingSoonCount = leavingSoonCount,
                vacatedCount = vacatedCount,
                newCount = newCount,
                pendingSubmissionsCount = pendingCount
            )
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
        val profilePhoto: TenantMedia? = null,
        val uploadProgress: UploadPhotoProgress = UploadPhotoProgress.Idle,
        val isPhotoUploading: Boolean = false,
        val isPhotoDownloading: Boolean = false,
        val photoDownloadError: String? = null,
        val needsDriveConsent: Boolean = false,
        val isVacating: Boolean = false,
        val isDeleting: Boolean = false
    ) : TenantDetailsUiState
}

sealed interface TenantDetailsUiEffect {
    object NavigateBack : TenantDetailsUiEffect
    data class ShowToast(val message: String) : TenantDetailsUiEffect
    data class LaunchGoogleConsent(val intent: Intent) : TenantDetailsUiEffect
}

@HiltViewModel
class TenantDetailsViewModel @Inject constructor(
    private val getTenantUseCase: GetTenantUseCase,
    private val vacateTenantUseCase: VacateTenantUseCase,
    private val deleteTenantUseCase: DeleteTenantUseCase,
    private val updateLeavingDateUseCase: UpdateLeavingDateUseCase,
    private val repository: TenantRepository,
    private val getTenantLedgerUseCase: com.example.features.rent.domain.usecase.GetTenantLedgerUseCase,
    private val getTenantProfilePhotoUseCase: GetTenantProfilePhotoUseCase,
    private val uploadTenantProfilePhotoUseCase: UploadTenantProfilePhotoUseCase,
    private val deleteTenantProfilePhotoUseCase: DeleteTenantProfilePhotoUseCase,
    private val fetchAndCacheProfilePhotoUseCase: FetchAndCacheProfilePhotoUseCase,
    private val syncTenantDriveFolderUseCase: SyncTenantDriveFolderUseCase,
    private val googleAuthManager: GoogleFormsAuthManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tenantId: Int? = savedStateHandle.get<String>("tenantId")?.toIntOrNull()
        ?: savedStateHandle.get<Int>("tenantId")

    private val _uiState = MutableStateFlow<TenantDetailsUiState>(TenantDetailsUiState.Loading)
    val uiState: StateFlow<TenantDetailsUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<TenantDetailsUiEffect>()
    val uiEffect: SharedFlow<TenantDetailsUiEffect> = _uiEffect.asSharedFlow()

    private var pendingPhotoUploadUri: Uri? = null

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
                // Ensure photo is fetched / cached from Drive if available and sync human-readable folder name
                if (tenant.cloudId.isNotBlank()) {
                    launch { fetchAndCacheProfilePhotoUseCase(tenant.cloudId) }
                    launch { syncTenantDriveFolderUseCase(tenant.cloudId) }
                }

                combine(
                    getTenantLedgerUseCase(tenantId),
                    getTenantProfilePhotoUseCase(tenant.cloudId)
                ) { payments, photo ->
                    val prev = _uiState.value
                    val currentProgress = if (prev is TenantDetailsUiState.Success) prev.uploadProgress else UploadPhotoProgress.Idle
                    val isUploading = if (prev is TenantDetailsUiState.Success) prev.isPhotoUploading else false
                    val isDownloading = if (prev is TenantDetailsUiState.Success) prev.isPhotoDownloading else false
                    val downloadErr = if (prev is TenantDetailsUiState.Success) prev.photoDownloadError else null
                    val needsConsent = if (prev is TenantDetailsUiState.Success) prev.needsDriveConsent else false

                    TenantDetailsUiState.Success(
                        tenant = tenant,
                        payments = payments,
                        profilePhoto = photo,
                        uploadProgress = currentProgress,
                        isPhotoUploading = isUploading,
                        isPhotoDownloading = isDownloading,
                        photoDownloadError = downloadErr,
                        needsDriveConsent = needsConsent
                    )
                }.collectLatest { state ->
                    _uiState.value = state

                    // Check if tenant has remote photo on Google Drive but local file is not yet cached
                    val photo = state.profilePhoto
                    if (photo != null && photo.driveFileId.isNotBlank() && !photo.hasLocalFile) {
                        downloadPhotoIfMissing(state.tenant.cloudId)
                    }
                }
            } else {
                _uiState.value = TenantDetailsUiState.Error("Tenant not found.")
            }
        }
    }

    fun downloadPhotoIfMissing(tenantCloudId: String) {
        val current = _uiState.value as? TenantDetailsUiState.Success ?: return
        if (current.isPhotoDownloading) return

        viewModelScope.launch {
            val hasDrive = googleAuthManager.hasDriveScope()
            if (!hasDrive) {
                _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                    needsDriveConsent = true,
                    isPhotoDownloading = false
                ) ?: return@launch
                return@launch
            }

            _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                isPhotoDownloading = true,
                photoDownloadError = null,
                needsDriveConsent = false
            ) ?: return@launch

            val res = fetchAndCacheProfilePhotoUseCase(tenantCloudId)
            when (res) {
                is PgResult.Success -> {
                    _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                        isPhotoDownloading = false,
                        photoDownloadError = null,
                        needsDriveConsent = false
                    ) ?: return@launch
                }
                is PgResult.Failure -> {
                    if (res.error is PgError.ConsentRequiredError) {
                        _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                            isPhotoDownloading = false,
                            needsDriveConsent = true,
                            photoDownloadError = null
                        ) ?: return@launch
                    } else {
                        _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                            isPhotoDownloading = false,
                            photoDownloadError = res.error.message ?: "Failed to download photo from Google Drive"
                        ) ?: return@launch
                    }
                }
            }
        }
    }

    fun retryPhotoDownload() {
        val current = _uiState.value as? TenantDetailsUiState.Success ?: return
        if (current.tenant.cloudId.isNotBlank()) {
            downloadPhotoIfMissing(current.tenant.cloudId)
        }
    }

    fun uploadProfilePhoto(uri: Uri) {
        val currentState = _uiState.value
        if (currentState !is TenantDetailsUiState.Success) return

        pendingPhotoUploadUri = uri
        _uiState.value = currentState.copy(
            isPhotoUploading = true,
            uploadProgress = UploadPhotoProgress.ProcessingImage("Processing image...")
        )

        viewModelScope.launch {
            _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                uploadProgress = UploadPhotoProgress.UploadingToDrive(10)
            ) ?: return@launch

            val result = uploadTenantProfilePhotoUseCase(
                propertyId = currentState.tenant.propertyId,
                tenantCloudId = currentState.tenant.cloudId,
                imageUri = uri
            )

            when (result) {
                is PgResult.Success -> {
                    pendingPhotoUploadUri = null
                    _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                        profilePhoto = result.data,
                        isPhotoUploading = false,
                        uploadProgress = UploadPhotoProgress.Success(result.data),
                        needsDriveConsent = false
                    ) ?: return@launch
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Profile photo uploaded to Google Drive."))
                }
                is PgResult.Failure -> {
                    val error = result.error
                    if (error is PgError.ConsentRequiredError) {
                        _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                            isPhotoUploading = false,
                            needsDriveConsent = true,
                            uploadProgress = UploadPhotoProgress.Error("Google Drive permission required.", isAuthError = true)
                        ) ?: return@launch

                        val intent = error.consentIntent ?: googleAuthManager.getDriveConsentIntent()
                        _uiEffect.emit(TenantDetailsUiEffect.LaunchGoogleConsent(intent))
                    } else {
                        _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                            isPhotoUploading = false,
                            uploadProgress = UploadPhotoProgress.Error(error.message)
                        ) ?: return@launch
                        _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Upload failed: ${error.message}"))
                    }
                }
            }
        }
    }

    fun deleteProfilePhoto() {
        val currentState = _uiState.value
        if (currentState !is TenantDetailsUiState.Success) return

        viewModelScope.launch {
            val result = deleteTenantProfilePhotoUseCase(currentState.tenant.cloudId)
            if (result is PgResult.Success) {
                _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                    profilePhoto = null,
                    uploadProgress = UploadPhotoProgress.Idle
                ) ?: return@launch
                _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Profile photo removed."))
            } else if (result is PgResult.Failure) {
                _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Failed to remove photo: ${result.error.message}"))
            }
        }
    }

    fun handleGoogleConsentResult(data: Intent?) {
        viewModelScope.launch {
            val result = googleAuthManager.handleSignInResult(data)
            if (result is PgResult.Success) {
                val info = result.data
                if (info.hasDriveFileScope) {
                    _uiState.value = (_uiState.value as? TenantDetailsUiState.Success)?.copy(
                        needsDriveConsent = false
                    ) ?: return@launch

                    // Automatically download remote photo if waiting
                    val current = _uiState.value as? TenantDetailsUiState.Success
                    if (current != null && current.profilePhoto != null && !current.profilePhoto.hasLocalFile) {
                        downloadPhotoIfMissing(current.tenant.cloudId)
                    }

                    // Automatically retry pending photo upload if exists
                    pendingPhotoUploadUri?.let { uri ->
                        uploadProfilePhoto(uri)
                    }
                } else {
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast("Google Drive permission was not granted."))
                }
            }
        }
    }

    fun getDriveConsentIntent(): Intent {
        return googleAuthManager.getDriveConsentIntent()
    }

    fun updateLeavingDate(leavingDate: String) {
        val currentState = _uiState.value
        if (currentState is TenantDetailsUiState.Success) {
            viewModelScope.launch {
                val result = updateLeavingDateUseCase.execute(currentState.tenant.id, leavingDate)
                if (result is TenantValidationResult.Success) {
                    val msg = if (leavingDate.isBlank()) "Leaving date cleared." else "Leaving date updated to $leavingDate."
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast(msg))
                    loadTenantDetails()
                } else if (result is TenantValidationResult.Error) {
                    _uiEffect.emit(TenantDetailsUiEffect.ShowToast(result.message))
                }
            }
        }
    }

    fun vacateTenant(leavingDate: String? = null) {
        val currentState = _uiState.value
        if (currentState is TenantDetailsUiState.Success) {
            _uiState.value = currentState.copy(isVacating = true)
            viewModelScope.launch {
                val effective = leavingDate ?: currentState.tenant.leavingDate.ifBlank { null }
                val result = vacateTenantUseCase.execute(currentState.tenant.id, effective)
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
    val leavingDate: String = "",
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
    fun onLeavingDateChanged(value: String) = _uiState.update { it.copy(leavingDate = value) }
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
                leavingDate = state.leavingDate,
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
    val leavingDate: String = "",
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
    private val syncTenantDriveFolderUseCase: SyncTenantDriveFolderUseCase,
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
                        leavingDate = tenant.leavingDate,
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
    fun onLeavingDateChanged(value: String) = _uiState.update { it.copy(leavingDate = value) }
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
                leavingDate = state.leavingDate,
                notes = state.notes
            )
            
            when (result) {
                is TenantValidationResult.Success -> {
                    val updated = getTenantUseCase.execute(state.id)
                    if (updated != null && updated.cloudId.isNotBlank()) {
                        syncTenantDriveFolderUseCase(updated.cloudId)
                    }
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is TenantValidationResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}
