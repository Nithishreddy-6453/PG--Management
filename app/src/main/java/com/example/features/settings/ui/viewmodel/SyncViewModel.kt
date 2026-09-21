package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.ConflictRecordEntity
import com.example.data.sync.ConflictResolutionStrategy
import com.example.data.sync.SyncCoordinator
import com.example.data.sync.SyncDiagnostics
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
    private val auth: FirebaseAuth
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

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun getDeviceId(): String = syncCoordinator.getDeviceId()

    fun getUserEmail(): String = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Not authenticated"

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
