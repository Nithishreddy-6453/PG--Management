package com.example.data.firestore.model

import com.example.data.database.FormAccessRequestEntity
import com.example.data.database.FormVersionHistoryEntity
import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.domain.model.FormVersionHistory

data class FormAccessRequestDto(
    val requestId: String = "",
    val propertyId: String = "",
    val formId: String = "",
    val formTitle: String = "",
    val requesterPgUid: String = "",
    val requesterGoogleEmail: String = "",
    val requesterName: String = "",
    val ownerPgUid: String = "",
    val ownerGoogleEmail: String = "",
    val requestedRole: String = "EDITOR",
    val status: String = "PENDING",
    val requestNote: String = "",
    val rejectionReason: String? = null,
    val notificationStatus: String = "PENDING",
    val notificationSentAt: Long? = null,
    val notificationError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val syncStatus: String = "SYNCED",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

fun FormAccessRequestEntity.toDto(): FormAccessRequestDto {
    return FormAccessRequestDto(
        requestId = requestId,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        requesterPgUid = requesterPgUid,
        requesterGoogleEmail = requesterGoogleEmail,
        requesterName = requesterName,
        ownerPgUid = ownerPgUid,
        ownerGoogleEmail = ownerGoogleEmail,
        requestedRole = requestedRole,
        status = status,
        requestNote = requestNote,
        rejectionReason = rejectionReason,
        notificationStatus = notificationStatus,
        notificationSentAt = notificationSentAt,
        notificationError = notificationError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt
    )
}

fun FormAccessRequestDto.toEntity(): FormAccessRequestEntity {
    return FormAccessRequestEntity(
        requestId = requestId,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        requesterPgUid = requesterPgUid,
        requesterGoogleEmail = requesterGoogleEmail,
        requesterName = requesterName,
        ownerPgUid = ownerPgUid,
        ownerGoogleEmail = ownerGoogleEmail,
        requestedRole = requestedRole,
        status = status,
        requestNote = requestNote,
        rejectionReason = rejectionReason,
        notificationStatus = notificationStatus,
        notificationSentAt = notificationSentAt,
        notificationError = notificationError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt
    )
}

fun FormAccessRequestEntity.toDomainModel(): FormAccessRequest {
    return FormAccessRequest(
        requestId = requestId,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        requesterPgUid = requesterPgUid,
        requesterGoogleEmail = requesterGoogleEmail,
        requesterName = requesterName,
        ownerPgUid = ownerPgUid,
        ownerGoogleEmail = ownerGoogleEmail,
        requestedRole = requestedRole,
        status = status,
        requestNote = requestNote,
        rejectionReason = rejectionReason,
        notificationStatus = notificationStatus,
        notificationSentAt = notificationSentAt,
        notificationError = notificationError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt
    )
}

fun FormAccessRequest.toDto(): FormAccessRequestDto = toEntity().toDto()

fun FormAccessRequest.toEntity(): FormAccessRequestEntity {
    return FormAccessRequestEntity(
        requestId = requestId,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        requesterPgUid = requesterPgUid,
        requesterGoogleEmail = requesterGoogleEmail,
        requesterName = requesterName,
        ownerPgUid = ownerPgUid,
        ownerGoogleEmail = ownerGoogleEmail,
        requestedRole = requestedRole,
        status = status,
        requestNote = requestNote,
        rejectionReason = rejectionReason,
        notificationStatus = notificationStatus,
        notificationSentAt = notificationSentAt,
        notificationError = notificationError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt
    )
}

data class FormVersionHistoryDto(
    val id: String = "",
    val propertyId: String = "",
    val formId: String = "",
    val formTitle: String = "",
    val responderUri: String = "",
    val editUri: String = "",
    val googleAccountEmail: String = "",
    val formVersion: Int = 1,
    val replacedAt: Long = System.currentTimeMillis(),
    val replacedByEmail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

fun FormVersionHistoryEntity.toDto(): FormVersionHistoryDto {
    return FormVersionHistoryDto(
        id = id,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        replacedAt = replacedAt,
        replacedByEmail = replacedByEmail,
        createdAt = createdAt
    )
}

fun FormVersionHistoryDto.toEntity(): FormVersionHistoryEntity {
    return FormVersionHistoryEntity(
        id = id,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        replacedAt = replacedAt,
        replacedByEmail = replacedByEmail,
        createdAt = createdAt
    )
}

fun FormVersionHistoryEntity.toDomainModel(): FormVersionHistory {
    return FormVersionHistory(
        id = id,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        replacedAt = replacedAt,
        replacedByEmail = replacedByEmail,
        createdAt = createdAt
    )
}

fun FormVersionHistory.toEntity(): FormVersionHistoryEntity {
    return FormVersionHistoryEntity(
        id = id,
        propertyId = propertyId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        replacedAt = replacedAt,
        replacedByEmail = replacedByEmail,
        createdAt = createdAt
    )
}
