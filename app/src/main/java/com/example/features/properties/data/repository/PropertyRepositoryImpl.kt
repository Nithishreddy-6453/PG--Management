package com.example.features.properties.data.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.PropertyDao
import com.example.data.database.PropertyEntity
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.properties.domain.repository.PropertyRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyRepositoryImpl @Inject constructor(
    private val propertyDao: PropertyDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator? = null
) : PropertyRepository {

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: ""

    override fun getActivePropertiesFlow(): Flow<List<PropertyEntity>> {
        return propertyDao.getActivePropertiesForOwnerFlow(currentOwnerId)
    }

    override fun getAllPropertiesFlow(): Flow<List<PropertyEntity>> {
        return propertyDao.getAllPropertiesForOwnerFlow(currentOwnerId)
    }

    override fun getPropertyFlow(propertyId: String): Flow<PropertyEntity?> {
        return propertyDao.getPropertyFlow(propertyId)
    }

    override fun getCurrentPropertyFlow(): Flow<PropertyEntity?> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            propertyDao.getPropertyFlow(propId)
        }
    }

    override suspend fun getActiveProperties(): List<PropertyEntity> {
        return propertyDao.getProperties(currentOwnerId).filter { it.isActive && !it.deleted }
    }

    override suspend fun getAllProperties(): List<PropertyEntity> {
        return propertyDao.getProperties(currentOwnerId)
    }

    override suspend fun getProperty(propertyId: String): PropertyEntity? {
        return propertyDao.getProperty(propertyId)
    }

    override suspend fun getCurrentProperty(): PropertyEntity? {
        val currentId = currentPropertyManager.getCurrentPropertyId()
        return propertyDao.getProperty(currentId) ?: ensureDefaultProperty()
    }

    override suspend fun createProperty(
        name: String,
        address: String,
        city: String,
        state: String,
        postalCode: String,
        contactNumber: String,
        description: String
    ): PgResult<PropertyEntity> {
        if (name.isBlank()) {
            return PgResult.Failure(PgError.ValidationError("Property name is required"))
        }

        val propId = "prop_${java.util.UUID.randomUUID()}"
        val property = PropertyEntity(
            propertyId = propId,
            ownerId = currentOwnerId,
            propertyName = name.trim(),
            address = address.trim(),
            city = city.trim(),
            state = state.trim(),
            postalCode = postalCode.trim(),
            contactNumber = contactNumber.trim(),
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            version = 1,
            deleted = false,
            syncStatus = "PENDING_UPLOAD"
        )

        return try {
            propertyDao.insertProperty(property)
            // Auto switch to newly created property
            currentPropertyManager.setCurrentPropertyId(propId)
            syncCoordinator?.enqueueOperation("PROPERTY", propId, "CREATE")
            PgResult.Success(property)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to save property: ${e.localizedMessage}"))
        }
    }

    override suspend fun updateProperty(property: PropertyEntity): PgResult<Unit> {
        return try {
            val updated = property.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            propertyDao.updateProperty(updated)
            syncCoordinator?.enqueueOperation("PROPERTY", updated.propertyId, "UPDATE")
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to update property: ${e.localizedMessage}"))
        }
    }

    override suspend fun archiveProperty(propertyId: String): PgResult<Unit> {
        return try {
            propertyDao.archiveProperty(propertyId)
            syncCoordinator?.enqueueOperation("PROPERTY", propertyId, "UPDATE")
            
            // If current property is archived, switch to first available active property
            val currentId = currentPropertyManager.getCurrentPropertyId()
            if (currentId == propertyId) {
                val remaining = getActiveProperties()
                if (remaining.isNotEmpty()) {
                    currentPropertyManager.setCurrentPropertyId(remaining.first().propertyId)
                } else {
                    ensureDefaultProperty()
                }
            }
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to archive property: ${e.localizedMessage}"))
        }
    }

    override suspend fun deleteProperty(propertyId: String): PgResult<Unit> {
        return try {
            propertyDao.softDeleteProperty(propertyId)
            syncCoordinator?.enqueueOperation("PROPERTY", propertyId, "DELETE")
            
            val currentId = currentPropertyManager.getCurrentPropertyId()
            if (currentId == propertyId) {
                val remaining = getActiveProperties()
                if (remaining.isNotEmpty()) {
                    currentPropertyManager.setCurrentPropertyId(remaining.first().propertyId)
                } else {
                    ensureDefaultProperty()
                }
            }
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to delete property: ${e.localizedMessage}"))
        }
    }

    override fun getSelectedPropertyIdFlow(): Flow<String> {
        return currentPropertyManager.currentPropertyIdFlow
    }

    override suspend fun getSelectedPropertyId(): String {
        return currentPropertyManager.getCurrentPropertyId()
    }

    override suspend fun setSelectedPropertyId(propertyId: String) {
        currentPropertyManager.setCurrentPropertyId(propertyId)
    }

    override suspend fun ensureDefaultProperty(): PropertyEntity {
        return currentPropertyManager.ensureDefaultProperty()
    }
}
