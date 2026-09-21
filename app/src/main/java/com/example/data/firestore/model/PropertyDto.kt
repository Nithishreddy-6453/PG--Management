package com.example.data.firestore.model

/**
 * DTO representing a Property document in the 'properties' Firestore collection.
 */
data class PropertyDto(
    val id: String = "",
    val ownerId: String = "",
    val pgName: String = "",
    val propertyName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val upiId: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val contactNumber: String = "",
    val description: String = "",
    val totalFloors: Int = 0,
    val totalRooms: Int = 0,
    val currency: String = "₹",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
