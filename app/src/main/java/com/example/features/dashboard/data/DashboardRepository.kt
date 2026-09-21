package com.example.features.dashboard.data

import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileDao
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.RentPaymentDao
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomDao
import com.example.data.database.RoomEntity
import com.example.data.database.TenantDao
import com.example.data.database.TenantEntity
import com.example.features.properties.data.CurrentPropertyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class DashboardRepository @Inject constructor(
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val expenseDao: ExpenseDao,
    private val ownerProfileDao: OwnerProfileDao,
    private val currentPropertyManager: CurrentPropertyManager
) {
    fun getRoomsFlow(): Flow<List<RoomEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            roomDao.getRoomsForPropertyFlow(propId)
        }

    fun getTenantsFlow(): Flow<List<TenantEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            tenantDao.getAllTenantsForPropertyFlow(propId)
        }

    fun getPaymentsFlow(): Flow<List<RentPaymentEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId)
        }

    fun getExpensesFlow(): Flow<List<ExpenseEntity>> =
        currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            expenseDao.getAllExpensesForPropertyFlow(propId)
        }

    fun getOwnerProfileFlow(): Flow<OwnerProfileEntity?> = ownerProfileDao.getProfileFlow()
    
    suspend fun getRooms(): List<RoomEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getAllRooms(propId)
    }

    suspend fun getProfile(): OwnerProfileEntity? = ownerProfileDao.getProfile()
}
