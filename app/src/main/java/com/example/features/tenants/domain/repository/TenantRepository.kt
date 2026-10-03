package com.example.features.tenants.domain.repository

import com.example.data.database.BedAssignmentEntity
import com.example.data.database.BedEntity
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
    suspend fun insertRoom(room: RoomEntity)
    suspend fun getCurrentPropertyId(): String

    // Bed operations for assignments & validation
    suspend fun getBed(roomNumber: String, bedId: String): BedEntity?
    suspend fun getBedsForRoom(roomNumber: String): List<BedEntity>
    suspend fun insertBed(bed: BedEntity)
    suspend fun updateBed(bed: BedEntity)
    suspend fun getActiveAssignmentForBed(roomNumber: String, bedId: String): BedAssignmentEntity?
    suspend fun getActiveAssignmentForTenant(tenantId: Int): BedAssignmentEntity?
    suspend fun insertBedAssignment(assignment: BedAssignmentEntity)
    suspend fun updateBedAssignment(assignment: BedAssignmentEntity)
    
    // Rent payments for vacation and checkout lifecycle
    suspend fun insertRentPayment(payment: RentPaymentEntity)
    suspend fun deletePaymentsForTenant(tenantId: Int)
    fun getPaymentsForTenantFlow(tenantId: Int): Flow<List<RentPaymentEntity>>
}
