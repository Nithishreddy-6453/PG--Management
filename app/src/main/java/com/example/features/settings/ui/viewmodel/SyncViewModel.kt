package com.example.features.settings.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.ConflictRecordEntity
import com.example.data.database.SyncOperationEntity
import com.example.data.database.TenantDao
import com.example.data.sync.ConflictResolutionStrategy
import com.example.data.sync.SyncCoordinator
import com.example.data.sync.SyncDiagnostics
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.tenants.domain.repository.TenantMediaRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val auth: FirebaseAuth,
    private val googleAuthManager: GoogleFormsAuthManager,
    private val tenantDao: TenantDao,
    private val tenantMediaRepository: TenantMediaRepository
) : ViewModel() {

    val diagnostics: StateFlow<SyncDiagnostics> = syncCoordinator.syncDiagnosticsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncDiagnostics(
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 0,
                isSyncing = false,
                lastSyncedAt = 0L,
                lastError = null
            )
        )

    val conflicts: StateFlow<List<ConflictRecordEntity>> = syncCoordinator.conflictRecordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val syncOperations: StateFlow<List<SyncOperationEntity>> = syncCoordinator.syncOperationsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isDriveSyncing = MutableStateFlow(false)
    val isDriveSyncing: StateFlow<Boolean> = _isDriveSyncing.asStateFlow()

    fun getDeviceId(): String = syncCoordinator.getDeviceId()

    fun getUserEmail(): String = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Not authenticated"

    fun hasDriveScope(): Boolean = googleAuthManager.hasDriveScope()

    fun getDriveConsentIntent(): Intent = googleAuthManager.getDriveConsentIntent()

    fun handleGoogleConsentResult(data: Intent?) {
        viewModelScope.launch {
            val result = googleAuthManager.handleSignInResult(data)
            if (result is PgResult.Success) {
                if (result.data.hasDriveFileScope) {
                    _actionMessage.value = "Google Drive authorization successful! Syncing media..."
                    syncAllDriveMedia()
                } else {
                    _actionMessage.value = "Google Drive scope permission was not granted."
                }
            } else if (result is PgResult.Failure) {
                _actionMessage.value = "Authorization failed: ${result.error.message}"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _actionMessage.value = "Starting manual sync..."
            val res = syncCoordinator.syncNow()
            _actionMessage.value = when (res) {
                is PgResult.Success -> "Synchronization completed"
                is PgResult.Failure -> "Sync failed: ${res.error.message}"
            }
        }
    }

    fun syncAllDriveMedia() {
        viewModelScope.launch {
            if (!googleAuthManager.hasDriveScope()) {
                _actionMessage.value = "Google Drive authorization required."
                return@launch
            }
            _isDriveSyncing.value = true
            _actionMessage.value = "Scanning Google Drive and syncing tenant profile photos..."
            try {
                val tenants = tenantDao.getAllTenants()
                var syncedPhotosCount = 0
                for (tenant in tenants) {
                    if (tenant.cloudId.isNotBlank()) {
                        tenantMediaRepository.syncTenantDriveFolder(tenant.cloudId)
                        val res = tenantMediaRepository.fetchAndCachePhotoIfMissing(tenant.cloudId)
                        if (res is PgResult.Success && res.data != null) {
                            syncedPhotosCount++
                        }
                    }
                }
                _actionMessage.value = "Drive Photo Sync completed! Synced $syncedPhotosCount photo(s)."
            } catch (e: Exception) {
                _actionMessage.value = "Drive Media Sync failed: ${e.localizedMessage}"
            } finally {
                _isDriveSyncing.value = false
            }
        }
    }

    fun resolveConflict(conflictId: String, strategy: ConflictResolutionStrategy) {
        viewModelScope.launch {
            _actionMessage.value = "Resolving conflict..."
            val res = syncCoordinator.resolveConflict(conflictId, strategy)
            _actionMessage.value = when (res) {
                is PgResult.Success -> "Conflict resolved successfully"
                is PgResult.Failure -> "Failed to resolve conflict: ${res.error.message}"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
