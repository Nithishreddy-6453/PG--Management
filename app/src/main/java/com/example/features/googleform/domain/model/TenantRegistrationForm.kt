package com.example.features.googleform.domain.model

data class TenantRegistrationForm(
    val propertyId: String,
    val cloudId: String,
    val ownerId: String,
    val formId: String,
    val formTitle: String,
    val responderUri: String,
    val editUri: String,
    val googleAccountEmail: String,
    val formVersion: Int,
    val published: Boolean,
    val active: Boolean,
    val questionMapping: Map<String, String>, // key -> questionId
    val lastCheckedAt: Long,
    val lastSuccessfulCheckAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isAvailable: Boolean
        get() = active && published && formId.isNotBlank() && responderUri.isNotBlank()
}

data class GoogleAccountInfo(
    val isConnected: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val hasFormsBodyScope: Boolean = false,
    val hasResponsesReadonlyScope: Boolean = false,
    val hasDriveFileScope: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val authError: String? = null
) {
    val hasAllRequiredScopes: Boolean
        get() = isConnected && hasFormsBodyScope && hasResponsesReadonlyScope

    val needsAdditionalConsent: Boolean
        get() = isConnected && (!hasFormsBodyScope || !hasResponsesReadonlyScope)

    val needsDriveConsent: Boolean
        get() = isConnected && !hasDriveFileScope
}
