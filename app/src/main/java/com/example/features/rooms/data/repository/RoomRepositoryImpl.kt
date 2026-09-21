package com.example.features.rooms.data.repository

import com.example.data.database.RoomDao
import com.example.data.database.TenantDao
import com.example.data.database.RentPaymentDao
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) : RoomRepository {

    override fun getRoomsFlow(): Flow<List<RoomEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomsForPropertyFlow(propId)
        }

    override fun getRoomFlow(roomNumber: String): Flow<RoomEntity?> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomFlow(propId, roomNumber)
        }

    override fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getTenantsInRoomForPropertyFlow(propId, roomNumber)
        }

    override fun getPaymentsForRoomFlow(roomNumber: String): Flow<List<RentPaymentEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId).map { payments ->
                payments.filter { it.roomNumber == roomNumber }
            }
        }
    }

    override fun getRoomsSummariesFlow(): Flow<List<RoomSummary>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            combine(
                roomDao.getRoomsForPropertyFlow(propId),
                tenantDao.getAllTenantsForPropertyFlow(propId)
            ) { rooms, tenants ->
                rooms.map { room ->
                    val roomTenants = tenants.filter { it.roomNumber == room.roomNumber }
                    RoomSummary(room, roomTenants)
                }
            }
        }
    }

    override fun getRoomSummaryFlow(roomNumber: String): Flow<RoomSummary?> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            combine(
                roomDao.getRoomFlow(propId, roomNumber),
                tenantDao.getTenantsInRoomForPropertyFlow(propId, roomNumber)
            ) { room, tenants ->
                room?.let { RoomSummary(it, tenants) }
            }
        }
    }

    override suspend fun getRoom(roomNumber: String): RoomEntity? {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val room = roomDao.getRoom(propId, roomNumber)
        if (room != null) return room
        return roomDao.getRoom(roomNumber)
    }

    override suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val list = tenantDao.getTenantsInRoom(propId, roomNumber)
        if (list.isNotEmpty()) return list
        return tenantDao.getTenantsInRoom(roomNumber)
    }

    override suspend fun insertRoom(room: RoomEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = room.copy(
            propertyId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        roomDao.insertRoom(updated)
        syncCoordinator?.enqueueOperation("ROOM", updated.roomNumber, "CREATE")
    }

    override suspend fun deleteRoom(roomNumber: String) {
        val propId = currentPropertyManager.getCurrentPropertyId()
        roomDao.softDeleteRoom(propId, roomNumber)
        syncCoordinator?.enqueueOperation("ROOM", roomNumber, "DELETE")
    }
}
