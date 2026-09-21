package com.example.data.sync

import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.core.di.ApplicationScope
import com.example.data.firestore.repository.FirestoreExpenseRepository
import com.example.data.firestore.repository.FirestorePaymentRepository
import com.example.data.firestore.repository.FirestorePropertyRepository
import com.example.data.firestore.repository.FirestoreRoomRepository
import com.example.data.firestore.repository.FirestoreTenantRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages active Firestore snapshot listeners to push remote changes into Room in real-time.
 * Strictly adheres to: ROOM REMAINS THE LOCAL SOURCE OF TRUTH.
 *
 * Flow:
 * Remote change -> Firestore listener -> SyncEngine.reconcile -> Room database -> Compose UI via Flow
 */
@Singleton
class RealtimeSyncManager @Inject constructor(
    private val firestorePropertyRepository: FirestorePropertyRepository,
    private val firestoreRoomRepository: FirestoreRoomRepository,
    private val firestoreTenantRepository: FirestoreTenantRepository,
    private val firestorePaymentRepository: FirestorePaymentRepository,
    private val firestoreExpenseRepository: FirestoreExpenseRepository,
    private val syncEngine: SyncEngine,
    private val auth: FirebaseAuth,
    private val logger: PgLogger,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var listeningJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun startListening(ownerId: String? = auth.currentUser?.uid) {
        if (ownerId.isNullOrBlank()) {
            logger.i(TAG, "Cannot start realtime sync: User is not authenticated")
            stopListening()
            return
        }

        if (_isListening.value && listeningJob?.isActive == true) {
            logger.d(TAG, "Realtime listeners already active for user: $ownerId")
            return
        }

        stopListening()
        logger.i(TAG, "Starting realtime Firestore snapshot listeners for owner: $ownerId")
        _isListening.value = true

        listeningJob = scope.launch {
            // 0. Observe Properties
            launch {
                firestorePropertyRepository.observeProperties(ownerId, includeDeleted = true).collect { result ->
                    when (result) {
                        is PgResult.Success -> {
                            logger.d(TAG, "Realtime snapshot: Received ${result.data.size} properties")
                            syncEngine.reconcileRemoteProperties(result.data, ownerId)
                        }
                        is PgResult.Failure -> {
                            logger.w(TAG, "Property observation error: ${result.error.message}")
                        }
                    }
                }
            }

            // 1. Observe Rooms
            launch {
                firestoreRoomRepository.observeRooms(ownerId, includeDeleted = true).collect { result ->
                    when (result) {
                        is PgResult.Success -> {
                            logger.d(TAG, "Realtime snapshot: Received ${result.data.size} rooms")
                            syncEngine.reconcileRemoteRooms(result.data, ownerId)
                        }
                        is PgResult.Failure -> {
                            logger.w(TAG, "Room observation error: ${result.error.message}")
                        }
                    }
                }
            }

            // 2. Observe Tenants
            launch {
                firestoreTenantRepository.observeTenants(ownerId, includeDeleted = true).collect { result ->
                    when (result) {
                        is PgResult.Success -> {
                            logger.d(TAG, "Realtime snapshot: Received ${result.data.size} tenants")
                            syncEngine.reconcileRemoteTenants(result.data, ownerId)
                        }
                        is PgResult.Failure -> {
                            logger.w(TAG, "Tenant observation error: ${result.error.message}")
                        }
                    }
                }
            }

            // 3. Observe Payments
            launch {
                firestorePaymentRepository.observePayments(ownerId, includeDeleted = true).collect { result ->
                    when (result) {
                        is PgResult.Success -> {
                            logger.d(TAG, "Realtime snapshot: Received ${result.data.size} payments")
                            syncEngine.reconcileRemotePayments(result.data, ownerId)
                        }
                        is PgResult.Failure -> {
                            logger.w(TAG, "Payment observation error: ${result.error.message}")
                        }
                    }
                }
            }

            // 4. Observe Expenses
            launch {
                firestoreExpenseRepository.observeExpenses(ownerId, includeDeleted = true).collect { result ->
                    when (result) {
                        is PgResult.Success -> {
                            logger.d(TAG, "Realtime snapshot: Received ${result.data.size} expenses")
                            syncEngine.reconcileRemoteExpenses(result.data, ownerId)
                        }
                        is PgResult.Failure -> {
                            logger.w(TAG, "Expense observation error: ${result.error.message}")
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        if (listeningJob != null || _isListening.value) {
            logger.i(TAG, "Stopping all realtime Firestore snapshot listeners")
            listeningJob?.cancel()
            listeningJob = null
            _isListening.value = false
        }
    }

    companion object {
        private const val TAG = "RealtimeSyncManager"
    }
}
