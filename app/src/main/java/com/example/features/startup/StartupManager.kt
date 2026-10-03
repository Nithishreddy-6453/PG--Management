package com.example.features.startup

import com.example.core.common.PgLogger
import com.example.core.integrity.DataIntegrityManager
import com.example.data.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.core.di.ApplicationScope
import com.example.features.properties.data.CurrentPropertyManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startup state representation.
 */
sealed interface StartupState {
    object Idle : StartupState
    data class Initializing(val progress: Float, val status: String) : StartupState
    object Ready : StartupState
    data class Failed(val reason: String) : StartupState
}

/**
 * Coordination hub responsible for initializing background tasks, logging, databases, and configuration setup.
 */
@Singleton
class StartupManager @Inject constructor(
    private val logger: PgLogger,
    private val syncCoordinator: SyncCoordinator?,
    private val dataIntegrityManager: DataIntegrityManager?,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val currentPropertyManager: CurrentPropertyManager? = null
) {
    constructor(logger: PgLogger, externalScope: CoroutineScope) : this(logger, null, null, externalScope, null)

    private val _state = MutableStateFlow<StartupState>(StartupState.Idle)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        logger.i(TAG, "StartupManager initialized.")
    }

    /**
     * Executes our standard initial bootstrap sequence asynchronously.
     */
    fun startBootstrap() {
        if (_state.value != StartupState.Idle) return

        externalScope.launch {
            try {
                logger.i(TAG, "Starting system bootstrap...")
                
                _state.value = StartupState.Initializing(0.1f, "Initializing Logger...")
                delay(100)
                
                _state.value = StartupState.Initializing(0.3f, "Verifying Session & Sync...")
                val uid = try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                } catch (e: Exception) {
                    null
                }
                if (!uid.isNullOrBlank() && syncCoordinator != null) {
                    _state.value = StartupState.Initializing(0.5f, "Checking cloud account data...")
                    syncCoordinator.handleUserSignIn(uid)
                }

                _state.value = StartupState.Initializing(0.7f, "Resolving active property...")
                currentPropertyManager?.restoreActiveProperty()

                _state.value = StartupState.Initializing(0.85f, "Verifying room & bed data integrity...")
                try {
                    dataIntegrityManager?.performSafeRepair()
                } catch (e: Exception) {
                    logger.w(TAG, "Data integrity repair non-fatal warning: ${e.message}")
                }
                
                _state.value = StartupState.Initializing(1.0f, "System ready.")
                logger.i(TAG, "Bootstrap completed successfully.")
                
                _state.value = StartupState.Ready
            } catch (e: Exception) {
                logger.e(TAG, "Fatal error encountered during system initialization", e)
                _state.value = StartupState.Failed(e.localizedMessage ?: "Unknown initialization failure")
            }
        }
    }

    companion object {
        private const val TAG = "StartupManager"
    }
}
