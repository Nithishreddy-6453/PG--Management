package com.example.features.rooms.domain.usecase

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRoomsUseCase @Inject constructor(
    private val repository: RoomRepository
) {
    operator fun invoke(): Flow<List<RoomSummary>> = repository.getRoomsSummariesFlow()
}

class GetRoomDetailsUseCase @Inject constructor(
    private val repository: RoomRepository
) {
    operator fun invoke(roomNumber: String): Flow<RoomSummary?> = repository.getRoomSummaryFlow(roomNumber)
}

class ValidateRoomUseCase @Inject constructor(
    private val repository: RoomRepository
) {
    suspend fun validateForAdd(
        roomNumber: String,
        floor: String,
        capacity: Int,
        ratePerBed: Double
    ): RoomValidationResult {
        if (roomNumber.isBlank()) {
            return RoomValidationResult.Error("Room Number is required.")
        }
        if (floor.isBlank()) {
            return RoomValidationResult.Error("Floor is required.")
        }
        if (capacity <= 0) {
            return RoomValidationResult.Error("Capacity must be greater than zero.")
        }
        if (ratePerBed < 0) {
            return RoomValidationResult.Error("Monthly rent cannot be negative.")
        }
        val existingRoom = repository.getRoom(roomNumber.trim())
        if (existingRoom != null) {
            return RoomValidationResult.Error("Room Number ${roomNumber.trim()} already exists.")
        }
        return RoomValidationResult.Success
    }

    suspend fun validateForUpdate(
        roomNumber: String,
        floor: String,
        capacity: Int,
        ratePerBed: Double
    ): RoomValidationResult {
        if (roomNumber.isBlank()) {
            return RoomValidationResult.Error("Room Number is required.")
        }
        if (floor.isBlank()) {
            return RoomValidationResult.Error("Floor is required.")
        }
        if (capacity <= 0) {
            return RoomValidationResult.Error("Capacity must be greater than zero.")
        }
        if (ratePerBed < 0) {
            return RoomValidationResult.Error("Monthly rent cannot be negative.")
        }
        return RoomValidationResult.Success
    }
}

class AddRoomUseCase @Inject constructor(
    private val repository: RoomRepository,
    private val validateUseCase: ValidateRoomUseCase
) {
    suspend operator fun invoke(
        roomNumber: String,
        floor: String,
        capacity: Int,
        ratePerBed: Double,
        roomType: String,
        notes: String
    ): RoomValidationResult {
        val validation = validateUseCase.validateForAdd(roomNumber, floor, capacity, ratePerBed)
        if (validation is RoomValidationResult.Success) {
            val entity = RoomEntity(
                roomNumber = roomNumber.trim(),
                floor = floor.trim(),
                capacity = capacity,
                ratePerBed = ratePerBed,
                roomType = roomType,
                notes = notes.trim()
            )
            repository.insertRoom(entity)
        }
        return validation
    }
}

class UpdateRoomUseCase @Inject constructor(
    private val repository: RoomRepository,
    private val validateUseCase: ValidateRoomUseCase
) {
    suspend operator fun invoke(
        roomNumber: String,
        floor: String,
        capacity: Int,
        ratePerBed: Double,
        roomType: String,
        notes: String
    ): RoomValidationResult {
        val currentTenants = repository.getTenantsInRoom(roomNumber)
        if (capacity < currentTenants.size) {
            return RoomValidationResult.Error("Bed capacity cannot be reduced below current active tenants count (${currentTenants.size}).")
        }

        val validation = validateUseCase.validateForUpdate(roomNumber, floor, capacity, ratePerBed)
        if (validation is RoomValidationResult.Error) {
            return validation
        }

        val entity = RoomEntity(
            roomNumber = roomNumber,
            floor = floor.trim(),
            capacity = capacity,
            ratePerBed = ratePerBed,
            roomType = roomType,
            notes = notes.trim()
        )
        repository.insertRoom(entity)
        return RoomValidationResult.Success
    }
}

class DeleteRoomUseCase @Inject constructor(
    private val repository: RoomRepository
) {
    suspend operator fun invoke(roomNumber: String): RoomValidationResult {
        val currentTenants = repository.getTenantsInRoom(roomNumber)
        if (currentTenants.isNotEmpty()) {
            return RoomValidationResult.Error("Cannot delete Room $roomNumber because it has ${currentTenants.size} active tenants.")
        }
        repository.deleteRoom(roomNumber)
        return RoomValidationResult.Success
    }
}

class SearchRoomsUseCase @Inject constructor() {
    operator fun invoke(rooms: List<RoomSummary>, query: String): List<RoomSummary> {
        if (query.isBlank()) return rooms
        val term = query.lowercase().trim()
        return rooms.filter {
            it.roomNumber.lowercase().contains(term) ||
            it.floor.lowercase().contains(term) ||
            it.roomType.lowercase().contains(term) ||
            it.notes.lowercase().contains(term)
        }
    }
}

class FilterRoomsUseCase @Inject constructor() {
    operator fun invoke(
        rooms: List<RoomSummary>,
        floor: String?,
        roomType: String?,
        occupancyStatus: String?
    ): List<RoomSummary> {
        var result = rooms
        if (floor != null && floor != "All" && floor.isNotBlank()) {
            result = result.filter { it.floor == floor }
        }
        if (roomType != null && roomType != "All" && roomType.isNotBlank()) {
            result = result.filter { it.roomType == roomType }
        }
        if (occupancyStatus != null && occupancyStatus != "All" && occupancyStatus.isNotBlank()) {
            result = result.filter { it.occupancyStatus.equals(occupancyStatus, ignoreCase = true) }
        }
        return result
    }
}

data class OverallOccupancy(
    val totalBeds: Int = 0,
    val occupiedBeds: Int = 0,
    val availableBeds: Int = 0,
    val occupancyPercentage: Double = 0.0,
    val vacancyPercentage: Double = 0.0
)

class OccupancyCalculationUseCase @Inject constructor() {
    operator fun invoke(rooms: List<RoomSummary>): OverallOccupancy {
        val total = rooms.sumOf { it.totalBeds }
        val occupied = rooms.sumOf { it.occupiedBeds }
        val available = (total - occupied).coerceAtLeast(0)
        val occPercent = if (total > 0) (occupied.toDouble() / total) * 100.0 else 0.0
        val vacPercent = if (total > 0) (available.toDouble() / total) * 100.0 else 0.0
        return OverallOccupancy(total, occupied, available, occPercent, vacPercent)
    }
}
