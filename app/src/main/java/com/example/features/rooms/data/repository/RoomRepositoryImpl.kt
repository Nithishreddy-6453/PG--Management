package com.example.features.rooms.data.repository

import com.example.data.database.*
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
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
    private val bedDao: BedDao,
    private val bedAssignmentDao: BedAssignmentDao,
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

    override fun getBedsForRoomFlow(roomNumber: String): Flow<List<BedEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            bedDao.getBedsForRoomFlow(propId, roomNumber)
        }

    override fun getBedAssignmentsForRoomFlow(roomNumber: String): Flow<List<BedAssignmentEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            bedAssignmentDao.getAssignmentsForRoomFlow(propId, roomNumber)
        }

    override fun getRoomsSummariesFlow(): Flow<List<RoomSummary>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            combine(
                roomDao.getRoomsForPropertyFlow(propId),
                tenantDao.getAllTenantsForPropertyFlow(propId),
                bedAssignmentDao.getAllAssignmentsFlow(propId)
            ) { rooms, tenants, assignments ->
                rooms.map { room ->
                    val roomTenants = tenants.filter { it.roomNumber == room.roomNumber }
                    val roomAssignments = assignments.filter { it.roomNumber == room.roomNumber }
                    RoomSummary(room, roomTenants, emptyList(), roomAssignments)
                }
            }
        }
    }

    override fun getRoomSummaryFlow(roomNumber: String): Flow<RoomSummary?> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            combine(
                roomDao.getRoomFlow(propId, roomNumber),
                tenantDao.getTenantsInRoomForPropertyFlow(propId, roomNumber),
                bedDao.getBedsForRoomFlow(propId, roomNumber),
                bedAssignmentDao.getAssignmentsForRoomFlow(propId, roomNumber)
            ) { room, tenants, beds, assignments ->
                room?.let { RoomSummary(it, tenants, beds, assignments) }
            }
        }
    }

    override suspend fun getRoom(roomNumber: String): RoomEntity? {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getRoom(propId, roomNumber) ?: roomDao.getRoom(roomNumber)
    }

    override suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return tenantDao.getTenantsInRoom(propId, roomNumber).ifEmpty { tenantDao.getTenantsInRoom(roomNumber) }
    }

    override suspend fun getBedsForRoom(roomNumber: String): List<BedEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return bedDao.getBedsForRoom(propId, roomNumber)
    }

    override suspend fun getBedAssignmentsForRoom(roomNumber: String): List<BedAssignmentEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return bedAssignmentDao.getAssignmentsForRoom(propId, roomNumber)
    }

    override suspend fun insertRoom(room: RoomEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = room.copy(
            propertyId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        roomDao.insertRoom(updated)
        for (i in 1..updated.capacity) {
            val bedId = "Bed $i"
            if (bedDao.getBed(updated.propertyId, updated.roomNumber, bedId) == null) {
                bedDao.insertBed(BedEntity(roomNumber = updated.roomNumber, bedId = bedId, propertyId = updated.propertyId))
            }
        }
        syncCoordinator?.enqueueOperation("ROOM", updated.roomNumber, "CREATE")
    }

    override suspend fun deleteRoom(roomNumber: String) {
        val propId = currentPropertyManager.getCurrentPropertyId()
        roomDao.softDeleteRoom(propId, roomNumber)
        syncCoordinator?.enqueueOperation("ROOM", roomNumber, "DELETE")
    }

    override suspend fun assignTenantToBed(roomNumber: String, bedId: String, tenantId: Int, startDate: String, agreedRent: Double): RoomValidationResult {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val bed = bedDao.getBed(propId, roomNumber, bedId) ?: return RoomValidationResult.Error("Bed does not exist.")
        if (bed.status == "BLOCKED") return RoomValidationResult.Error("Bed is blocked / out of service.")
        
        val activeAssignment = bedAssignmentDao.getActiveAssignmentForBed(propId, roomNumber, bedId)
        if (activeAssignment != null) return RoomValidationResult.Error("Bed is already occupied.")

        val tenantActiveAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId)
        if (tenantActiveAssignment != null) return RoomValidationResult.Error("Tenant already occupies another bed.")

        val assignment = BedAssignmentEntity(
            tenantId = tenantId,
            roomNumber = roomNumber,
            bedId = bedId,
            startDate = startDate,
            agreedRent = agreedRent,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(assignment)
        bedDao.updateBed(bed.copy(status = "OCCUPIED", updatedAt = System.currentTimeMillis()))

        val tenant = tenantDao.getTenantById(tenantId)
        if (tenant != null) {
            tenantDao.updateTenant(tenant.copy(roomNumber = roomNumber, bedId = bedId, monthlyRent = agreedRent, updatedAt = System.currentTimeMillis()))
        }
        return RoomValidationResult.Success
    }

    override suspend fun transferTenant(tenantId: Int, newRoomNumber: String, newBedId: String, transferDate: String, newAgreedRent: Double): RoomValidationResult {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val destBed = bedDao.getBed(propId, newRoomNumber, newBedId) ?: return RoomValidationResult.Error("Destination bed does not exist.")
        if (destBed.status == "BLOCKED") return RoomValidationResult.Error("Destination bed is blocked.")

        val activeDestAssignment = bedAssignmentDao.getActiveAssignmentForBed(propId, newRoomNumber, newBedId)
        if (activeDestAssignment != null) return RoomValidationResult.Error("Destination bed is already occupied.")

        val currentAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId)
            ?: return RoomValidationResult.Error("Tenant has no active bed assignment.")

        bedAssignmentDao.updateAssignment(currentAssignment.copy(endDate = transferDate, updatedAt = System.currentTimeMillis()))
        val oldBed = bedDao.getBed(propId, currentAssignment.roomNumber, currentAssignment.bedId)
        if (oldBed != null) {
            bedDao.updateBed(oldBed.copy(status = "AVAILABLE", updatedAt = System.currentTimeMillis()))
        }

        val newAssignment = BedAssignmentEntity(
            tenantId = tenantId,
            roomNumber = newRoomNumber,
            bedId = newBedId,
            startDate = transferDate,
            agreedRent = newAgreedRent,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(newAssignment)
        bedDao.updateBed(destBed.copy(status = "OCCUPIED", updatedAt = System.currentTimeMillis()))

        val tenant = tenantDao.getTenantById(tenantId)
        if (tenant != null) {
            tenantDao.updateTenant(tenant.copy(roomNumber = newRoomNumber, bedId = newBedId, monthlyRent = newAgreedRent, updatedAt = System.currentTimeMillis()))
        }
        return RoomValidationResult.Success
    }

    override suspend fun vacateTenant(tenantId: Int, leavingDate: String): RoomValidationResult {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val currentAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId)
        if (currentAssignment != null) {
            bedAssignmentDao.updateAssignment(currentAssignment.copy(endDate = leavingDate, updatedAt = System.currentTimeMillis()))
            val bed = bedDao.getBed(propId, currentAssignment.roomNumber, currentAssignment.bedId)
            if (bed != null) {
                bedDao.updateBed(bed.copy(status = "AVAILABLE", updatedAt = System.currentTimeMillis()))
            }
        }
        val tenant = tenantDao.getTenantById(tenantId)
        if (tenant != null) {
            tenantDao.updateTenant(tenant.copy(leavingDate = leavingDate, updatedAt = System.currentTimeMillis()))
        }
        return RoomValidationResult.Success
    }

    override suspend fun blockBed(roomNumber: String, bedId: String, blocked: Boolean): RoomValidationResult {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val bed = bedDao.getBed(propId, roomNumber, bedId) ?: return RoomValidationResult.Error("Bed not found.")
        if (blocked && bed.status == "OCCUPIED") {
            return RoomValidationResult.Error("Cannot block an occupied bed.")
        }
        val newStatus = if (blocked) "BLOCKED" else "AVAILABLE"
        bedDao.updateBed(bed.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
        return RoomValidationResult.Success
    }
}
