package com.example.data.firestore.model

/**
 * DTO representing a Room document in the 'rooms' Firestore collection.
 */
data class RoomDto(
    val id: String = "",
    val ownerId: String = "",
    val roomNumber: String = "",
    val floor: String = "",
    val capacity: Int = 0,
    val ratePerBed: Double = 0.0,
    val roomType: String = "AC",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
