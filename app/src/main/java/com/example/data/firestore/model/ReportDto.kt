package com.example.data.firestore.model

/**
 * DTO representing a Financial or Operational Report document in the 'reports' Firestore collection.
 */
data class ReportDto(
    val id: String = "",
    val ownerId: String = "",
    val reportPeriod: String = "",
    val totalRevenue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val outstandingRent: Double = 0.0,
    val collectionRate: Double = 0.0,
    val occupancyRate: Double = 0.0,
    val generatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
