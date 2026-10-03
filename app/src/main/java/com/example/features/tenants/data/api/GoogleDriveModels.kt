package com.example.features.tenants.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFileItem> = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "mimeType") val mimeType: String = "",
    @Json(name = "size") val size: String? = null,
    @Json(name = "thumbnailLink") val thumbnailLink: String? = null,
    @Json(name = "webContentLink") val webContentLink: String? = null,
    @Json(name = "trashed") val trashed: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class UpdateMetadataRequest(
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class CreateFolderRequest(
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String = "application/vnd.google-apps.folder",
    @Json(name = "parents") val parents: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class FileMetadataRequest(
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String = "image/jpeg",
    @Json(name = "parents") val parents: List<String>? = null
)
