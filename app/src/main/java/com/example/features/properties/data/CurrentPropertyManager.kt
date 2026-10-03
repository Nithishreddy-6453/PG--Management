package com.example.features.properties.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.database.PropertyDao
import com.example.data.database.PropertyEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.propertyDataStore: DataStore<Preferences> by preferencesDataStore(name = "property_settings_prefs")

/**
 * Single source of truth for the currently active property / PG.
 * Persists the selection across app restarts, process death, and configuration changes.
 */
@Singleton
class CurrentPropertyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val propertyDao: PropertyDao,
    private val auth: FirebaseAuth? = null
) {
    companion object {
        val KEY_CURRENT_PROPERTY_ID = stringPreferencesKey("current_property_id")
        const val DEFAULT_PROPERTY_ID = "property_default"
        const val DEFAULT_PROPERTY_NAME = "Emerald Stays"
    }

    private val currentOwnerId: String
        get() = try { auth?.currentUser?.uid ?: "" } catch (_: Exception) { "" }

    @Volatile
    private var inMemoryPropertyId: String = DEFAULT_PROPERTY_ID

    private val _inMemoryPropertyFlow = kotlinx.coroutines.flow.MutableStateFlow(DEFAULT_PROPERTY_ID)

    val currentPropertyIdFlow: Flow<String> = _inMemoryPropertyFlow.asStateFlow()

    suspend fun getCurrentPropertyId(): String {
        return inMemoryPropertyId
    }

    suspend fun restoreActiveProperty(): String {
        if (inMemoryPropertyId.isNotBlank() && inMemoryPropertyId != DEFAULT_PROPERTY_ID) {
            val existing = propertyDao.getProperty(inMemoryPropertyId)
            if (existing != null && !existing.deleted && existing.isActive) {
                return inMemoryPropertyId
            }
        }

        // 1. Check local Room database properties first (instant, non-blocking)
        try {
            val ownerId = currentOwnerId
            val ownerProps = if (ownerId.isNotBlank()) propertyDao.getProperties(ownerId) else emptyList()
            val props = if (ownerProps.isNotEmpty()) ownerProps else propertyDao.getAllProperties()
            val activeProp = props.firstOrNull { !it.deleted && it.isActive }
                ?: props.firstOrNull { !it.deleted }
            if (activeProp != null) {
                inMemoryPropertyId = activeProp.propertyId
                _inMemoryPropertyFlow.value = activeProp.propertyId
                try {
                    context.propertyDataStore.edit { prefs ->
                        prefs[KEY_CURRENT_PROPERTY_ID] = activeProp.propertyId
                    }
                } catch (_: Throwable) {}
                return activeProp.propertyId
            }
        } catch (_: Throwable) {}

        // 2. Check DataStore preference if available
        try {
            val prefs = kotlinx.coroutines.withTimeoutOrNull(200) {
                context.propertyDataStore.data.first()
            }
            val stored = prefs?.get(KEY_CURRENT_PROPERTY_ID)
            if (!stored.isNullOrBlank() && stored != DEFAULT_PROPERTY_ID) {
                val prop = propertyDao.getProperty(stored)
                if (prop != null && !prop.deleted && prop.isActive) {
                    inMemoryPropertyId = stored
                    _inMemoryPropertyFlow.value = stored
                    return stored
                }
            }
        } catch (_: Throwable) {}

        return inMemoryPropertyId
    }

    suspend fun setCurrentPropertyId(propertyId: String) {
        if (propertyId.isNotBlank()) {
            inMemoryPropertyId = propertyId
            _inMemoryPropertyFlow.value = propertyId
            try {
                context.propertyDataStore.edit { prefs ->
                    prefs[KEY_CURRENT_PROPERTY_ID] = propertyId
                }
            } catch (_: Throwable) {}
        }
    }

    suspend fun ensureDefaultProperty(): PropertyEntity {
        val ownerId = currentOwnerId
        val allProps = propertyDao.getProperties(ownerId)
        val activeProp = allProps.firstOrNull { !it.deleted && it.isActive } ?: allProps.firstOrNull { !it.deleted }
        if (activeProp != null) {
            setCurrentPropertyId(activeProp.propertyId)
            return activeProp
        }
        val existing = propertyDao.getProperty(DEFAULT_PROPERTY_ID)
        if (existing != null && !existing.deleted) {
            setCurrentPropertyId(DEFAULT_PROPERTY_ID)
            return existing
        }
        val defaultProp = PropertyEntity(
            propertyId = DEFAULT_PROPERTY_ID,
            ownerId = ownerId,
            propertyName = DEFAULT_PROPERTY_NAME,
            address = "Main Road, Near Tech Park",
            city = "Bangalore",
            state = "Karnataka",
            postalCode = "560001",
            contactNumber = "+91 98765 43210",
            description = "Primary PG Facility with modern amenities",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            version = 1,
            deleted = false,
            syncStatus = "LOCAL_ONLY"
        )
        propertyDao.insertProperty(defaultProp)
        setCurrentPropertyId(DEFAULT_PROPERTY_ID)
        return defaultProp
    }
}
