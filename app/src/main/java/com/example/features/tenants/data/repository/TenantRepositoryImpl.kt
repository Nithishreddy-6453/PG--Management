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

    override suspend fun insertTenant(tenant: TenantEntity): Long {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = tenant.copy(
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        val id = tenantDao.insertTenant(updated)
        syncCoordinator?.enqueueOperation("TENANT", id.toString(), "CREATE")
        return id
    }

    override suspend fun updateTenant(tenant: TenantEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = tenant.copy(
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
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
        return roomDao.getRoom(propId, roomNumber)
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
        val updated = payment.copy(
            propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.insertPayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "CREATE")
    }

    override suspend fun deletePaymentsForTenant(tenantId: Int) {
        rentPaymentDao.softDeletePaymentsForTenant(tenantId)
    }

    override fun getPaymentsForTenantFlow(tenantId: Int): Flow<List<RentPaymentEntity>> {
        return rentPaymentDao.getPaymentsForTenantFlow(tenantId)
    }
}
