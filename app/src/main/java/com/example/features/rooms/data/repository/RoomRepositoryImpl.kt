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
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
    private val syncCoordinator: SyncCoordinator? = null,
    private val appDatabase: AppDatabase? = null
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
                bedDao.getAllBedsForPropertyFlow(propId),
                bedAssignmentDao.getAllAssignmentsFlow(propId)
            ) { rooms, tenants, beds, assignments ->
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                rooms.map { room ->
                    val roomTenants = tenants.filter { it.roomNumber == room.roomNumber }
                    val roomBeds = beds.filter { it.roomNumber == room.roomNumber }
                    val roomAssignments = assignments.filter { it.roomNumber == room.roomNumber }
                    RoomSummary(
                        room = room,
                        tenants = roomTenants,
                        beds = roomBeds,
                        assignments = roomAssignments,
                        currentDateStr = todayStr
                    )
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
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                room?.let {
                    RoomSummary(
                        room = it,
                        tenants = tenants,
                        beds = beds,
                        assignments = assignments,
                        currentDateStr = todayStr
                    )
                }
            }
        }
    }

    override suspend fun getRoom(roomNumber: String): RoomEntity? {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getRoom(propId, roomNumber.trim())
    }

    override suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return tenantDao.getTenantsInRoom(propId, roomNumber.trim())
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
                val newBed = BedEntity(
                    roomNumber = updated.roomNumber,
                    bedId = bedId,
                    status = "AVAILABLE",
                    propertyId = updated.propertyId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING_UPLOAD"
                )
                bedDao.insertBed(newBed)
                syncCoordinator?.enqueueOperation("BED", "${updated.propertyId}_${updated.roomNumber}_$bedId", "CREATE")
            }
        }
        syncCoordinator?.enqueueOperation("ROOM", updated.roomNumber, "CREATE")
    }

    override suspend fun deleteRoom(roomNumber: String) {
        val propId = currentPropertyManager.getCurrentPropertyId()
        roomDao.softDeleteRoom(propId, roomNumber)
        syncCoordinator?.enqueueOperation("ROOM", roomNumber, "DELETE")
    }

    override suspend fun assignTenantToBed(
        roomNumber: String,
        bedId: String,
        tenantId: Int,
        startDate: String,
        agreedRent: Double
    ): RoomValidationResult {
        suspend fun executeAssign(): RoomValidationResult {
            val propId = currentPropertyManager.getCurrentPropertyId()
            var bed = bedDao.getBed(propId, roomNumber, bedId)
            if (bed == null) {
                // Auto-create bed if within room capacity
                val room = roomDao.getRoom(propId, roomNumber) ?: return RoomValidationResult.Error("Room does not exist.")
                val newBed = BedEntity(
                    roomNumber = roomNumber,
                    bedId = bedId,
                    status = "AVAILABLE",
                    propertyId = propId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING_UPLOAD"
                )
                bedDao.insertBed(newBed)
                bed = newBed
            }
            if (bed.status == "BLOCKED") return RoomValidationResult.Error("Bed is blocked / out of service.")

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val activeAssignment = bedAssignmentDao.getActiveAssignmentForBed(propId, roomNumber, bedId, todayStr)
            if (activeAssignment != null && activeAssignment.tenantId != tenantId) {
                return RoomValidationResult.Error("Bed is already occupied by another tenant.")
            }

            val tenantActiveAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId, todayStr)
            if (tenantActiveAssignment != null && (tenantActiveAssignment.roomNumber != roomNumber || tenantActiveAssignment.bedId != bedId)) {
                return RoomValidationResult.Error("Tenant already occupies another bed ($tenantActiveAssignment.roomNumber / $tenantActiveAssignment.bedId).")
            }

            val assignment = BedAssignmentEntity(
                assignmentId = UUID.randomUUID().toString(),
                tenantId = tenantId,
                roomNumber = roomNumber,
                bedId = bedId,
                startDate = startDate.ifBlank { todayStr },
                endDate = null,
                agreedRent = agreedRent,
                propertyId = propId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            bedAssignmentDao.insertAssignment(assignment)
            syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", assignment.assignmentId, "CREATE")

            bedDao.updateBed(bed.copy(status = "OCCUPIED", updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD"))
            syncCoordinator?.enqueueOperation("BED", "${propId}_${roomNumber}_$bedId", "UPDATE")

            val tenant = tenantDao.getTenantById(tenantId)
            if (tenant != null) {
                tenantDao.updateTenant(
                    tenant.copy(
                        roomNumber = roomNumber,
                        bedId = bedId,
                        monthlyRent = if (agreedRent > 0) agreedRent else tenant.monthlyRent,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
                syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")
            }
            return RoomValidationResult.Success
        }

        return if (appDatabase != null) {
            appDatabase.withTransaction { executeAssign() }
        } else {
            executeAssign()
        }
    }

    override suspend fun transferTenant(
        tenantId: Int,
        newRoomNumber: String,
        newBedId: String,
        transferDate: String,
        newAgreedRent: Double
    ): RoomValidationResult {
        suspend fun executeTransfer(): RoomValidationResult {
            val propId = currentPropertyManager.getCurrentPropertyId()
            var destBed = bedDao.getBed(propId, newRoomNumber, newBedId)
            if (destBed == null) {
                val room = roomDao.getRoom(propId, newRoomNumber) ?: return RoomValidationResult.Error("Destination room does not exist.")
                val newBed = BedEntity(
                    roomNumber = newRoomNumber,
                    bedId = newBedId,
                    status = "AVAILABLE",
                    propertyId = propId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING_UPLOAD"
                )
                bedDao.insertBed(newBed)
                destBed = newBed
            }
            if (destBed.status == "BLOCKED") return RoomValidationResult.Error("Destination bed is blocked.")

            val activeDestAssignment = bedAssignmentDao.getActiveAssignmentForBed(propId, newRoomNumber, newBedId, transferDate)
            if (activeDestAssignment != null && activeDestAssignment.tenantId != tenantId) {
                return RoomValidationResult.Error("Destination bed is already occupied.")
            }

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val effectiveTransferDate = transferDate.ifBlank { todayStr }
            val currentAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId, todayStr)
                ?: bedAssignmentDao.getAssignmentsForTenant(propId, tenantId).lastOrNull { it.endDate == null || it.endDate >= todayStr }

            if (currentAssignment != null) {
                bedAssignmentDao.updateAssignment(
                    currentAssignment.copy(
                        endDate = effectiveTransferDate,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
                syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", currentAssignment.assignmentId, "UPDATE")

                val oldBed = bedDao.getBed(propId, currentAssignment.roomNumber, currentAssignment.bedId)
                if (oldBed != null && (oldBed.roomNumber != newRoomNumber || oldBed.bedId != newBedId)) {
                    bedDao.updateBed(oldBed.copy(status = "AVAILABLE", updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD"))
                    syncCoordinator?.enqueueOperation("BED", "${propId}_${currentAssignment.roomNumber}_${currentAssignment.bedId}", "UPDATE")
                }
            }

            val newAssignment = BedAssignmentEntity(
                assignmentId = UUID.randomUUID().toString(),
                tenantId = tenantId,
                roomNumber = newRoomNumber,
                bedId = newBedId,
                startDate = effectiveTransferDate,
                agreedRent = newAgreedRent,
                propertyId = propId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            bedAssignmentDao.insertAssignment(newAssignment)
            syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", newAssignment.assignmentId, "CREATE")

            bedDao.updateBed(destBed.copy(status = "OCCUPIED", updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD"))
            syncCoordinator?.enqueueOperation("BED", "${propId}_${newRoomNumber}_$newBedId", "UPDATE")

            val tenant = tenantDao.getTenantById(tenantId)
            if (tenant != null) {
                tenantDao.updateTenant(
                    tenant.copy(
                        roomNumber = newRoomNumber,
                        bedId = newBedId,
                        monthlyRent = if (newAgreedRent > 0) newAgreedRent else tenant.monthlyRent,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
                syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")
            }
            return RoomValidationResult.Success
        }

        return if (appDatabase != null) {
            appDatabase.withTransaction { executeTransfer() }
        } else {
            executeTransfer()
        }
    }

    override suspend fun vacateTenant(tenantId: Int, leavingDate: String, currentDateOverride: String?): RoomValidationResult {
        suspend fun executeVacate(): RoomValidationResult {
            val tenant = tenantDao.getTenantById(tenantId)
                ?: return RoomValidationResult.Error("Tenant not found.")
            val propId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") {
                tenant.propertyId
            } else {
                currentPropertyManager.getCurrentPropertyId()
            }

            val todayStr = currentDateOverride ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val effectiveDate = if (leavingDate.isNotBlank()) leavingDate.trim() else tenant.leavingDate.ifBlank { todayStr }
            val isFutureVacate = effectiveDate > todayStr

            val currentAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, tenantId, todayStr)
                ?: bedAssignmentDao.getAssignmentsForTenant(propId, tenantId).lastOrNull { it.endDate == null || it.endDate >= todayStr }

            if (currentAssignment != null) {
                bedAssignmentDao.updateAssignment(
                    currentAssignment.copy(
                        endDate = effectiveDate,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
                syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", currentAssignment.assignmentId, "UPDATE")

                if (!isFutureVacate) {
                    val bed = bedDao.getBed(propId, currentAssignment.roomNumber, currentAssignment.bedId)
                    if (bed != null) {
                        bedDao.updateBed(
                            bed.copy(
                                status = "AVAILABLE",
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "PENDING_UPLOAD"
                            )
                        )
                        syncCoordinator?.enqueueOperation("BED", "${propId}_${currentAssignment.roomNumber}_${currentAssignment.bedId}", "UPDATE")
                    }
                }
            } else {
                if (tenant.roomNumber.isNotBlank() && tenant.bedId.isNotBlank() && !isFutureVacate) {
                    val bed = bedDao.getBed(propId, tenant.roomNumber, tenant.bedId)
                    if (bed != null) {
                        bedDao.updateBed(
                            bed.copy(
                                status = "AVAILABLE",
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "PENDING_UPLOAD"
                            )
                        )
                        syncCoordinator?.enqueueOperation("BED", "${propId}_${tenant.roomNumber}_${tenant.bedId}", "UPDATE")
                    }
                }
            }

            val roomNum = if (tenant.roomNumber.isNotBlank()) tenant.roomNumber else currentAssignment?.roomNumber ?: ""
            val rawBedId = if (tenant.bedId.isNotBlank()) tenant.bedId else currentAssignment?.bedId ?: ""
            val bedLabel = if (rawBedId.isBlank()) "" else if (rawBedId.startsWith("Bed", ignoreCase = true)) rawBedId else "Bed $rawBedId"

            if (!isFutureVacate) {
                val vacatedNotes = if (tenant.notes.contains("Vacated")) {
                    tenant.notes
                } else if (roomNum.isNotBlank() && bedLabel.isNotBlank()) {
                    "${tenant.notes}\n[Vacated from Room $roomNum $bedLabel on $effectiveDate]".trim()
                } else {
                    "${tenant.notes}\n[Vacated on $effectiveDate]".trim()
                }
                tenantDao.updateTenant(
                    tenant.copy(
                        roomNumber = "",
                        bedId = "",
                        leavingDate = effectiveDate,
                        notes = vacatedNotes,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
            } else {
                tenantDao.updateTenant(
                    tenant.copy(
                        leavingDate = effectiveDate,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                )
            }
            syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")
            return RoomValidationResult.Success
        }

        return if (appDatabase != null) {
            appDatabase.withTransaction { executeVacate() }
        } else {
            executeVacate()
        }
    }

    override suspend fun blockBed(roomNumber: String, bedId: String, blocked: Boolean): RoomValidationResult {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val bed = bedDao.getBed(propId, roomNumber, bedId) ?: return RoomValidationResult.Error("Bed not found.")
        if (blocked && bed.status == "OCCUPIED") {
            return RoomValidationResult.Error("Cannot block an occupied bed.")
        }
        val newStatus = if (blocked) "BLOCKED" else "AVAILABLE"
        bedDao.updateBed(bed.copy(status = newStatus, updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD"))
        syncCoordinator?.enqueueOperation("BED", "${propId}_${roomNumber}_$bedId", "UPDATE")
        return RoomValidationResult.Success
    }
}
