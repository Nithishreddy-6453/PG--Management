package com.example.data.firestore.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class PendingTenantRegistrationDto(
    @get:PropertyName("cloudId") @set:PropertyName("cloudId") var cloudId: String = "",
    @get:PropertyName("ownerId") @set:PropertyName("ownerId") var ownerId: String = "",
    @get:PropertyName("propertyId") @set:PropertyName("propertyId") var propertyId: String = "",
    @get:PropertyName("formId") @set:PropertyName("formId") var formId: String = "",
    @get:PropertyName("responseId") @set:PropertyName("responseId") var responseId: String = "",
    @get:PropertyName("submittedAt") @set:PropertyName("submittedAt") var submittedAt: Long = 0L,
    @get:PropertyName("formVersion") @set:PropertyName("formVersion") var formVersion: Int = 1,
    @get:PropertyName("fullName") @set:PropertyName("fullName") var fullName: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("emergencyName") @set:PropertyName("emergencyName") var emergencyName: String = "",
    @get:PropertyName("emergencyPhone") @set:PropertyName("emergencyPhone") var emergencyPhone: String = "",
    @get:PropertyName("emergencyRelation") @set:PropertyName("emergencyRelation") var emergencyRelation: String = "",
    @get:PropertyName("permanentAddress") @set:PropertyName("permanentAddress") var permanentAddress: String = "",
    @get:PropertyName("currentAddress") @set:PropertyName("currentAddress") var currentAddress: String = "",
    @get:PropertyName("occupation") @set:PropertyName("occupation") var occupation: String = "",
    @get:PropertyName("organization") @set:PropertyName("organization") var organization: String = "",
    @get:PropertyName("expectedJoiningDate") @set:PropertyName("expectedJoiningDate") var expectedJoiningDate: String = "",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "PENDING",
    @get:PropertyName("createdTenantCloudId") @set:PropertyName("createdTenantCloudId") var createdTenantCloudId: String = "",
    @get:PropertyName("reviewedAt") @set:PropertyName("reviewedAt") var reviewedAt: Long = 0L,
    @get:PropertyName("reviewedBy") @set:PropertyName("reviewedBy") var reviewedBy: String = "",
    @get:PropertyName("rejectionReason") @set:PropertyName("rejectionReason") var rejectionReason: String = "",
    @get:PropertyName("duplicateMatchedTenantName") @set:PropertyName("duplicateMatchedTenantName") var duplicateMatchedTenantName: String = "",
    @get:PropertyName("duplicateMatchedTenantRoom") @set:PropertyName("duplicateMatchedTenantRoom") var duplicateMatchedTenantRoom: String = "",
    @get:PropertyName("duplicateMatchedTenantStatus") @set:PropertyName("duplicateMatchedTenantStatus") var duplicateMatchedTenantStatus: String = "",
    @get:PropertyName("claimedByDeviceId") @set:PropertyName("claimedByDeviceId") var claimedByDeviceId: String = "",
    @get:PropertyName("claimedAt") @set:PropertyName("claimedAt") var claimedAt: Long = 0L,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false,
    @get:PropertyName("syncStatus") @set:PropertyName("syncStatus") var syncStatus: String = "SYNCED",
    @get:PropertyName("lastSyncedAt") @set:PropertyName("lastSyncedAt") var lastSyncedAt: Long = 0L,
    @get:PropertyName("lastModifiedByDeviceId") @set:PropertyName("lastModifiedByDeviceId") var lastModifiedByDeviceId: String = ""
)
