package com.example.features.tenants.domain.repository

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import kotlinx.coroutines.flow.Flow

interface TenantRepository {
    fun getAllTenantsFlow(): Flow<List<TenantEntity>>
    suspend fun getTenantById(id: Int): TenantEntity?
    fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>>
    suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity>
    suspend fun insertTenant(tenant: TenantEntity): Long
    suspend fun updateTenant(tenant: TenantEntity)
    suspend fun deleteTenant(id: Int)
    
    // Room operations for assignments & validation
    suspend fun getRoom(roomNumber: String): RoomEntity?
    suspend fun getAllRooms(): List<RoomEntity>
    fun getAllRoomsFlow(): Flow<List<RoomEntity>>
    
    // Rent payments for vacation and checkout lifecycle
    suspend fun insertRentPayment(payment: RentPaymentEntity)
    suspend fun deletePaymentsForTenant(tenantId: Int)
    fun getPaymentsForTenantFlow(tenantId: Int): Flow<List<RentPaymentEntity>>
}
