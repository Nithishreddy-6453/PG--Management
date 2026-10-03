package com.example.data.firestore.model

/**
 * DTO representing a Tenant Registration Google Form configuration in Firestore.
 */
data class TenantRegistrationFormDto(
    val propertyId: String = "",
    val cloudId: String = "",
    val ownerId: String = "",
    val formId: String = "",
    val formTitle: String = "",
    val responderUri: String = "",
    val editUri: String = "",
    val googleAccountEmail: String = "",
    val formVersion: Int = 1,
    val published: Boolean = false,
    val active: Boolean = true,
    val questionMapping: String = "{}",
    val lastCheckedAt: Long = 0L,
    val lastSuccessfulCheckAt: Long = 0L,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
