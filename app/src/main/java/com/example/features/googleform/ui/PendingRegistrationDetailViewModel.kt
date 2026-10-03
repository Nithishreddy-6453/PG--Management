package com.example.features.googleform.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.googleform.domain.model.PendingTenantRegistration
import com.example.features.googleform.domain.usecase.AcceptPendingTenantUseCase
import com.example.features.googleform.domain.usecase.GetPendingRegistrationDetailsUseCase
import com.example.features.googleform.domain.usecase.RejectPendingTenantUseCase
import com.example.features.googleform.domain.usecase.UpdatePendingRegistrationUseCase
import com.example.features.tenants.domain.repository.TenantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PendingRegistrationDetailUiState(
    val registration: PendingTenantRegistration? = null,
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    
    // Editable fields
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val emergencyRelation: String = "",
    val permanentAddress: String = "",
    val currentAddress: String = "",
    val occupation: String = "",
    val organization: String = "",
    val expectedJoiningDate: String = "",
    val notes: String = "",

    // Onboarding Assignment fields
    val showAssignDialog: Boolean = false,
    val selectedRoomNumber: String = "",
    val selectedBedId: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val moveInDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val gender: String = "Male",
    val kycDocType: String = "Aadhaar Card",
    val assignmentNotes: String = "",

    // Rooms & Beds
    val rooms: List<RoomEntity> = emptyList(),
    val availableBeds: List<String> = emptyList(),

    val isSubmitting: Boolean = false,
    val isAcceptedSuccessfully: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PendingRegistrationDetailViewModel @Inject constructor(
    private val getDetailsUseCase: GetPendingRegistrationDetailsUseCase,
    private val updateRegistrationUseCase: UpdatePendingRegistrationUseCase,
    private val acceptPendingTenantUseCase: AcceptPendingTenantUseCase,
    private val rejectPendingTenantUseCase: RejectPendingTenantUseCase,
    private val tenantRepository: TenantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val registrationId: String = savedStateHandle.get<String>("registrationId") ?: ""

    private val _uiState = MutableStateFlow(PendingRegistrationDetailUiState(isLoading = true))
    val uiState: StateFlow<PendingRegistrationDetailUiState> = _uiState.asStateFlow()

    init {
        loadRegistration()
        loadRooms()
    }

    fun loadRegistration() {
        if (registrationId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Registration ID is missing") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = getDetailsUseCase(registrationId)
            when (result) {
                is PgResult.Success -> {
                    val reg = result.data
                    if (reg != null) {
                        val defaultMoveIn = if (reg.expectedJoiningDate.isNotBlank()) {
                            reg.expectedJoiningDate
                        } else {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        }
                        _uiState.update {
                            it.copy(
                                registration = reg,
                                isLoading = false,
                                fullName = reg.fullName,
                                phone = reg.phone,
                                email = reg.email,
                                emergencyName = reg.emergencyName,
                                emergencyPhone = reg.emergencyPhone,
                                emergencyRelation = reg.emergencyRelation,
                                permanentAddress = reg.permanentAddress,
                                currentAddress = reg.currentAddress,
                                occupation = reg.occupation,
                                organization = reg.organization,
                                expectedJoiningDate = reg.expectedJoiningDate,
                                notes = reg.notes,
                                moveInDate = defaultMoveIn
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Registration not found") }
                    }
                }
                is PgResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
                }
            }
        }
    }

    private fun loadRooms() {
        viewModelScope.launch {
            val roomsList = tenantRepository.getAllRooms()
            _uiState.update { it.copy(rooms = roomsList) }
            if (roomsList.isNotEmpty() && _uiState.value.selectedRoomNumber.isBlank()) {
                onRoomSelected(roomsList.first().roomNumber)
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun onFullNameChanged(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onEmergencyNameChanged(value: String) = _uiState.update { it.copy(emergencyName = value) }
    fun onEmergencyPhoneChanged(value: String) = _uiState.update { it.copy(emergencyPhone = value) }
    fun onEmergencyRelationChanged(value: String) = _uiState.update { it.copy(emergencyRelation = value) }
    fun onPermanentAddressChanged(value: String) = _uiState.update { it.copy(permanentAddress = value) }
    fun onCurrentAddressChanged(value: String) = _uiState.update { it.copy(currentAddress = value) }
    fun onOccupationChanged(value: String) = _uiState.update { it.copy(occupation = value) }
    fun onOrganizationChanged(value: String) = _uiState.update { it.copy(organization = value) }
    fun onExpectedJoiningDateChanged(value: String) = _uiState.update { it.copy(expectedJoiningDate = value) }
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value) }

    fun saveEdits() {
        val currentReg = _uiState.value.registration ?: return
        val state = _uiState.value

        val updated = currentReg.copy(
            fullName = state.fullName,
            phone = state.phone,
            email = state.email,
            emergencyName = state.emergencyName,
            emergencyPhone = state.emergencyPhone,
            emergencyRelation = state.emergencyRelation,
            permanentAddress = state.permanentAddress,
            currentAddress = state.currentAddress,
            occupation = state.occupation,
            organization = state.organization,
            expectedJoiningDate = state.expectedJoiningDate,
            notes = state.notes
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = updateRegistrationUseCase(updated)
            _uiState.update { it.copy(isSubmitting = false) }

            when (result) {
                is PgResult.Success -> {
                    _uiState.update {
                        it.copy(
                            registration = updated,
                            isEditMode = false,
                            actionMessage = "Registration details updated successfully"
                        )
                    }
                }
                is PgResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message) }
                }
            }
        }
    }

    fun openAssignDialog() {
        val state = _uiState.value
        if (state.rooms.isNotEmpty() && state.selectedRoomNumber.isBlank()) {
            onRoomSelected(state.rooms.first().roomNumber)
        }
        _uiState.update { it.copy(showAssignDialog = true) }
    }

    fun closeAssignDialog() {
        _uiState.update { it.copy(showAssignDialog = false) }
    }

    fun onRoomSelected(roomNo: String) {
        val trimmed = roomNo.trim()
        viewModelScope.launch {
            val room = _uiState.value.rooms.find { it.roomNumber.equals(trimmed, ignoreCase = true) }
                ?: tenantRepository.getRoom(trimmed)
            if (room != null) {
                val activeTenants = tenantRepository.getTenantsInRoom(room.roomNumber).filter { !it.deleted && it.roomNumber.isNotBlank() }
                val occupiedBeds = activeTenants.map { it.bedId.trim().lowercase() }
                
                val beds = (1..maxOf(room.capacity, 1)).map { "Bed ${(64 + it).toChar()}" }
                val available = beds.filter { !occupiedBeds.contains(it.trim().lowercase()) }
                val finalAvailable = if (available.isNotEmpty()) available else listOf("Bed A", "Bed B")

                _uiState.update {
                    it.copy(
                        selectedRoomNumber = room.roomNumber,
                        selectedBedId = if (available.isNotEmpty()) available.first() else "Bed A",
                        availableBeds = finalAvailable,
                        monthlyRent = if (it.monthlyRent.isBlank() && room.ratePerBed > 0) room.ratePerBed.toString() else it.monthlyRent,
                        securityDeposit = if (it.securityDeposit.isBlank() && room.ratePerBed > 0) room.ratePerBed.toString() else it.securityDeposit
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        selectedRoomNumber = trimmed,
                        availableBeds = listOf("Bed A", "Bed B", "Bed C"),
                        selectedBedId = if (it.selectedBedId.isBlank()) "Bed A" else it.selectedBedId
                    )
                }
            }
        }
    }

    fun onBedSelected(bed: String) = _uiState.update { it.copy(selectedBedId = bed) }
    fun onMonthlyRentChanged(rent: String) = _uiState.update { it.copy(monthlyRent = rent) }
    fun onSecurityDepositChanged(deposit: String) = _uiState.update { it.copy(securityDeposit = deposit) }
    fun onMoveInDateChanged(date: String) = _uiState.update { it.copy(moveInDate = date) }
    fun onGenderChanged(gender: String) = _uiState.update { it.copy(gender = gender) }
    fun onKycDocTypeChanged(type: String) = _uiState.update { it.copy(kycDocType = type) }
    fun onAssignmentNotesChanged(notes: String) = _uiState.update { it.copy(assignmentNotes = notes) }

    fun acceptRegistration() {
        val state = _uiState.value
        val reg = state.registration ?: return

        if (state.selectedRoomNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select a room.") }
            return
        }

        if (state.selectedBedId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select a bed.") }
            return
        }

        val rentVal = state.monthlyRent.toDoubleOrNull() ?: 0.0
        val depositVal = state.securityDeposit.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = acceptPendingTenantUseCase(
                cloudId = reg.cloudId,
                roomNumber = state.selectedRoomNumber,
                bedId = state.selectedBedId,
                monthlyRent = rentVal,
                securityDeposit = depositVal,
                moveInDate = state.moveInDate,
                gender = state.gender,
                kycDocType = state.kycDocType,
                notes = state.assignmentNotes
            )

            _uiState.update { it.copy(isSubmitting = false) }

            when (result) {
                is PgResult.Success -> {
                    _uiState.update {
                        it.copy(
                            showAssignDialog = false,
                            isAcceptedSuccessfully = true,
                            actionMessage = "Tenant ${reg.fullName} onboarded and assigned to Room ${state.selectedRoomNumber} (${state.selectedBedId}) successfully!"
                        )
                    }
                    loadRegistration()
                }
                is PgResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message) }
                }
            }
        }
    }

    fun rejectRegistration(reason: String) {
        val reg = _uiState.value.registration ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = rejectPendingTenantUseCase(reg.cloudId, reason.ifBlank { "Rejected by owner" })
            _uiState.update { it.copy(isSubmitting = false) }

            when (result) {
                is PgResult.Success -> {
                    _uiState.update {
                        it.copy(
                            actionMessage = "Registration rejected."
                        )
                    }
                    loadRegistration()
                }
                is PgResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message) }
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }
}
