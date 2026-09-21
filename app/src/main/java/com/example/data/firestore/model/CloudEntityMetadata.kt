package com.example.data.firestore.model

/**
 * Common metadata schema required for all Cloud Firestore documents.
 */
data class CloudEntityMetadata(
    val id: String = "",
    val ownerId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
