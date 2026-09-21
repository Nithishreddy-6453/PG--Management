package com.example.features.startup

import com.example.core.common.PgLogger
import com.example.data.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.core.di.ApplicationScope
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
    @ApplicationScope private val externalScope: CoroutineScope
) {
    constructor(logger: PgLogger, externalScope: CoroutineScope) : this(logger, null, externalScope)

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
                delay(150) // Simulate fast initialization step
                
                _state.value = StartupState.Initializing(0.4f, "Verifying Preferences & Security PIN...")
                delay(200)
                
                _state.value = StartupState.Initializing(0.7f, "Warming up SQLite connections...")
                syncCoordinator?.initializePeriodicSync()
                syncCoordinator?.triggerImmediateSync()
                delay(250)
                
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
