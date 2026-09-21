package com.example.data.firestore.model

/**
 * DTO representing a User document in the 'users' Firestore collection.
 */
data class UserDto(
    val id: String = "",
    val ownerId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val role: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
