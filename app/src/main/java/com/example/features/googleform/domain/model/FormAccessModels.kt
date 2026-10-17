package com.example.features.googleform.domain.model

/**
 * Access request created when a user on Mobile B (different Google account)
 * requests Google Forms editing access from the original form owner on Mobile A.
 */
data class FormAccessRequest(
    val requestId: String,
    val propertyId: String,
    val formId: String,
    val formTitle: String,
    val requesterPgUid: String,
    val requesterGoogleEmail: String,
    val requesterName: String,
    val ownerPgUid: String,
    val ownerGoogleEmail: String,
    val requestedRole: String = "EDITOR", // "EDITOR" or "VIEWER"
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED", "EXPIRED", "FAILED"
    val requestNote: String = "",
    val rejectionReason: String? = null,
    val notificationStatus: String = "PENDING", // "PENDING", "SENT", "FAILED"
    val notificationSentAt: Long? = null,
    val notificationError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
) {
    val isPending: Boolean
        get() = status.equals("PENDING", ignoreCase = true)

    val isApproved: Boolean
        get() = status.equals("APPROVED", ignoreCase = true) || status.equals("ACCEPTED", ignoreCase = true)

    val isRejected: Boolean
        get() = status.equals("REJECTED", ignoreCase = true)

    val isFailed: Boolean
        get() = status.equals("FAILED", ignoreCase = true)
}

/**
 * Audit log of replaced/historical Google Forms for a PG property.
 */
data class FormVersionHistory(
    val id: String,
    val propertyId: String,
    val formId: String,
    val formTitle: String,
    val responderUri: String,
    val editUri: String,
    val googleAccountEmail: String,
    val formVersion: Int,
    val replacedAt: Long = System.currentTimeMillis(),
    val replacedByEmail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
