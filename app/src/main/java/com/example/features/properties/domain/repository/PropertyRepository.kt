package com.example.features.properties.domain.repository

import com.example.core.common.PgResult
import com.example.data.database.PropertyEntity
import kotlinx.coroutines.flow.Flow

interface PropertyRepository {
    fun getActivePropertiesFlow(): Flow<List<PropertyEntity>>
    fun getAllPropertiesFlow(): Flow<List<PropertyEntity>>
    fun getPropertyFlow(propertyId: String): Flow<PropertyEntity?>
    fun getCurrentPropertyFlow(): Flow<PropertyEntity?>
    
    suspend fun getActiveProperties(): List<PropertyEntity>
    suspend fun getAllProperties(): List<PropertyEntity>
    suspend fun getProperty(propertyId: String): PropertyEntity?
    suspend fun getCurrentProperty(): PropertyEntity?
    
    suspend fun createProperty(
        name: String,
        address: String = "",
        city: String = "",
        state: String = "",
        postalCode: String = "",
        contactNumber: String = "",
        description: String = ""
    ): PgResult<PropertyEntity>
    
    suspend fun updateProperty(property: PropertyEntity): PgResult<Unit>
    suspend fun archiveProperty(propertyId: String): PgResult<Unit>
    suspend fun deleteProperty(propertyId: String): PgResult<Unit>
    
    // Active selection
    fun getSelectedPropertyIdFlow(): Flow<String>
    suspend fun getSelectedPropertyId(): String
    suspend fun setSelectedPropertyId(propertyId: String)
    suspend fun ensureDefaultProperty(): PropertyEntity
}
