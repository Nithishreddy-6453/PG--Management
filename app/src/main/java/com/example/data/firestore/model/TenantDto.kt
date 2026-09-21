package com.example.data.firestore.model

/**
 * DTO representing a Tenant document in the 'tenants' Firestore collection.
 */
data class TenantDto(
    val id: String = "",
    val cloudId: String = "",
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val localId: Int = 0,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val emergencyContact: String = "",
    val roomNumber: String = "",
    val bedId: String = "",
    val monthlyRent: Double = 0.0,
    val securityDeposit: Double = 0.0,
    val moveInDate: String = "",
    val isKycUploaded: Boolean = false,
    val kycDocType: String = "",
    val alternateContact: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val occupation: String = "",
    val companyOrCollege: String = "",
    val advancePaid: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
