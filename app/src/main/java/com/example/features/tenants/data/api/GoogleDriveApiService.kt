package com.example.features.tenants.data.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GoogleDriveApiService {

    @GET("https://www.googleapis.com/drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType)",
        @Query("pageSize") pageSize: Int = 10
    ): Response<DriveFileListResponse>

    @POST("https://www.googleapis.com/drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") authHeader: String,
        @Body request: CreateFolderRequest
    ): Response<DriveFileItem>

    @POST("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFileMultipart(
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<DriveFileItem>

    @Streaming
    @GET("https://www.googleapis.com/drive/v3/files/{fileId}")
    suspend fun downloadFileMedia(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    @GET("https://www.googleapis.com/drive/v3/files/{fileId}")
    suspend fun getFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id, name, mimeType, trashed"
    ): Response<DriveFileItem>

    @PATCH("https://www.googleapis.com/drive/v3/files/{fileId}")
    suspend fun updateFileMetadata(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Body metadata: UpdateMetadataRequest
    ): Response<DriveFileItem>

    @PATCH("https://www.googleapis.com/upload/drive/v3/files/{fileId}?uploadType=multipart")
    suspend fun updateFileMultipart(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<DriveFileItem>

    @DELETE("https://www.googleapis.com/drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String
    ): Response<Unit>
}
