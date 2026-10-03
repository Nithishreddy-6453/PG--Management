package com.example.data.firestore.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class TenantMediaDto(
    @get:PropertyName("cloudId") @set:PropertyName("cloudId") var cloudId: String = "",
    @get:PropertyName("ownerId") @set:PropertyName("ownerId") var ownerId: String = "",
    @get:PropertyName("propertyId") @set:PropertyName("propertyId") var propertyId: String = "",
    @get:PropertyName("tenantCloudId") @set:PropertyName("tenantCloudId") var tenantCloudId: String = "",
    @get:PropertyName("driveFileId") @set:PropertyName("driveFileId") var driveFileId: String = "",
    @get:PropertyName("driveTenantFolderId") @set:PropertyName("driveTenantFolderId") var driveTenantFolderId: String = "",
    @get:PropertyName("drivePhotosFolderId") @set:PropertyName("drivePhotosFolderId") var drivePhotosFolderId: String = "",
    @get:PropertyName("fileName") @set:PropertyName("fileName") var fileName: String = "",
    @get:PropertyName("mimeType") @set:PropertyName("mimeType") var mimeType: String = "image/jpeg",
    @get:PropertyName("sizeBytes") @set:PropertyName("sizeBytes") var sizeBytes: Long = 0L,
    @get:PropertyName("mediaType") @set:PropertyName("mediaType") var mediaType: String = "PROFILE_PHOTO",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "ACTIVE",
    @get:PropertyName("driveFolderId") @set:PropertyName("driveFolderId") var driveFolderId: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("deleted") @set:PropertyName("deleted") var deleted: Boolean = false,
    @get:PropertyName("lastModifiedByDeviceId") @set:PropertyName("lastModifiedByDeviceId") var lastModifiedByDeviceId: String = ""
)
