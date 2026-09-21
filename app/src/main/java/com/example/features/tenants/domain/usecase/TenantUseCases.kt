package com.example.features.tenants.domain.usecase

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.tenants.domain.repository.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class TenantValidationResult {
    object Success : TenantValidationResult()
    data class Error(val message: String) : TenantValidationResult()
}

class ValidateTenantUseCase @Inject constructor() {
    operator fun invoke(
        name: String,
        phone: String,
        emergencyContact: String,
        email: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        advancePaid: Double
    ): TenantValidationResult {
        if (name.isBlank()) {
            return TenantValidationResult.Error("Full Name is required.")
        }
        if (phone.isBlank()) {
            return TenantValidationResult.Error("Mobile Number is required.")
        }
        if (phone.length < 10) {
            return TenantValidationResult.Error("Mobile Number must be at least 10 digits.")
        }
        if (emergencyContact.isBlank()) {
            return TenantValidationResult.Error("Emergency Contact is required.")
        }
        if (emergencyContact.length < 10) {
            return TenantValidationResult.Error("Emergency Contact must be at least 10 digits.")
        }
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return TenantValidationResult.Error("Invalid Email format.")
        }
        if (roomNumber.isBlank()) {
            return TenantValidationResult.Error("Please assign a Room.")
        }
        if (bedId.isBlank()) {
            return TenantValidationResult.Error("Please assign a Bed.")
        }
        if (monthlyRent < 0) {
            return TenantValidationResult.Error("Monthly Rent cannot be negative.")
        }
        if (securityDeposit < 0) {
            return TenantValidationResult.Error("Security Deposit cannot be negative.")
        }
        if (advancePaid < 0) {
            return TenantValidationResult.Error("Advance Paid cannot be negative.")
        }
        return TenantValidationResult.Success
    }
}

class AddTenantUseCase @Inject constructor(
    private val repository: TenantRepository,
    private val validateTenantUseCase: ValidateTenantUseCase
) {
    suspend fun execute(
        name: String,
        phone: String,
        email: String,
        emergencyContact: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        advancePaid: Double,
        moveInDate: String,
        kycDocType: String,
        alternateContact: String = "",
        dob: String = "",
        gender: String = "",
        address: String = "",
        occupation: String = "",
        companyOrCollege: String = "",
        notes: String = ""
    ): TenantValidationResult {
        val validation = validateTenantUseCase(
            name, phone, emergencyContact, email, roomNumber, bedId,
            monthlyRent, securityDeposit, advancePaid
        )
        if (validation is TenantValidationResult.Error) {
            return validation
        }

        // Check if Room exists
        val room = repository.getRoom(roomNumber)
            ?: return TenantValidationResult.Error("Assigned Room $roomNumber does not exist.")

        // Check room capacity constraints
        val activeTenants = repository.getTenantsInRoom(roomNumber)
        if (activeTenants.size >= room.capacity) {
            return TenantValidationResult.Error("Room $roomNumber is already at full capacity (${room.capacity} beds).")
        }

        // Check duplicate bed assignment
        val isBedOccupied = activeTenants.any { it.bedId.equals(bedId, ignoreCase = true) }
        if (isBedOccupied) {
            return TenantValidationResult.Error("$bedId in Room $roomNumber is already occupied.")
        }

        // Check for duplicate phone
        val allTenants = repository.getAllTenantsFlow().first()
        if (allTenants.any { it.phone == phone && !it.roomNumber.isBlank() }) {
            return TenantValidationResult.Error("A tenant with phone number $phone is already registered.")
        }

        // Insert tenant
        val isKyc = kycDocType != "None" && kycDocType.isNotBlank()
        val tenant = TenantEntity(
            name = name,
            phone = phone,
            email = email,
            emergencyContact = emergencyContact,
            roomNumber = roomNumber,
            bedId = bedId,
            monthlyRent = monthlyRent,
            securityDeposit = securityDeposit,
            moveInDate = moveInDate,
            isKycUploaded = isKyc,
            kycDocType = kycDocType,
            alternateContact = alternateContact,
            dob = dob,
            gender = gender,
            address = address,
            occupation = occupation,
            companyOrCollege = companyOrCollege,
            advancePaid = advancePaid,
            notes = notes
        )
        val newId = repository.insertTenant(tenant)

        // Auto-create initial rent payment
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        repository.insertRentPayment(
            RentPaymentEntity(
                tenantId = newId.toInt(),
                tenantName = name,
                roomNumber = roomNumber,
                billingMonth = currentMonth,
                amount = monthlyRent,
                dueDate = currentDate,
                paymentDate = null,
                paymentMode = null,
                status = "Pending"
            )
        )

        return TenantValidationResult.Success
    }
}

class UpdateTenantUseCase @Inject constructor(
    private val repository: TenantRepository,
    private val validateTenantUseCase: ValidateTenantUseCase
) {
    suspend fun execute(
        id: Int,
        name: String,
        phone: String,
        email: String,
        emergencyContact: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        advancePaid: Double,
        moveInDate: String,
        kycDocType: String,
        alternateContact: String,
        dob: String,
        gender: String,
        address: String,
        occupation: String,
        companyOrCollege: String,
        notes: String
    ): TenantValidationResult {
        val validation = validateTenantUseCase(
            name, phone, emergencyContact, email, roomNumber, bedId,
            monthlyRent, securityDeposit, advancePaid
        )
        if (validation is TenantValidationResult.Error) {
            return validation
        }

        val existingTenant = repository.getTenantById(id)
            ?: return TenantValidationResult.Error("Tenant does not exist.")

        // If Room or Bed changed, perform checks
        if (existingTenant.roomNumber != roomNumber || existingTenant.bedId != bedId) {
            val room = repository.getRoom(roomNumber)
                ?: return TenantValidationResult.Error("Room $roomNumber does not exist.")

            val activeTenants = repository.getTenantsInRoom(roomNumber).filter { it.id != id }
            if (existingTenant.roomNumber != roomNumber && activeTenants.size >= room.capacity) {
                return TenantValidationResult.Error("Target Room $roomNumber is at full capacity (${room.capacity} beds).")
            }

            val isBedOccupied = activeTenants.any { it.bedId.equals(bedId, ignoreCase = true) }
            if (isBedOccupied) {
                return TenantValidationResult.Error("$bedId in Room $roomNumber is already occupied.")
            }
        }

        val isKyc = kycDocType != "None" && kycDocType.isNotBlank()
        val updatedTenant = existingTenant.copy(
            name = name,
            phone = phone,
            email = email,
            emergencyContact = emergencyContact,
            roomNumber = roomNumber,
            bedId = bedId,
            monthlyRent = monthlyRent,
            securityDeposit = securityDeposit,
            moveInDate = moveInDate,
            isKycUploaded = isKyc,
            kycDocType = kycDocType,
            alternateContact = alternateContact,
            dob = dob,
            gender = gender,
            address = address,
            occupation = occupation,
            companyOrCollege = companyOrCollege,
            advancePaid = advancePaid,
            notes = notes
        )
        repository.updateTenant(updatedTenant)
        return TenantValidationResult.Success
    }
}

class DeleteTenantUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    suspend fun execute(id: Int) {
        repository.deleteTenant(id)
    }
}

class VacateTenantUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    suspend fun execute(id: Int): TenantValidationResult {
        val tenant = repository.getTenantById(id)
            ?: return TenantValidationResult.Error("Tenant not found.")
        
        // Vacate means setting room number and bed assignment to empty, and adding to notes
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val vacatedNotes = "${tenant.notes}\n[Vacated from Room ${tenant.roomNumber} Bed ${tenant.bedId} on $currentDate]".trim()
        
        val vacatedTenant = tenant.copy(
            roomNumber = "",
            bedId = "",
            notes = vacatedNotes
        )
        
        repository.updateTenant(vacatedTenant)
        return TenantValidationResult.Success
    }
}

class AssignRoomUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    suspend fun execute(tenantId: Int, roomNumber: String, bedId: String): TenantValidationResult {
        val tenant = repository.getTenantById(tenantId)
            ?: return TenantValidationResult.Error("Tenant not found.")

        val room = repository.getRoom(roomNumber)
            ?: return TenantValidationResult.Error("Room $roomNumber does not exist.")

        val activeTenants = repository.getTenantsInRoom(roomNumber).filter { it.id != tenantId }
        if (activeTenants.size >= room.capacity) {
            return TenantValidationResult.Error("Room $roomNumber is at full capacity.")
        }

        if (activeTenants.any { it.bedId.equals(bedId, ignoreCase = true) }) {
            return TenantValidationResult.Error("Bed $bedId is already occupied.")
        }

        val updatedTenant = tenant.copy(
            roomNumber = roomNumber,
            bedId = bedId
        )
        repository.updateTenant(updatedTenant)
        return TenantValidationResult.Success
    }
}

class GetTenantUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    suspend fun execute(id: Int): TenantEntity? {
        return repository.getTenantById(id)
    }
}

class GetTenantsUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    fun execute(): Flow<List<TenantEntity>> {
        return repository.getAllTenantsFlow()
    }
}

class SearchTenantUseCase @Inject constructor() {
    operator fun invoke(
        tenants: List<TenantEntity>,
        query: String,
        roomFilter: String,
        occupancyFilter: String,
        sortBy: String
    ): List<TenantEntity> {
        var result = tenants

        // 1. Query Search (Name or Phone)
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.phone.contains(query)
            }
        }

        // 2. Room Filter
        if (roomFilter != "All" && roomFilter.isNotBlank()) {
            result = result.filter { it.roomNumber == roomFilter }
        }

        // 3. Occupancy Filter
        if (occupancyFilter == "Active") {
            result = result.filter { !it.roomNumber.isBlank() }
        } else if (occupancyFilter == "Vacated") {
            result = result.filter { it.roomNumber.isBlank() }
        }

        // 4. Sorting
        result = when (sortBy) {
            "Alphabetical" -> result.sortedBy { it.name.lowercase() }
            "Move-in Date" -> result.sortedByDescending { it.moveInDate }
            else -> result
        }

        return result
    }
}
