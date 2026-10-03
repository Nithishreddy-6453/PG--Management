package com.example.features.rent.domain.model

import com.example.data.database.RentPaymentEntity

/**
 * Proration details for a tenant's stay during a specific billing month.
 */
data class ProrationDetails(
    val moveInDate: String,
    val leavingDate: String?,
    val billingMonth: String,
    val daysInMonth: Int,
    val startDay: Int,
    val endDay: Int,
    val applicableDays: Int,
    val isProrated: Boolean,
    val prorationReason: String
)

/**
 * Preview item for a single tenant during month generation.
 */
data class TenantMonthPreview(
    val tenantId: Int,
    val tenantCloudId: String,
    val tenantName: String,
    val roomNumber: String,
    val monthlyRent: Double,
    val applicableDays: Int,
    val daysInMonth: Int,
    val expectedRent: Double,
    val isProrated: Boolean,
    val prorationReason: String,
    val alreadyExists: Boolean
)

/**
 * Preview summary for an entire property's month generation.
 */
data class MonthGenerationPreview(
    val propertyId: String,
    val propertyName: String,
    val billingMonth: String,
    val daysInMonth: Int,
    val totalApplicableTenants: Int,
    val fullMonthTenants: Int,
    val proratedTenants: Int,
    val leavingTenants: Int,
    val expectedTotalRent: Double,
    val isAlreadyGenerated: Boolean,
    val existingRecordsCount: Int,
    val tenantPreviews: List<TenantMonthPreview>
)

/**
 * Financial metrics calculated from monthly rent records.
 */
data class MonthlyRentStats(
    val billingMonth: String,
    val totalExpectedRent: Double,
    val totalPaid: Double,
    val totalOutstanding: Double,
    val totalTenantsCount: Int,
    val paidTenantsCount: Int,
    val partiallyPaidTenantsCount: Int,
    val pendingTenantsCount: Int,
    val proratedTenantsCount: Int,
    val collectionPercentage: Double
)
