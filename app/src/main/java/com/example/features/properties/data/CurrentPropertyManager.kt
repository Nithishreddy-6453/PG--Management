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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    val currentPropertyIdFlow: Flow<String> = kotlinx.coroutines.flow.merge(
        _inMemoryPropertyFlow,
        context.propertyDataStore.data.map { prefs ->
            val storedId = prefs[KEY_CURRENT_PROPERTY_ID]
            val resolved = if (!storedId.isNullOrBlank()) storedId else inMemoryPropertyId
            inMemoryPropertyId = resolved
            resolved
        }
    ).distinctUntilChanged()

    suspend fun getCurrentPropertyId(): String {
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
            } catch (_: Exception) {}
        }
    }

    suspend fun ensureDefaultProperty(): PropertyEntity {
        val ownerId = currentOwnerId
        val existing = propertyDao.getProperty(DEFAULT_PROPERTY_ID)
        if (existing != null) {
            return existing
        }
        val allProps = propertyDao.getProperties(ownerId)
        if (allProps.isNotEmpty()) {
            val first = allProps.first()
            setCurrentPropertyId(first.propertyId)
            return first
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
