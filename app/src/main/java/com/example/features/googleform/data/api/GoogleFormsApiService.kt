package com.example.features.googleform.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GoogleFormsApiService {

    @POST("v1/forms")
    suspend fun createForm(
        @Header("Authorization") authorization: String,
        @Body request: CreateFormRequest
    ): Response<FormResponse>

    @POST("v1/forms/{formId}:batchUpdate")
    suspend fun batchUpdateForm(
        @Header("Authorization") authorization: String,
        @Path("formId") formId: String,
        @Body request: BatchUpdateRequest
    ): Response<BatchUpdateResponse>

    @GET("v1/forms/{formId}")
    suspend fun getForm(
        @Header("Authorization") authorization: String,
        @Path("formId") formId: String
    ): Response<FormResponse>

    @GET("v1/forms/{formId}/responses")
    suspend fun listResponses(
        @Header("Authorization") authorization: String,
        @Path("formId") formId: String,
        @retrofit2.http.Query("pageToken") pageToken: String? = null,
        @retrofit2.http.Query("pageSize") pageSize: Int? = null,
        @retrofit2.http.Query("after") afterTimestamp: String? = null
    ): Response<ListFormResponsesResponse>
}
