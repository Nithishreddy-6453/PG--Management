package com.example.core.device

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages unique installation-specific device identity.
 *
 * CRITICAL ARCHITECTURAL DISTINCTION:
 * - Firebase UID identifies the USER ACCOUNT.
 * - deviceId identifies the SPECIFIC INSTALLATION / DEVICE.
 *
 * Persisted in SharedPreferences so it survives app restarts and background kills,
 * but changes upon fresh app installation (cleared app storage/reinstall).
 */
@Singleton
class DeviceIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Returns the stable installation identifier for this device.
     * Generates a new UUID if this is a fresh installation.
     */
    fun getDeviceId(): String {
        cachedDeviceId?.let { return it }

        synchronized(this) {
            cachedDeviceId?.let { return it }
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            cachedDeviceId = id
            return id
        }
    }

    /**
     * Tracks the last authenticated ownerId on this installation.
     * Used for detecting account switching to prevent cross-account cache leakage.
     */
    fun getLastActiveOwnerId(): String? {
        return prefs.getString(KEY_LAST_OWNER_ID, null)
    }

    fun setLastActiveOwnerId(ownerId: String?) {
        if (ownerId == null) {
            prefs.edit().remove(KEY_LAST_OWNER_ID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_OWNER_ID, ownerId).apply()
        }
    }

    /**
     * Checks whether the initial cloud bootstrap has completed for this owner on this device.
     */
    fun isDeviceBootstrapCompleted(ownerId: String): Boolean {
        if (ownerId.isBlank()) return false
        return prefs.getBoolean(KEY_BOOTSTRAP_DONE_PREFIX + ownerId, false)
    }

    /**
     * Persists the bootstrap completion state for this owner on this device.
     */
    fun setDeviceBootstrapCompleted(ownerId: String, completed: Boolean) {
        if (ownerId.isBlank()) return
        prefs.edit().putBoolean(KEY_BOOTSTRAP_DONE_PREFIX + ownerId, completed).apply()
    }

    /**
     * Clears bootstrap state for an owner (e.g. during logout or account deletion).
     */
    fun clearDeviceBootstrap(ownerId: String) {
        if (ownerId.isBlank()) return
        prefs.edit().remove(KEY_BOOTSTRAP_DONE_PREFIX + ownerId).apply()
    }

    /**
     * Human-readable device model for diagnostics and conflict logs.
     */
    fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Android"
        val model = android.os.Build.MODEL ?: "Device"
        return "$manufacturer $model (${getDeviceId().takeLast(4)})"
    }

    companion object {
        private const val PREFS_NAME = "pg_device_identity_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_OWNER_ID = "last_active_owner_id"
        private const val KEY_BOOTSTRAP_DONE_PREFIX = "bootstrap_completed_"
    }
}
