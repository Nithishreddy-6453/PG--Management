package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class OccupancyStats(
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyPercentage: Double = 0.0,
    val totalBeds: Int = 0,
    val occupiedBeds: Int = 0,
    val vacantBeds: Int = 0,
    val bedOccupancyPercentage: Double = 0.0
)

class OccupancyUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<OccupancyStats> {
        return combine(repository.getRoomsFlow(), repository.getTenantsFlow()) { rooms, tenants ->
            val totalRooms = rooms.size
            val occupiedRooms = rooms.count { room -> tenants.any { it.roomNumber == room.roomNumber } }
            val vacantRooms = totalRooms - occupiedRooms
            val occupancyPercent = if (totalRooms > 0) (occupiedRooms.toDouble() / totalRooms.toDouble()) * 100.0 else 0.0
            
            val totalBeds = rooms.sumOf { it.capacity }
            val occupiedBeds = tenants.size
            val vacantBeds = totalBeds - occupiedBeds
            val bedOccupancyPercent = if (totalBeds > 0) (occupiedBeds.toDouble() / totalBeds.toDouble()) * 100.0 else 0.0
            
            OccupancyStats(
                totalRooms = totalRooms,
                occupiedRooms = occupiedRooms,
                vacantRooms = vacantRooms,
                occupancyPercentage = occupancyPercent,
                totalBeds = totalBeds,
                occupiedBeds = occupiedBeds,
                vacantBeds = vacantBeds,
                bedOccupancyPercentage = bedOccupancyPercent
            )
        }
    }
}
