package com.example.data.firestore.model

/**
 * DTO representing Application / Business Settings document in the 'settings' Firestore collection.
 */
data class SettingsDto(
    val id: String = "",
    val ownerId: String = "",
    val pgName: String = "",
    val ownerName: String = "",
    val contactNumber: String = "",
    val emailAddress: String = "",
    val address: String = "",
    val defaultMonthlyRent: Double = 0.0,
    val defaultSecurityDeposit: Double = 0.0,
    val defaultRentDueDay: Int = 1,
    val currency: String = "₹",
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val biometricEnabled: Boolean = false,
    val rentDueReminder: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
