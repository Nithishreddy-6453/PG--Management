package com.example.features.tenants.domain.usecase

import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.tenants.domain.repository.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
        val cleanPhone = phone.filter { it.isDigit() }
        if (cleanPhone.isBlank()) {
            return TenantValidationResult.Error("Mobile Number is required.")
        }
        if (cleanPhone.length < 10) {
            return TenantValidationResult.Error("Mobile Number must be at least 10 digits.")
        }
        if (emergencyContact.isNotBlank()) {
            val cleanEmergency = emergencyContact.filter { it.isDigit() }
            if (cleanEmergency.length < 10) {
                return TenantValidationResult.Error("Emergency Contact must be at least 10 digits if provided.")
            }
        }
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
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
        leavingDate: String = "",
        notes: String = ""
    ): TenantValidationResult {
        val trimmedRoom = roomNumber.trim()
        val trimmedBed = bedId.trim()
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()

        val validation = validateTenantUseCase(
            trimmedName, trimmedPhone, emergencyContact, email, trimmedRoom, trimmedBed,
            monthlyRent, securityDeposit, advancePaid
        )
        if (validation is TenantValidationResult.Error) {
            return validation
        }

        val propId = repository.getCurrentPropertyId()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Check if Room exists; auto-create if it doesn't so onboarding is never blocked
        var room = repository.getRoom(trimmedRoom)
        if (room == null) {
            val newRoom = RoomEntity(
                roomNumber = trimmedRoom,
                floor = "1st Floor",
                capacity = maxOf(2, (trimmedBed.lastOrNull()?.let { if (it.isLetter()) (it.uppercaseChar() - 'A' + 1) else 2 } ?: 2)),
                ratePerBed = monthlyRent,
                roomType = "Standard",
                propertyId = propId
            )
            repository.insertRoom(newRoom)
            room = newRoom
        }

        // Check room capacity constraints
        val activeTenants = repository.getTenantsInRoom(trimmedRoom).filter { !it.deleted && it.roomNumber.isNotBlank() && (it.leavingDate.isBlank() || it.leavingDate > todayStr) }
        if (activeTenants.size >= room.capacity) {
            return TenantValidationResult.Error("Room $trimmedRoom is already at full capacity (${room.capacity} beds).")
        }

        // Ensure bed entity exists
        var bed = repository.getBed(trimmedRoom, trimmedBed)
        if (bed == null) {
            val newBed = BedEntity(
                roomNumber = trimmedRoom,
                bedId = trimmedBed,
                status = "AVAILABLE",
                propertyId = propId
            )
            repository.insertBed(newBed)
            bed = newBed
        }

        if (bed.status == "BLOCKED") {
            return TenantValidationResult.Error("$trimmedBed in Room $trimmedRoom is blocked / out of service.")
        }

        // Check duplicate active assignment on bed
        val existingActiveAssignment = repository.getActiveAssignmentForBed(trimmedRoom, trimmedBed)
        if (existingActiveAssignment != null) {
            return TenantValidationResult.Error("$trimmedBed in Room $trimmedRoom is already occupied.")
        }

        // Check for duplicate phone against active tenants
        val cleanPhone = trimmedPhone.filter { it.isDigit() }
        val allTenants = repository.getAllTenantsFlow().first()
        if (allTenants.any { !it.deleted && it.roomNumber.isNotBlank() && it.phone.filter { p -> p.isDigit() } == cleanPhone }) {
            return TenantValidationResult.Error("A tenant with phone number $trimmedPhone is already registered.")
        }

        // Insert tenant
        val isKyc = kycDocType != "None" && kycDocType.isNotBlank()
        val tenant = TenantEntity(
            name = trimmedName,
            phone = trimmedPhone,
            email = email.trim(),
            emergencyContact = emergencyContact.trim(),
            roomNumber = trimmedRoom,
            bedId = trimmedBed,
            monthlyRent = monthlyRent,
            securityDeposit = securityDeposit,
            moveInDate = moveInDate.ifBlank { todayStr },
            isKycUploaded = isKyc,
            kycDocType = kycDocType,
            alternateContact = alternateContact.trim(),
            dob = dob.trim(),
            gender = gender,
            address = address.trim(),
            occupation = occupation.trim(),
            companyOrCollege = companyOrCollege.trim(),
            advancePaid = advancePaid,
            leavingDate = leavingDate.trim(),
            notes = notes.trim(),
            propertyId = propId
        )
        val newId = repository.insertTenant(tenant).toInt()

        // Insert BedAssignmentEntity
        val assignment = BedAssignmentEntity(
            assignmentId = UUID.randomUUID().toString(),
            tenantId = newId,
            roomNumber = trimmedRoom,
            bedId = trimmedBed,
            startDate = moveInDate.ifBlank { todayStr },
            endDate = if (leavingDate.isNotBlank()) leavingDate.trim() else null,
            agreedRent = monthlyRent,
            propertyId = propId
        )
        repository.insertBedAssignment(assignment)

        // Update Bed status to OCCUPIED
        repository.updateBed(bed.copy(status = "OCCUPIED"))

        // Auto-create initial rent payment using RentBillingEngine for accurate calendar proration
        try {
            val billingMonth = RentBillingEngine.formatCanonicalBillingMonth(Date())
            val proration = RentBillingEngine.calculateProrationDetails(
                moveInDateStr = moveInDate.ifBlank { todayStr },
                leavingDateStr = leavingDate,
                billingMonthStr = billingMonth
            )
            if (proration.applicableDays > 0) {
                val expectedRent = RentBillingEngine.calculateExpectedRent(
                    monthlyRent = monthlyRent,
                    applicableDays = proration.applicableDays,
                    daysInMonth = proration.daysInMonth
                )
                val dueDateStr = if (moveInDate.isNotBlank()) moveInDate else todayStr
                val cloudId = RentBillingEngine.generateDeterministicCloudId(
                    propertyId = propId,
                    tenantId = newId,
                    billingMonth = billingMonth
                )
                repository.insertRentPayment(
                    RentPaymentEntity(
                        cloudId = cloudId,
                        tenantId = newId,
                        tenantName = trimmedName,
                        roomNumber = trimmedRoom,
                        billingMonth = billingMonth,
                        amount = expectedRent,
                        amountPaid = 0.0,
                        dueDate = dueDateStr,
                        paymentDate = null,
                        paymentMode = null,
                        status = "Pending",
                        propertyId = propId
                    )
                )
            }
        } catch (_: Exception) {
            // Non-critical: Do not fail tenant creation if initial ledger item creation encounters error
        }

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
        leavingDate: String = "",
        notes: String
    ): TenantValidationResult {
        val trimmedRoom = roomNumber.trim()
        val trimmedBed = bedId.trim()
        val validation = validateTenantUseCase(
            name, phone, emergencyContact, email, trimmedRoom, trimmedBed,
            monthlyRent, securityDeposit, advancePaid
        )
        if (validation is TenantValidationResult.Error) {
            return validation
        }

        val existingTenant = repository.getTenantById(id)
            ?: return TenantValidationResult.Error("Tenant does not exist.")

        val propId = if (existingTenant.propertyId.isNotBlank() && existingTenant.propertyId != "property_default") {
            existingTenant.propertyId
        } else {
            repository.getCurrentPropertyId()
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // If Room or Bed changed, perform checks and reconcile assignments
        if (existingTenant.roomNumber != trimmedRoom || existingTenant.bedId != trimmedBed) {
            val room = repository.getRoom(trimmedRoom)
                ?: return TenantValidationResult.Error("Room $trimmedRoom does not exist.")

            val activeTenants = repository.getTenantsInRoom(trimmedRoom).filter {
                it.id != id && !it.deleted && it.roomNumber.isNotBlank() && (it.leavingDate.isBlank() || it.leavingDate > todayStr)
            }
            if (existingTenant.roomNumber != trimmedRoom && activeTenants.size >= room.capacity) {
                return TenantValidationResult.Error("Target Room $trimmedRoom is at full capacity (${room.capacity} beds).")
            }

            var destBed = repository.getBed(trimmedRoom, trimmedBed)
            if (destBed == null) {
                val newBed = BedEntity(
                    roomNumber = trimmedRoom,
                    bedId = trimmedBed,
                    status = "AVAILABLE",
                    propertyId = propId
                )
                repository.insertBed(newBed)
                destBed = newBed
            }
            if (destBed.status == "BLOCKED") {
                return TenantValidationResult.Error("$trimmedBed in Room $trimmedRoom is blocked / out of service.")
            }

            val existingBedAssign = repository.getActiveAssignmentForBed(trimmedRoom, trimmedBed)
            if (existingBedAssign != null && existingBedAssign.tenantId != id) {
                return TenantValidationResult.Error("$trimmedBed in Room $trimmedRoom is already occupied.")
            }

            // End old assignment
            val currentAssignment = repository.getActiveAssignmentForTenant(id)
            if (currentAssignment != null) {
                repository.updateBedAssignment(currentAssignment.copy(endDate = todayStr))
                val oldBed = repository.getBed(currentAssignment.roomNumber, currentAssignment.bedId)
                if (oldBed != null && (oldBed.roomNumber != trimmedRoom || oldBed.bedId != trimmedBed)) {
                    repository.updateBed(oldBed.copy(status = "AVAILABLE"))
                }
            }

            // Create new assignment
            val newAssignment = BedAssignmentEntity(
                assignmentId = UUID.randomUUID().toString(),
                tenantId = id,
                roomNumber = trimmedRoom,
                bedId = trimmedBed,
                startDate = todayStr,
                endDate = if (leavingDate.isNotBlank()) leavingDate.trim() else null,
                agreedRent = monthlyRent,
                propertyId = propId
            )
            repository.insertBedAssignment(newAssignment)
            repository.updateBed(destBed.copy(status = "OCCUPIED"))
        }

        val isKyc = kycDocType != "None" && kycDocType.isNotBlank()
        val updatedTenant = existingTenant.copy(
            name = name.trim(),
            phone = phone.trim(),
            email = email.trim(),
            emergencyContact = emergencyContact.trim(),
            roomNumber = trimmedRoom,
            bedId = trimmedBed,
            monthlyRent = monthlyRent,
            securityDeposit = securityDeposit,
            moveInDate = moveInDate,
            isKycUploaded = isKyc,
            kycDocType = kycDocType,
            alternateContact = alternateContact.trim(),
            dob = dob.trim(),
            gender = gender,
            address = address.trim(),
            occupation = occupation.trim(),
            companyOrCollege = companyOrCollege.trim(),
            advancePaid = advancePaid,
            leavingDate = leavingDate.trim(),
            notes = notes.trim()
        )
        repository.updateTenant(updatedTenant)
        return TenantValidationResult.Success
    }
}

class UpdateLeavingDateUseCase @Inject constructor(
    private val repository: TenantRepository
) {
    suspend fun execute(id: Int, leavingDate: String): TenantValidationResult {
        val tenant = repository.getTenantById(id)
            ?: return TenantValidationResult.Error("Tenant not found.")
        val updated = tenant.copy(leavingDate = leavingDate.trim())
        repository.updateTenant(updated)
        return TenantValidationResult.Success
    }
}

class DeleteTenantUseCase @Inject constructor(
    private val repository: TenantRepository,
    private val roomRepository: RoomRepository
) {
    suspend fun execute(id: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        roomRepository.vacateTenant(id, todayStr)
        repository.deleteTenant(id)
    }
}

class VacateTenantUseCase @Inject constructor(
    private val repository: TenantRepository,
    private val roomRepository: RoomRepository
) {
    suspend fun execute(id: Int, leavingDate: String? = null, currentDateOverride: String? = null): TenantValidationResult {
        val tenant = repository.getTenantById(id)
            ?: return TenantValidationResult.Error("Tenant not found.")
        
        val dateToUse = if (!leavingDate.isNullOrBlank()) leavingDate.trim()
            else if (tenant.leavingDate.isNotBlank()) tenant.leavingDate.trim()
            else currentDateOverride ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val result = roomRepository.vacateTenant(id, dateToUse, currentDateOverride)
        return when (result) {
            is RoomValidationResult.Success -> TenantValidationResult.Success
            is RoomValidationResult.Error -> TenantValidationResult.Error(result.message)
        }
    }
}

class AssignRoomUseCase @Inject constructor(
    private val repository: TenantRepository,
    private val roomRepository: RoomRepository
) {
    suspend fun execute(tenantId: Int, roomNumber: String, bedId: String): TenantValidationResult {
        val tenant = repository.getTenantById(tenantId)
            ?: return TenantValidationResult.Error("Tenant not found.")

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val result = roomRepository.assignTenantToBed(
            roomNumber = roomNumber.trim(),
            bedId = bedId.trim(),
            tenantId = tenantId,
            startDate = tenant.moveInDate.ifBlank { todayStr },
            agreedRent = tenant.monthlyRent
        )
        return when (result) {
            is RoomValidationResult.Success -> TenantValidationResult.Success
            is RoomValidationResult.Error -> TenantValidationResult.Error(result.message)
        }
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

        // 1. Query Search (Name, Phone, Room, Bed)
        if (query.isNotBlank()) {
            val q = query.trim()
            result = result.filter {
                it.name.contains(q, ignoreCase = true) ||
                        it.phone.contains(q, ignoreCase = true) ||
                        it.roomNumber.contains(q, ignoreCase = true) ||
                        it.bedId.contains(q, ignoreCase = true)
            }
        }

        // 2. Room Filter
        if (roomFilter != "All" && roomFilter.isNotBlank()) {
            result = result.filter { it.roomNumber == roomFilter }
        }

        // 3. Occupancy Filter
        when (occupancyFilter) {
            "Active" -> {
                result = result.filter { it.roomNumber.isNotBlank() && !it.deleted }
            }
            "Leaving Soon", "Leaving" -> {
                result = result.filter { it.roomNumber.isNotBlank() && !it.deleted && it.leavingDate.isNotBlank() }
            }
            "Vacated" -> {
                result = result.filter { it.roomNumber.isBlank() || it.deleted }
            }
            "New" -> {
                result = result.filter {
                    it.roomNumber.isNotBlank() && !it.deleted && isRecentMoveIn(it.moveInDate)
                }
            }
        }

        // 4. Sorting
        result = when (sortBy) {
            "Name", "Alphabetical" -> result.sortedBy { it.name.lowercase() }
            "Move-in Date", "Joined Date" -> result.sortedByDescending { it.moveInDate }
            "Room" -> result.sortedWith(compareBy({ it.roomNumber }, { it.bedId }))
            "Leaving Date" -> result.sortedBy { if (it.leavingDate.isBlank()) "9999-99-99" else it.leavingDate }
            else -> result.sortedBy { it.name.lowercase() }
        }

        return result
    }

    private fun isRecentMoveIn(moveInDate: String?): Boolean {
        if (moveInDate.isNullOrBlank()) return false
        val d = com.example.core.util.PgDateUtil.parseDate(moveInDate) ?: return false
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -30)
        return d.after(cal.time)
    }
}
