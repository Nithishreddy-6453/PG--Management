package com.example.features.rooms.domain.repository

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.BedEntity
import com.example.data.database.BedAssignmentEntity
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import kotlinx.coroutines.flow.Flow

interface RoomRepository {
    fun getRoomsFlow(): Flow<List<RoomEntity>>
    fun getRoomFlow(roomNumber: String): Flow<RoomEntity?>
    fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>>
    fun getPaymentsForRoomFlow(roomNumber: String): Flow<List<RentPaymentEntity>>
    fun getBedsForRoomFlow(roomNumber: String): Flow<List<BedEntity>>
    fun getBedAssignmentsForRoomFlow(roomNumber: String): Flow<List<BedAssignmentEntity>>
    fun getRoomsSummariesFlow(): Flow<List<RoomSummary>>
    fun getRoomSummaryFlow(roomNumber: String): Flow<RoomSummary?>
    
    suspend fun getRoom(roomNumber: String): RoomEntity?
    suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity>
    suspend fun getBedsForRoom(roomNumber: String): List<BedEntity>
    suspend fun getBedAssignmentsForRoom(roomNumber: String): List<BedAssignmentEntity>
    suspend fun insertRoom(room: RoomEntity)
    suspend fun deleteRoom(roomNumber: String)
    suspend fun assignTenantToBed(roomNumber: String, bedId: String, tenantId: Int, startDate: String, agreedRent: Double): RoomValidationResult
    suspend fun transferTenant(tenantId: Int, newRoomNumber: String, newBedId: String, transferDate: String, newAgreedRent: Double): RoomValidationResult
    suspend fun vacateTenant(tenantId: Int, leavingDate: String): RoomValidationResult
    suspend fun blockBed(roomNumber: String, bedId: String, blocked: Boolean): RoomValidationResult
}
