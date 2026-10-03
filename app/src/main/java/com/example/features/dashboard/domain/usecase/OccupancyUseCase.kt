package com.example.features.dashboard.domain.usecase

import androidx.compose.runtime.Immutable
import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@Immutable
data class OccupancyStats(
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyPercentage: Double = 0.0,
    val totalBeds: Int = 0,
    val occupiedBeds: Int = 0,
    val vacantBeds: Int = 0,
    val bedOccupancyPercentage: Double = 0.0,
    val activeTenantsCount: Int = 0,
    val totalPgCapacity: Int = 0,
    val homeOccupancyPercentage: Double = 0.0,
    val vacanciesCount: Int = 0,
    val tenantsCapacityRatioText: String = "0 / 0 Tenants",
    val capacityDisplayText: String = "0 Tenants / 0 Capacity"
)

class OccupancyUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<OccupancyStats> {
        return combine(
            repository.getRoomsFlow(),
            repository.getBedsFlow(),
            repository.getTenantsFlow()
        ) { rooms, beds, tenants ->
            val activeRooms = rooms.filter { !it.deleted }
            val activeTenants = tenants.filter { !it.deleted && it.roomNumber.isNotBlank() }
            val activeTenantsCount = activeTenants.size

            // PG Capacity means total usable beds that can accommodate tenants
            val totalPgCapacity = activeRooms.sumOf { room ->
                val rBeds = beds.filter { it.roomNumber == room.roomNumber }
                if (rBeds.isNotEmpty()) {
                    rBeds.count { it.status != "BLOCKED" }
                } else {
                    room.capacity
                }
            }

            val vacanciesCount = (totalPgCapacity - activeTenantsCount).coerceAtLeast(0)
            val homeOccupancyPercentage = if (totalPgCapacity > 0) {
                (activeTenantsCount.toDouble() / totalPgCapacity.toDouble()) * 100.0
            } else 0.0

            val totalRooms = activeRooms.size
            val occupiedRooms = activeRooms.count { room -> activeTenants.any { it.roomNumber == room.roomNumber } }
            val vacantRooms = (totalRooms - occupiedRooms).coerceAtLeast(0)
            val roomOccupancyPercent = if (totalRooms > 0) (occupiedRooms.toDouble() / totalRooms.toDouble()) * 100.0 else 0.0

            val totalBeds = totalPgCapacity
            val occupiedBeds = activeTenantsCount.coerceAtMost(totalBeds)
            val vacantBeds = vacanciesCount
            val bedOccupancyPercent = homeOccupancyPercentage

            OccupancyStats(
                totalRooms = totalRooms,
                occupiedRooms = occupiedRooms,
                vacantRooms = vacantRooms,
                occupancyPercentage = roomOccupancyPercent,
                totalBeds = totalBeds,
                occupiedBeds = occupiedBeds,
                vacantBeds = vacantBeds,
                bedOccupancyPercentage = bedOccupancyPercent,
                activeTenantsCount = activeTenantsCount,
                totalPgCapacity = totalPgCapacity,
                homeOccupancyPercentage = homeOccupancyPercentage,
                vacanciesCount = vacanciesCount,
                tenantsCapacityRatioText = "$activeTenantsCount / $totalPgCapacity Tenants",
                capacityDisplayText = "$activeTenantsCount Tenants / $totalPgCapacity Capacity"
            )
        }.flowOn(Dispatchers.Default)
    }
}
