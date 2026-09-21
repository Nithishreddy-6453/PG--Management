package com.example.data.repository

import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileDao
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.PropertyDao
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentDao
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomDao
import com.example.data.database.RoomEntity
import com.example.data.database.TenantDao
import com.example.data.database.TenantEntity
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PgRepository @Inject constructor(
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val expenseDao: ExpenseDao,
    private val ownerProfileDao: OwnerProfileDao,
    private val propertyDao: PropertyDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) {
    val allRooms: Flow<List<RoomEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomsForPropertyFlow(propId)
        }

    val allTenants: Flow<List<TenantEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getAllTenantsForPropertyFlow(propId)
        }

    val allPayments: Flow<List<RentPaymentEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId)
        }

    val allExpenses: Flow<List<ExpenseEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            expenseDao.getAllExpensesForPropertyFlow(propId)
        }

    val ownerProfile: Flow<OwnerProfileEntity?> = ownerProfileDao.getProfileFlow()

    val currentProperty: Flow<PropertyEntity?> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            propertyDao.getPropertyFlow(propId)
        }

    val allProperties: Flow<List<PropertyEntity>> = propertyDao.getActivePropertiesFlow()

    suspend fun insertRoom(room: RoomEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = room.copy(
            propertyId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        roomDao.insertRoom(updated)
        syncCoordinator?.enqueueOperation("ROOM", updated.roomNumber, "CREATE")
    }

    suspend fun deleteRoom(roomNumber: String) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        roomDao.softDeleteRoom(currentPropId, roomNumber)
        syncCoordinator?.enqueueOperation("ROOM", roomNumber, "DELETE")
    }

    suspend fun insertTenant(tenant: TenantEntity): Int {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = tenant.copy(
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        val id = tenantDao.insertTenant(updated).toInt()
        syncCoordinator?.enqueueOperation("TENANT", id.toString(), "CREATE")
        return id
    }

    suspend fun updateTenant(tenant: TenantEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = tenant.copy(
            propertyId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        tenantDao.updateTenant(updated)
        syncCoordinator?.enqueueOperation("TENANT", updated.id.toString(), "UPDATE")
    }

    suspend fun deleteTenant(id: Int) {
        tenantDao.softDeleteTenant(id)
        rentPaymentDao.softDeletePaymentsForTenant(id)
        syncCoordinator?.enqueueOperation("TENANT", id.toString(), "DELETE")
    }

    suspend fun insertPayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = payment.copy(
            propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.insertPayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "CREATE")
    }

    suspend fun updatePayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = payment.copy(
            propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.updatePayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
    }

    suspend fun deletePayment(id: Int) {
        rentPaymentDao.softDeletePayment(id)
        syncCoordinator?.enqueueOperation("PAYMENT", id.toString(), "DELETE")
    }

    suspend fun insertExpense(expense: ExpenseEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = expense.copy(
            propertyId = currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        expenseDao.insertExpense(updated)
        syncCoordinator?.enqueueOperation("EXPENSE", updated.id.toString(), "CREATE")
    }

    suspend fun deleteExpense(id: Int) {
        expenseDao.softDeleteExpense(id)
        syncCoordinator?.enqueueOperation("EXPENSE", id.toString(), "DELETE")
    }

    suspend fun getProfile(): OwnerProfileEntity? {
        return ownerProfileDao.getProfile()
    }

    suspend fun insertProfile(profile: OwnerProfileEntity) {
        val updated = profile.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        ownerProfileDao.insertProfile(updated)
        syncCoordinator?.enqueueOperation("PROPERTY", "property_main", "UPDATE")
    }

    fun getTenantsInRoom(roomNumber: String): Flow<List<TenantEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getTenantsInRoomForPropertyFlow(propId, roomNumber)
        }
    }

    fun getRoomFlow(roomNumber: String): Flow<RoomEntity?> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomFlow(propId, roomNumber)
        }
    }

    suspend fun getRoom(roomNumber: String): RoomEntity? {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getRoom(propId, roomNumber)
    }
}
