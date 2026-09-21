package com.example.features.rooms.domain.repository

import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.rooms.domain.model.RoomSummary
import kotlinx.coroutines.flow.Flow

interface RoomRepository {
    fun getRoomsFlow(): Flow<List<RoomEntity>>
    fun getRoomFlow(roomNumber: String): Flow<RoomEntity?>
    fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>>
    fun getPaymentsForRoomFlow(roomNumber: String): Flow<List<RentPaymentEntity>>
    fun getRoomsSummariesFlow(): Flow<List<RoomSummary>>
    fun getRoomSummaryFlow(roomNumber: String): Flow<RoomSummary?>
    
    suspend fun getRoom(roomNumber: String): RoomEntity?
    suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity>
    suspend fun insertRoom(room: RoomEntity)
    suspend fun deleteRoom(roomNumber: String)
}
