package com.example.features.rooms.domain.model

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.BedEntity
import com.example.data.database.BedAssignmentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoomSummary(
    val room: RoomEntity,
    val tenants: List<TenantEntity> = emptyList(),
    val beds: List<BedEntity> = emptyList(),
    val assignments: List<BedAssignmentEntity> = emptyList(),
    val currentDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
) {
    val roomNumber: String get() = room.roomNumber
    val floor: String get() = room.floor
    val totalBeds: Int get() = if (beds.isNotEmpty()) beds.size else room.capacity
    val ratePerBed: Double get() = room.ratePerBed
    val roomType: String get() = room.roomType
    val notes: String get() = room.notes
    val isActive: Boolean get() = room.isActive

    val blockedBeds: Int get() = beds.count { it.status == "BLOCKED" }
    val usableBeds: Int get() = (totalBeds - blockedBeds).coerceAtLeast(0)

    val activeAssignments: List<BedAssignmentEntity> get() = assignments.filter {
        !it.deleted && (it.endDate == null || it.endDate > currentDateStr)
    }

    val activeTenants: List<TenantEntity> get() {
        val activeTenantIds = activeAssignments.map { it.tenantId }.toSet()
        return tenants.filter {
            !it.deleted && (it.id in activeTenantIds || (it.roomNumber == room.roomNumber && (it.leavingDate.isBlank() || it.leavingDate > currentDateStr)))
        }
    }
    
    val leavingTenants: List<TenantEntity> get() = activeTenants.filter { it.leavingDate.isNotBlank() }
    val hasUpcomingVacancy: Boolean get() = leavingTenants.isNotEmpty()
    
    val occupiedBeds: Int get() = activeAssignments.size.coerceAtMost(usableBeds)
    val availableBeds: Int get() = (usableBeds - occupiedBeds).coerceAtLeast(0)
    
    val activeTenantCount: Int get() = activeTenants.size

    val occupancyStatus: String get() = when {
        occupiedBeds == 0 -> "Empty"
        availableBeds == 0 && usableBeds > 0 -> "Full"
        else -> "Partially Occupied"
    }
    
    val occupancyPercentage: Double get() = if (usableBeds > 0) {
        (occupiedBeds.toDouble() / usableBeds.toDouble()) * 100.0
    } else 0.0

    val vacancyPercentage: Double get() = if (usableBeds > 0) {
        (availableBeds.toDouble() / usableBeds.toDouble()) * 100.0
    } else 0.0
}

sealed interface RoomValidationResult {
    object Success : RoomValidationResult
    data class Error(val message: String) : RoomValidationResult
}
