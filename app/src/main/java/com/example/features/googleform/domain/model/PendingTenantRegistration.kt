package com.example.features.googleform.domain.model

enum class PendingRegistrationStatus(val dbValue: String) {
    PENDING("PENDING"),
    NEEDS_REVIEW("NEEDS_REVIEW"),
    DUPLICATE("DUPLICATE"),
    ACCEPTING("ACCEPTING"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED");

    companion object {
        fun fromString(value: String): PendingRegistrationStatus {
            return entries.find { it.dbValue.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

data class PendingTenantRegistration(
    val cloudId: String,
    val ownerId: String,
    val propertyId: String,
    val formId: String,
    val responseId: String,
    val submittedAt: Long,
    val formVersion: Int,
    val fullName: String,
    val phone: String,
    val email: String,
    val emergencyName: String,
    val emergencyPhone: String,
    val emergencyRelation: String,
    val permanentAddress: String,
    val currentAddress: String,
    val occupation: String,
    val organization: String,
    val expectedJoiningDate: String,
    val notes: String,
    val status: PendingRegistrationStatus,
    val createdTenantCloudId: String,
    val reviewedAt: Long,
    val reviewedBy: String,
    val rejectionReason: String,
    val duplicateMatchedTenantName: String,
    val duplicateMatchedTenantRoom: String,
    val duplicateMatchedTenantStatus: String,
    val claimedByDeviceId: String,
    val claimedAt: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isPending: Boolean
        get() = status == PendingRegistrationStatus.PENDING || status == PendingRegistrationStatus.NEEDS_REVIEW || status == PendingRegistrationStatus.DUPLICATE

    val isAccepted: Boolean
        get() = status == PendingRegistrationStatus.ACCEPTED

    val isRejected: Boolean
        get() = status == PendingRegistrationStatus.REJECTED

    val hasDuplicateWarning: Boolean
        get() = status == PendingRegistrationStatus.DUPLICATE || duplicateMatchedTenantName.isNotBlank()
}
