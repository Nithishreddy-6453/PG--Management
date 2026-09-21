package com.example.features.tenants.data.repository

import com.example.data.database.*
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.tenants.domain.repository.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) : TenantRepository {

    override fun getAllTenantsFlow(): Flow<List<TenantEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getAllTenantsForPropertyFlow(propId)
        }
    }

    override suspend fun getTenantById(id: Int): TenantEntity? {
        return tenantDao.getTenantById(id)
    }

    override fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getTenantsInRoomForPropertyFlow(propId, roomNumber)
        }
    }

    override suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return tenantDao.getTenantsInRoom(propId, roomNumber)
    }

    override suspend fun getCurrentPropertyId(): String {
        return currentPropertyManager.getCurrentPropertyId()
    }

    override suspend fun insertTenant(tenant: TenantEntity): Long {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val cloudId = if (tenant.cloudId.isNotBlank()) tenant.cloudId else "tenant_${java.util.UUID.randomUUID()}"
        val updated = tenant.copy(
            cloudId = cloudId,
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
            ownerId = if (tenant.ownerId.isNotBlank()) tenant.ownerId else ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        val id = tenantDao.insertTenant(updated)
        syncCoordinator?.enqueueOperation("TENANT", id.toString(), "CREATE")
        return id
    }

    override suspend fun updateTenant(tenant: TenantEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val cloudId = if (tenant.cloudId.isNotBlank()) tenant.cloudId else "tenant_${if (tenant.id > 0) tenant.id else java.util.UUID.randomUUID()}"
        val updated = tenant.copy(
            cloudId = cloudId,
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
            ownerId = if (tenant.ownerId.isNotBlank()) tenant.ownerId else ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        tenantDao.updateTenant(updated)
        syncCoordinator?.enqueueOperation("TENANT", updated.id.toString(), "UPDATE")
    }

    override suspend fun deleteTenant(id: Int) {
        tenantDao.softDeleteTenant(id)
        rentPaymentDao.softDeletePaymentsForTenant(id)
        syncCoordinator?.enqueueOperation("TENANT", id.toString(), "DELETE")
    }

    override suspend fun getRoom(roomNumber: String): RoomEntity? {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val room = roomDao.getRoom(propId, roomNumber)
        if (room != null) return room
        return roomDao.getRoom(roomNumber)
    }

    override suspend fun insertRoom(room: RoomEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val updated = room.copy(
            propertyId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else currentPropId,
            ownerId = if (room.ownerId.isNotBlank()) room.ownerId else ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        roomDao.insertRoom(updated)
        syncCoordinator?.enqueueOperation("ROOM", "${updated.propertyId}_${updated.roomNumber}", "CREATE")
    }

    override suspend fun getAllRooms(): List<RoomEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getAllRooms(propId)
    }

    override fun getAllRoomsFlow(): Flow<List<RoomEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomsForPropertyFlow(propId)
        }
    }

    override suspend fun insertRentPayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }

        val existingPayment: RentPaymentEntity? = when {
            payment.id > 0 -> rentPaymentDao.getPaymentByIdIncludingDeleted(payment.id)
            payment.cloudId.isNotBlank() -> rentPaymentDao.getPaymentByCloudIdIncludingDeleted(payment.cloudId)
            payment.tenantId > 0 && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantPropertyAndMonth(payment.tenantId, currentPropId, payment.billingMonth)
                    ?: rentPaymentDao.getPaymentForTenantAndMonth(payment.tenantId, payment.billingMonth)
            }
            payment.tenantName.isNotBlank() && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantNamePropertyAndMonth(payment.tenantName, currentPropId, payment.billingMonth)
            }
            else -> null
        }

        if (existingPayment != null && !existingPayment.deleted) {
            val updated = existingPayment.copy(
                tenantName = payment.tenantName.ifBlank { existingPayment.tenantName },
                roomNumber = payment.roomNumber.ifBlank { existingPayment.roomNumber },
                amount = if (payment.amount > 0) payment.amount else existingPayment.amount,
                propertyId = if (existingPayment.propertyId.isNotBlank() && existingPayment.propertyId != "property_default") existingPayment.propertyId else currentPropId,
                ownerId = if (existingPayment.ownerId.isNotBlank()) existingPayment.ownerId else ownerId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            rentPaymentDao.updatePayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
        } else {
            val cloudId = if (payment.cloudId.isNotBlank()) payment.cloudId else "payment_${java.util.UUID.randomUUID()}"
            val updated = payment.copy(
                cloudId = cloudId,
                propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
                ownerId = if (payment.ownerId.isNotBlank()) payment.ownerId else ownerId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            val insertedId = rentPaymentDao.insertPayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", insertedId.toString(), "CREATE")
        }
    }

    override suspend fun deletePaymentsForTenant(tenantId: Int) {
        rentPaymentDao.softDeletePaymentsForTenant(tenantId)
    }

    override fun getPaymentsForTenantFlow(tenantId: Int): Flow<List<RentPaymentEntity>> {
        return rentPaymentDao.getPaymentsForTenantFlow(tenantId)
    }
}
