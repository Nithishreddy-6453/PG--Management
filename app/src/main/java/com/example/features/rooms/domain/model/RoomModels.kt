package com.example.features.rooms.domain.model

import com.example.core.util.PgDateUtil
import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity

fun isSameRoomNumber(room1: String?, room2: String?): Boolean {
    if (room1.isNullOrBlank() || room2.isNullOrBlank()) return false
    val r1 = room1.trim().removePrefix("Room ").removePrefix("room ").trim()
    val r2 = room2.trim().removePrefix("Room ").removePrefix("room ").trim()
    return r1.equals(r2, ignoreCase = true)
}

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
    val sharingType: String get() {
        val notesOrType = notes.ifBlank { roomType }
        if (notesOrType.contains("Sharing", ignoreCase = true)) {
            return notesOrType
        }
        return when (totalBeds) {
            1 -> "Single Sharing"
            2 -> "Double Sharing"
            3 -> "Triple Sharing"
            4 -> "Four Sharing"
            else -> if (totalBeds > 0) "$totalBeds Sharing" else "Unspecified"
        }
    }
    val notes: String get() = room.notes
    val isActive: Boolean get() = room.isActive

    // Non-deleted physical beds belonging to this room
    val roomBeds: List<BedEntity> get() = beds.filter { !it.deleted && isSameRoomNumber(it.roomNumber, room.roomNumber) }

    // Blocked beds count cannot exceed room capacity
    val blockedBeds: Int get() = roomBeds.count { it.status == "BLOCKED" }.coerceAtMost(totalBeds)
    val usableBeds: Int get() = (totalBeds - blockedBeds).coerceAtLeast(0)

    // Robust list of active tenants belonging to this room
    val activeTenants: List<TenantEntity> get() {
        val tenantMap = tenants.associateBy { it.id }

        return tenants.filter { tenant ->
            if (tenant.deleted) return@filter false

            val isMatchByRoom = isSameRoomNumber(tenant.roomNumber, room.roomNumber)
            val isMatchByAssignment = assignments.any { assign ->
                !assign.deleted &&
                assign.tenantId == tenant.id &&
                isSameRoomNumber(assign.roomNumber, room.roomNumber)
            }

            if (!isMatchByRoom && !isMatchByAssignment) return@filter false

            // Check leaving date: only exclude if explicitly set and strictly in the past
            if (tenant.leavingDate.isNotBlank()) {
                val isPastLeaving = PgDateUtil.isDatePastOrToday(tenant.leavingDate, currentDateStr) &&
                    !PgDateUtil.isDateFuture(tenant.leavingDate, currentDateStr)
                if (isPastLeaving) return@filter false
            }

            true
        }.distinctBy { it.id }
    }

    // Authoritative active assignments covering today on usable (non-blocked) beds
    val activeAssignments: List<BedAssignmentEntity> get() {
        val blockedBedsList = roomBeds.filter { it.status == "BLOCKED" }
        val activeTenantMap = activeTenants.associateBy { it.id }

        val validAssignments = assignments.filter { assign ->
            if (assign.deleted || !isSameRoomNumber(assign.roomNumber, room.roomNumber)) return@filter false
            if (blockedBedsList.any { com.example.core.util.BedIdentityUtil.matchesSameBed(it.bedId, assign.bedId) }) return@filter false

            val tenant = activeTenantMap[assign.tenantId]
            tenant != null
        }

        // Direct assignment synthesis for active tenants who don't have an explicit BedAssignmentEntity record
        val assignedTenantIds = validAssignments.map { it.tenantId }.toSet()
        val unassignedTenants = activeTenants.filter { it.id !in assignedTenantIds }

        val syntheticAssignments = unassignedTenants.mapIndexed { idx, tenant ->
            val bedId = if (tenant.bedId.isNotBlank()) tenant.bedId else "Bed ${idx + 1}"
            BedAssignmentEntity(
                assignmentId = "synthetic_${tenant.id}",
                tenantId = tenant.id,
                roomNumber = room.roomNumber,
                bedId = bedId,
                startDate = tenant.moveInDate,
                endDate = if (tenant.leavingDate.isNotBlank()) tenant.leavingDate else null,
                agreedRent = tenant.monthlyRent,
                propertyId = tenant.propertyId
            )
        }

        return (validAssignments + syntheticAssignments)
            .distinctBy { com.example.core.util.BedIdentityUtil.parseBedIndex(it.bedId) ?: it.bedId.lowercase() }
            .take(usableBeds)
    }

    // Canonical physical beds for this room, strictly conforming to room capacity (totalBeds)
    val canonicalPhysicalBeds: List<BedEntity> get() {
        val nonDeleted = roomBeds
        val convention = com.example.core.util.BedIdentityUtil.determineConvention(nonDeleted.map { it.bedId })
        val activeAssignBedIds = activeAssignments.map { it.bedId }

        val slotMap = mutableMapOf<Int, BedEntity>()
        for (bed in nonDeleted) {
            val idx = com.example.core.util.BedIdentityUtil.parseBedIndex(bed.bedId)
            if (idx != null) {
                val current = slotMap[idx]
                if (current == null) {
                    slotMap[idx] = bed
                } else {
                    // Conflict resolution: prefer bed with active assignment or blocked status
                    val currentHasAssign = activeAssignBedIds.any { com.example.core.util.BedIdentityUtil.matchesSameBed(it, current.bedId) }
                    val bedHasAssign = activeAssignBedIds.any { com.example.core.util.BedIdentityUtil.matchesSameBed(it, bed.bedId) }
                    if (!currentHasAssign && (bedHasAssign || bed.status == "BLOCKED")) {
                        slotMap[idx] = bed
                    }
                }
            }
        }

        val result = mutableListOf<BedEntity>()
        for (i in 1..totalBeds) {
            val existing = slotMap[i]
            if (existing != null) {
                result.add(existing)
            } else {
                result.add(
                    BedEntity(
                        roomNumber = room.roomNumber,
                        bedId = com.example.core.util.BedIdentityUtil.formatBedId(i, convention),
                        status = "AVAILABLE",
                        propertyId = room.propertyId
                    )
                )
            }
        }
        return result.take(totalBeds)
    }

    // Only future leaving dates count as upcoming vacancies
    val leavingTenants: List<TenantEntity> get() = activeTenants.filter {
        it.leavingDate.isNotBlank() && PgDateUtil.isDateFuture(it.leavingDate, currentDateStr)
    }

    val hasUpcomingVacancy: Boolean get() = leavingTenants.isNotEmpty()

    val occupiedBeds: Int get() = activeAssignments.size.coerceAtLeast(activeTenants.size).coerceAtMost(usableBeds)
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
