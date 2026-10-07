package com.example.features.rooms.domain.model

import com.example.core.util.PgDateUtil
import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity

data class RoomSummary(
    val room: RoomEntity,
    val tenants: List<TenantEntity> = emptyList(),
    val beds: List<BedEntity> = emptyList(),
    val assignments: List<BedAssignmentEntity> = emptyList(),
    val currentDateStr: String = PgDateUtil.todayIso()
) {
    val roomNumber: String get() = room.roomNumber
    val floor: String get() = room.floor

    // Canonical room capacity is strictly determined by room.capacity (never inflated by beds.size)
    val totalBeds: Int get() = room.capacity
    val ratePerBed: Double get() = room.ratePerBed
    val roomType: String get() = room.roomType
    val notes: String get() = room.notes
    val isActive: Boolean get() = room.isActive

    // Non-deleted physical beds belonging to this room
    val roomBeds: List<BedEntity> get() = beds.filter { !it.deleted && it.roomNumber == room.roomNumber }

    // Blocked beds count cannot exceed room capacity
    val blockedBeds: Int get() = roomBeds.count { it.status == "BLOCKED" }.coerceAtMost(totalBeds)
    val usableBeds: Int get() = (totalBeds - blockedBeds).coerceAtLeast(0)

    // Authoritative active assignments covering today on usable (non-blocked) beds
    val activeAssignments: List<BedAssignmentEntity> get() {
        val blockedBedIds = roomBeds.filter { it.status == "BLOCKED" }.map { it.bedId.lowercase() }.toSet()
        val tenantMap = tenants.associateBy { it.id }

        return assignments.filter { assign ->
            if (assign.deleted || assign.roomNumber != room.roomNumber) return@filter false
            if (blockedBedIds.contains(assign.bedId.lowercase())) return@filter false

            // Start date must be started on or before today
            if (assign.startDate.isNotBlank() && PgDateUtil.isDateFuture(assign.startDate, currentDateStr)) {
                return@filter false
            }

            // End date must be null or strictly in the future
            if (!assign.endDate.isNullOrBlank() && PgDateUtil.isDatePastOrToday(assign.endDate, currentDateStr)) {
                return@filter false
            }

            // Associated tenant must not be deleted or vacated on or before today
            val tenant = tenantMap[assign.tenantId]
            if (tenant != null) {
                if (tenant.deleted) return@filter false
                if (tenant.roomNumber.isNotBlank() && tenant.roomNumber != room.roomNumber) return@filter false
                if (tenant.leavingDate.isNotBlank() && PgDateUtil.isDatePastOrToday(tenant.leavingDate, currentDateStr)) {
                    return@filter false
                }
            } else {
                return@filter false
            }
            true
        }
        .distinctBy { it.bedId.lowercase() } // Max 1 active assignment per physical bed
        .take(usableBeds) // Cannot exceed canonical usable beds
    }

    // Active tenants strictly correspond to the valid active assignments
    val activeTenants: List<TenantEntity> get() {
        val activeTenantIds = activeAssignments.map { it.tenantId }.toSet()
        return tenants.filter { it.id in activeTenantIds }
    }

    // Only future leaving dates count as upcoming vacancies
    val leavingTenants: List<TenantEntity> get() = activeTenants.filter {
        it.leavingDate.isNotBlank() && PgDateUtil.isDateFuture(it.leavingDate, currentDateStr)
    }

    val hasUpcomingVacancy: Boolean get() = leavingTenants.isNotEmpty()

    val occupiedBeds: Int get() = activeAssignments.size
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
