package com.example.features.rooms.domain.model

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity

data class RoomSummary(
    val room: RoomEntity,
    val tenants: List<TenantEntity>
) {
    val roomNumber: String get() = room.roomNumber
    val floor: String get() = room.floor
    val totalBeds: Int get() = room.capacity
    val ratePerBed: Double get() = room.ratePerBed
    val roomType: String get() = room.roomType
    val notes: String get() = room.notes
    
    val occupiedBeds: Int get() = tenants.size
    val availableBeds: Int get() = (totalBeds - occupiedBeds).coerceAtLeast(0)
    
    val activeTenantCount: Int get() = tenants.size
    
    val occupancyStatus: String get() = when {
        occupiedBeds == 0 -> "Empty"
        availableBeds == 0 -> "Full"
        else -> "Available"
    }
    
    val occupancyPercentage: Double get() = if (totalBeds > 0) {
        (occupiedBeds.toDouble() / totalBeds) * 100.0
    } else 0.0
    
    val vacancyPercentage: Double get() = if (totalBeds > 0) {
        (availableBeds.toDouble() / totalBeds) * 100.0
    } else 0.0
}

sealed interface RoomValidationResult {
    object Success : RoomValidationResult
    data class Error(val message: String) : RoomValidationResult
}
