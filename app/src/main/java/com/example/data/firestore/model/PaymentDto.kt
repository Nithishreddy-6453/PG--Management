package com.example.data.firestore.model

/**
 * DTO representing a Payment document in the 'payments' Firestore collection.
 */
data class PaymentDto(
    val id: String = "",
    val cloudId: String = "",
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val localId: Int = 0,
    val tenantId: Int = 0,
    val tenantName: String = "",
    val roomNumber: String = "",
    val billingMonth: String = "",
    val amount: Double = 0.0,
    val amountPaid: Double = 0.0,
    val dueDate: String = "",
    val paymentDate: String? = null,
    val paymentMode: String? = null,
    val transactionReference: String? = null,
    val remarks: String? = null,
    val status: String = "Pending",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
