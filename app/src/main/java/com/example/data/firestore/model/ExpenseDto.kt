package com.example.data.firestore.model

/**
 * DTO representing an Expense document in the 'expenses' Firestore collection.
 */
data class ExpenseDto(
    val id: String = "",
    val cloudId: String = "",
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val localId: Int = 0,
    val amount: Double = 0.0,
    val category: String = "",
    val date: String = "",
    val notes: String = "",
    val title: String = "",
    val paymentMethod: String = "Cash",
    val vendor: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastModifiedByDeviceId: String = ""
)
