package com.example.features.dashboard.data

import com.example.data.database.BedAssignmentDao
import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedDao
import com.example.data.database.BedEntity
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
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DashboardRepository @Inject constructor(
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val expenseDao: ExpenseDao,
    private val bedDao: BedDao,
    private val bedAssignmentDao: BedAssignmentDao,
    private val propertyDao: PropertyDao,
    private val ownerProfileDao: OwnerProfileDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val currentBillingMonthManager: CurrentBillingMonthManager
) {
    fun getCurrentPropertyIdFlow(): Flow<String> = currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged()

    fun getCurrentPropertyFlow(): Flow<PropertyEntity?> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            propertyDao.getPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getRoomsFlow(): Flow<List<RoomEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            roomDao.getRoomsForPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getBedsFlow(): Flow<List<BedEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            bedDao.getAllBedsForPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getAssignmentsFlow(): Flow<List<BedAssignmentEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            bedAssignmentDao.getAllAssignmentsFlow(propId)
        }.distinctUntilChanged()

    fun getTenantsFlow(): Flow<List<TenantEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            tenantDao.getAllTenantsForPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getPaymentsFlow(): Flow<List<RentPaymentEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getCurrentBillingMonthFlow(): Flow<String> = currentBillingMonthManager.currentBillingMonthFlow.distinctUntilChanged()

    fun getPaymentsForCurrentBillingMonthFlow(): Flow<List<RentPaymentEntity>> =
        combine(
            currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged(),
            currentBillingMonthManager.currentBillingMonthFlow.distinctUntilChanged()
        ) { propId, billingMonth ->
            Pair(propId, billingMonth)
        }.flatMapLatest { (propId, billingMonth) ->
            rentPaymentDao.getPaymentsForPropertyAndMonthFlow(propId, billingMonth)
        }.distinctUntilChanged()

    fun getExpensesFlow(): Flow<List<ExpenseEntity>> =
        currentPropertyManager.currentPropertyIdFlow.distinctUntilChanged().flatMapLatest { propId ->
            expenseDao.getAllExpensesForPropertyFlow(propId)
        }.distinctUntilChanged()

    fun getExpensesForCurrentBillingMonthFlow(): Flow<List<ExpenseEntity>> =
        combine(
            getExpensesFlow(),
            currentBillingMonthManager.currentBillingMonthFlow.distinctUntilChanged()
        ) { expenses, billingMonth ->
            val parsedMonth = RentBillingEngine.parseBillingMonth(billingMonth)
            val monthPrefix1 = String.format("%04d-%02d", parsedMonth.year, parsedMonth.month1Based)
            expenses.filter { !it.deleted && (it.date.startsWith(monthPrefix1) || it.date.contains(parsedMonth.canonicalName, ignoreCase = true)) }
        }.distinctUntilChanged()

    fun getOwnerProfileFlow(): Flow<OwnerProfileEntity?> = ownerProfileDao.getProfileFlow()
    
    suspend fun getRooms(): List<RoomEntity> {
        val propId = currentPropertyManager.getCurrentPropertyId()
        return roomDao.getAllRooms(propId)
    }

    suspend fun getProfile(): OwnerProfileEntity? = ownerProfileDao.getProfile()
}
