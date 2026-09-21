package com.example.data.sync

import androidx.room.withTransaction
import com.example.core.common.PgError
import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.core.device.DeviceIdentityManager
import com.example.data.database.AppDatabase
import com.example.data.database.ConflictRecordDao
import com.example.data.database.ConflictRecordEntity
import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileDao
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.PropertyDao
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentDao
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomDao
import com.example.data.database.RoomEntity
import com.example.data.database.SyncOperationEntity
import com.example.data.database.SyncQueueDao
import com.example.data.database.TenantDao
import com.example.data.database.TenantEntity
import com.example.data.firestore.mapper.toDto
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.mapper.toOwnerProfileEntity
import com.example.data.firestore.mapper.toPropertyDto
import com.example.data.firestore.model.ExpenseDto
import com.example.data.firestore.model.PaymentDto
import com.example.data.firestore.model.PropertyDto
import com.example.data.firestore.model.RoomDto
import com.example.data.firestore.model.TenantDto
import com.example.data.firestore.repository.FirestoreExpenseRepository
import com.example.data.firestore.repository.FirestorePaymentRepository
import com.example.data.firestore.repository.FirestorePropertyRepository
import com.example.data.firestore.repository.FirestoreRoomRepository
import com.example.data.firestore.repository.FirestoreSettingsRepository
import com.example.data.firestore.repository.FirestoreTenantRepository
import com.example.data.firestore.repository.FirestoreUserRepository
import com.example.features.properties.data.CurrentPropertyManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val database: AppDatabase,
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val rentPaymentDao: RentPaymentDao,
    private val expenseDao: ExpenseDao,
    private val ownerProfileDao: OwnerProfileDao,
    private val propertyDao: PropertyDao,
    private val syncQueueDao: SyncQueueDao,
    private val conflictRecordDao: ConflictRecordDao,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val firestoreRoomRepository: FirestoreRoomRepository,
    private val firestoreTenantRepository: FirestoreTenantRepository,
    private val firestorePaymentRepository: FirestorePaymentRepository,
    private val firestoreExpenseRepository: FirestoreExpenseRepository,
    private val firestorePropertyRepository: FirestorePropertyRepository,
    private val firestoreSettingsRepository: FirestoreSettingsRepository,
    private val firestoreUserRepository: FirestoreUserRepository,
    private val currentPropertyManager: CurrentPropertyManager,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver,
    private val logger: PgLogger
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(System.currentTimeMillis())
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val pendingCountFlow = syncQueueDao.getPendingCountFlow()
    val failedCountFlow = syncQueueDao.getFailedCountFlow()
    val conflictCountFlow = conflictRecordDao.getConflictCountFlow()
    val conflictRecordsFlow: Flow<List<ConflictRecordEntity>> = conflictRecordDao.getActiveConflictsFlow()

    private val currentOwnerId: String?
        get() = try {
            auth.currentUser?.uid
        } catch (e: Exception) {
            null
        }

    suspend fun isLocalDatabaseEmpty(): Boolean {
        return try {
            val rooms = roomDao.getAllRooms()
            val tenants = tenantDao.getAllTenants()
            val payments = rentPaymentDao.getAllPaymentsSync()
            val expenses = expenseDao.getAllExpensesSync()
            rooms.isEmpty() && tenants.isEmpty() && payments.isEmpty() && expenses.isEmpty()
        } catch (e: Exception) {
            logger.w(TAG, "Failed checking if local database is empty: ${e.message}")
            false
        }
    }

    /**
     * Inspects Firestore to determine whether an existing account has cloud data.
     * Returns true if properties, rooms, tenants, user profile, or settings exist for this UID.
     */
    suspend fun hasCloudData(ownerId: String): Boolean {
        if (ownerId.isBlank()) return false
        return try {
            // 1. Check properties collection
            val propRes = firestorePropertyRepository.getAllProperties(ownerId, includeDeleted = false)
            if (propRes is PgResult.Success && propRes.data.isNotEmpty()) {
                logger.i(TAG, "Cloud data check: Found ${propRes.data.size} properties for $ownerId")
                return true
            }

            // 2. Check rooms collection
            val roomRes = firestoreRoomRepository.getAllRooms(ownerId, includeDeleted = false)
            if (roomRes is PgResult.Success && roomRes.data.isNotEmpty()) {
                logger.i(TAG, "Cloud data check: Found ${roomRes.data.size} rooms for $ownerId")
                return true
            }

            // 3. Check tenants collection
            val tenantRes = firestoreTenantRepository.getAllTenants(ownerId, includeDeleted = false)
            if (tenantRes is PgResult.Success && tenantRes.data.isNotEmpty()) {
                logger.i(TAG, "Cloud data check: Found ${tenantRes.data.size} tenants for $ownerId")
                return true
            }

            // 4. Check user profile
            val userRes = firestoreUserRepository.getUser(ownerId)
            if (userRes is PgResult.Success && userRes.data != null) {
                logger.i(TAG, "Cloud data check: Found user profile for $ownerId")
                return true
            }

            // 5. Check main property document
            val mainPropRes = firestorePropertyRepository.getProperty("prop_$ownerId")
            if (mainPropRes is PgResult.Success && mainPropRes.data != null) {
                logger.i(TAG, "Cloud data check: Found main property document for $ownerId")
                return true
            }

            logger.i(TAG, "Cloud data check: No cloud data found for $ownerId")
            false
        } catch (e: Exception) {
            logger.w(TAG, "Cloud data check error for $ownerId: ${e.message}")
            false
        }
    }

    /**
     * ONE-TIME INITIAL CLOUD BOOTSTRAP:
     * Downloads existing account and property data from Firestore directly into local Room.
     * CRITICAL: NEVER uploads local data during bootstrap. Cloud data wins.
     */
    suspend fun performInitialCloudBootstrap(ownerId: String): PgResult<Unit> {
        if (ownerId.isBlank()) {
            return PgResult.Failure(PgError.SecurityError("Cannot bootstrap without valid ownerId"))
        }

        _isSyncing.value = true
        _lastError.value = null
        logger.i(TAG, "Initiating ONE-TIME Initial Cloud Bootstrap for owner $ownerId on device ${deviceIdentityManager.getDeviceId()}")

        return try {
            // 1. Wipe any local dummy/pre-populated data to prevent pollution or conflicts
            database.clearAllUserData()

            // 2. Download and insert Properties
            when (val propRes = firestorePropertyRepository.getAllProperties(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val props = propRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${props.size} properties from cloud")
                    for (dto in props) {
                        propertyDao.insertProperty(
                            dto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = dto.version,
                                updatedAt = dto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = dto.deleted
                            )
                        )
                    }
                    val activeProp = props.firstOrNull { !it.deleted && it.isActive } ?: props.firstOrNull { !it.deleted }
                    if (activeProp != null) {
                        currentPropertyManager.setCurrentPropertyId(activeProp.id)
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading properties: ${propRes.error.message}")
            }

            // 3. Download and insert Rooms
            when (val roomRes = firestoreRoomRepository.getAllRooms(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val rooms = roomRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${rooms.size} rooms from cloud")
                    for (dto in rooms) {
                        roomDao.insertRoom(
                            dto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = dto.version,
                                updatedAt = dto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = dto.deleted
                            )
                        )
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading rooms: ${roomRes.error.message}")
            }

            // 4. Download and insert Tenants
            when (val tenantRes = firestoreTenantRepository.getAllTenants(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val tenants = tenantRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${tenants.size} tenants from cloud")
                    for (dto in tenants) {
                        tenantDao.insertTenant(
                            dto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = dto.version,
                                updatedAt = dto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = dto.deleted
                            )
                        )
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading tenants: ${tenantRes.error.message}")
            }

            // 5. Download and insert Rent Payments
            when (val paymentRes = firestorePaymentRepository.getAllPayments(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val payments = paymentRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${payments.size} rent payments from cloud")
                    for (dto in payments) {
                        rentPaymentDao.insertPayment(
                            dto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = dto.version,
                                updatedAt = dto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = dto.deleted
                            )
                        )
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading payments: ${paymentRes.error.message}")
            }

            // 6. Download and insert Expenses
            when (val expenseRes = firestoreExpenseRepository.getAllExpenses(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val expenses = expenseRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${expenses.size} expenses from cloud")
                    for (dto in expenses) {
                        expenseDao.insertExpense(
                            dto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = dto.version,
                                updatedAt = dto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = dto.deleted
                            )
                        )
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading expenses: ${expenseRes.error.message}")
            }

            // 7. Download and insert Owner Profile / Property Profile
            val propertyMainRes = firestorePropertyRepository.getProperty("property_main")
            if (propertyMainRes is PgResult.Success && propertyMainRes.data != null) {
                val prop = propertyMainRes.data
                ownerProfileDao.insertProfile(
                    prop.toOwnerProfileEntity().copy(
                        ownerId = ownerId,
                        syncStatus = "SYNCED",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val userRes = firestoreUserRepository.getUser(ownerId)
                if (userRes is PgResult.Success && userRes.data != null) {
                    val u = userRes.data
                    ownerProfileDao.insertProfile(
                        OwnerProfileEntity(
                            id = 1,
                            pgName = "My PG",
                            ownerName = if (u.displayName.isNotBlank()) u.displayName else "Owner",
                            phone = u.phone,
                            upiId = "",
                            pinCode = "",
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            // 8. Wipe any leftover pending upload queue items from pre-login state
            syncQueueDao.clearAll()

            // 9. Mark persistent bootstrap state as completed
            deviceIdentityManager.setDeviceBootstrapCompleted(ownerId, true)
            _lastSyncedAt.value = System.currentTimeMillis()
            logger.i(TAG, "ONE-TIME Initial Cloud Bootstrap finished successfully for owner $ownerId")
            PgResult.Success(Unit)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Initial bootstrap error"
            logger.e(TAG, "Initial Cloud Bootstrap failed: $errorMsg", e)
            _lastError.value = errorMsg
            PgResult.Failure(PgError.UnknownError(errorMsg, e))
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncNow(): PgResult<Unit> {
        val ownerId = currentOwnerId
        if (ownerId.isNullOrBlank()) {
            logger.i(TAG, "Sync skipped: User not authenticated")
            return PgResult.Success(Unit)
        }

        if (!deviceIdentityManager.isDeviceBootstrapCompleted(ownerId)) {
            logger.w(TAG, "Sync skipped: Initial cloud bootstrap is not yet completed for owner $ownerId")
            return PgResult.Success(Unit)
        }

        if (_isSyncing.value) {
            logger.i(TAG, "Sync already in progress")
            return PgResult.Success(Unit)
        }

        _isSyncing.value = true
        _lastError.value = null
        logger.i(TAG, "Starting synchronization for user: $ownerId [Device: ${deviceIdentityManager.getDeviceId()}]")

        return try {
            // 1. Upload local changes queued in sync_queue
            uploadPendingChanges(ownerId)

            // 2. Download remote updates from Firestore
            downloadRemoteChanges(ownerId)

            _lastSyncedAt.value = System.currentTimeMillis()
            logger.i(TAG, "Synchronization completed successfully")
            PgResult.Success(Unit)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown synchronization error"
            logger.e(TAG, "Synchronization failed: $errorMsg", e)
            _lastError.value = errorMsg
            PgResult.Failure(PgError.UnknownError(errorMsg, e))
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun uploadPendingChanges(ownerId: String) {
        val pendingOps = syncQueueDao.getPendingOperations()
        logger.i(TAG, "Processing ${pendingOps.size} pending upload operations")

        for (op in pendingOps) {
            try {
                syncQueueDao.update(op.copy(status = "SYNCING", lastAttemptAt = System.currentTimeMillis()))
                when (op.entityType) {
                    "ROOM" -> processRoomUpload(op, ownerId)
                    "TENANT" -> processTenantUpload(op, ownerId)
                    "PAYMENT" -> processPaymentUpload(op, ownerId)
                    "EXPENSE" -> processExpenseUpload(op, ownerId)
                    "PROPERTY" -> processPropertyUpload(op, ownerId)
                    "PROFILE" -> processProfileUpload(op, ownerId)
                    else -> logger.w(TAG, "Unknown entity type in sync queue: ${op.entityType}")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed uploading operation ${op.id} (${op.entityType}): ${e.message}", e)
                syncQueueDao.update(
                    op.copy(
                        status = "FAILED",
                        retryCount = op.retryCount + 1,
                        error = e.localizedMessage ?: "Upload failed",
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun processRoomUpload(op: SyncOperationEntity, ownerId: String) {
        val room = roomDao.getRoomIncludingDeleted(op.entityId)
        if (room == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        if (op.operationType == "DELETE" || room.deleted) {
            when (val res = firestoreRoomRepository.deleteRoom(room.roomNumber, myDeviceId)) {
                is PgResult.Success -> {
                    roomDao.updateRoom(
                        room.copy(
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("ROOM", room.roomNumber)
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val nextVersion = room.version + 1
            val now = System.currentTimeMillis()
            val dto = room.toDto(ownerId, myDeviceId).copy(
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreRoomRepository.saveRoom(dto)) {
                is PgResult.Success -> {
                    roomDao.updateRoom(
                        room.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("ROOM", room.roomNumber)
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        }
    }

    private suspend fun processTenantUpload(op: SyncOperationEntity, ownerId: String) {
        val localId = op.entityId.toIntOrNull() ?: return
        val tenant = tenantDao.getTenantByIdIncludingDeleted(localId)
        if (tenant == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = "tenant_$localId"
        if (op.operationType == "DELETE" || tenant.deleted) {
            when (val res = firestoreTenantRepository.deleteTenant(docId, myDeviceId)) {
                is PgResult.Success -> {
                    tenantDao.updateTenant(
                        tenant.copy(
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("TENANT", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val nextVersion = tenant.version + 1
            val now = System.currentTimeMillis()
            val dto = tenant.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreTenantRepository.saveTenant(dto)) {
                is PgResult.Success -> {
                    tenantDao.updateTenant(
                        tenant.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("TENANT", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        }
    }

    private suspend fun processPaymentUpload(op: SyncOperationEntity, ownerId: String) {
        val localId = op.entityId.toIntOrNull() ?: return
        val payment = rentPaymentDao.getPaymentByIdIncludingDeleted(localId)
        if (payment == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = "payment_$localId"
        if (op.operationType == "DELETE" || payment.deleted) {
            when (val res = firestorePaymentRepository.deletePayment(docId, myDeviceId)) {
                is PgResult.Success -> {
                    rentPaymentDao.updatePayment(
                        payment.copy(
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PAYMENT", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val nextVersion = payment.version + 1
            val now = System.currentTimeMillis()
            val dto = payment.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestorePaymentRepository.savePayment(dto)) {
                is PgResult.Success -> {
                    rentPaymentDao.updatePayment(
                        payment.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PAYMENT", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        }
    }

    private suspend fun processExpenseUpload(op: SyncOperationEntity, ownerId: String) {
        val localId = op.entityId.toIntOrNull() ?: return
        val expense = expenseDao.getExpenseByIdIncludingDeleted(localId)
        if (expense == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = "expense_$localId"
        if (op.operationType == "DELETE" || expense.deleted) {
            when (val res = firestoreExpenseRepository.deleteExpense(docId, myDeviceId)) {
                is PgResult.Success -> {
                    expenseDao.updateExpense(
                        expense.copy(
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("EXPENSE", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val nextVersion = expense.version + 1
            val now = System.currentTimeMillis()
            val dto = expense.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreExpenseRepository.saveExpense(dto)) {
                is PgResult.Success -> {
                    expenseDao.updateExpense(
                        expense.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("EXPENSE", localId.toString())
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        }
    }

    private suspend fun processPropertyUpload(op: SyncOperationEntity, ownerId: String) {
        val property = propertyDao.getProperty(op.entityId)
        if (property == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        if (op.operationType == "DELETE" || property.deleted) {
            when (val res = firestorePropertyRepository.deleteProperty(property.propertyId, myDeviceId)) {
                is PgResult.Success -> {
                    propertyDao.updateProperty(
                        property.copy(
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PROPERTY", property.propertyId)
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val nextVersion = property.version + 1
            val now = System.currentTimeMillis()
            val dto = property.toDto(ownerId, myDeviceId).copy(
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestorePropertyRepository.saveProperty(dto)) {
                is PgResult.Success -> {
                    propertyDao.updateProperty(
                        property.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PROPERTY", property.propertyId)
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        }
    }

    private suspend fun processProfileUpload(op: SyncOperationEntity, ownerId: String) {
        val profile = ownerProfileDao.getProfileIncludingDeleted()
        if (profile == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val now = System.currentTimeMillis()
        val dto = profile.toPropertyDto(ownerId).copy(
            updatedAt = now
        )
        when (val res = firestorePropertyRepository.saveProperty(dto)) {
            is PgResult.Success -> {
                ownerProfileDao.insertProfile(
                    profile.copy(
                        syncStatus = "SYNCED",
                        ownerId = ownerId,
                        updatedAt = now,
                        lastSyncedAt = System.currentTimeMillis(),
                        lastModifiedByDeviceId = myDeviceId
                    )
                )
                syncQueueDao.deleteOperation(op.id)
            }
            is PgResult.Failure -> throw Exception(res.error.message)
        }
    }

    private suspend fun downloadRemoteChanges(ownerId: String) {
        logger.i(TAG, "Downloading remote changes for user: $ownerId")

        // 0. Sync Properties
        when (val propResult = firestorePropertyRepository.getAllProperties(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemoteProperties(propResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote properties: ${propResult.error.message}")
        }

        // 1. Sync Rooms
        when (val roomResult = firestoreRoomRepository.getAllRooms(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemoteRooms(roomResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote rooms: ${roomResult.error.message}")
        }

        // 2. Sync Tenants
        when (val tenantResult = firestoreTenantRepository.getAllTenants(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemoteTenants(tenantResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote tenants: ${tenantResult.error.message}")
        }

        // 3. Sync Payments
        when (val paymentResult = firestorePaymentRepository.getAllPayments(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemotePayments(paymentResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote payments: ${paymentResult.error.message}")
        }

        // 4. Sync Expenses
        when (val expenseResult = firestoreExpenseRepository.getAllExpenses(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemoteExpenses(expenseResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote expenses: ${expenseResult.error.message}")
        }

        // 5. Sync Property Profile
        when (val propertyResult = firestorePropertyRepository.getProperty("property_main")) {
            is PgResult.Success -> {
                val propertyDto = propertyResult.data
                if (propertyDto != null) {
                    val local = ownerProfileDao.getProfileIncludingDeleted()
                    if (local == null || local.syncStatus != "PENDING_UPLOAD") {
                        ownerProfileDao.insertProfile(
                            propertyDto.toOwnerProfileEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
            is PgResult.Failure -> logger.w(TAG, "Could not fetch property profile: ${propertyResult.error.message}")
        }
    }

    private fun isRoomContentEqual(local: RoomEntity, remote: RoomDto): Boolean {
        return local.roomNumber == remote.roomNumber &&
                local.floor == remote.floor &&
                local.capacity == remote.capacity &&
                local.ratePerBed == remote.ratePerBed &&
                local.roomType == remote.roomType &&
                local.notes == remote.notes &&
                local.deleted == remote.deleted
    }

    private fun isTenantContentEqual(local: TenantEntity, remote: TenantDto): Boolean {
        return local.name.trim() == remote.name.trim() &&
                local.phone.trim() == remote.phone.trim() &&
                local.email.trim() == remote.email.trim() &&
                local.emergencyContact.trim() == remote.emergencyContact.trim() &&
                local.roomNumber == remote.roomNumber &&
                local.bedId == remote.bedId &&
                local.monthlyRent == remote.monthlyRent &&
                local.securityDeposit == remote.securityDeposit &&
                local.moveInDate == remote.moveInDate &&
                local.isKycUploaded == remote.isKycUploaded &&
                local.kycDocType == remote.kycDocType &&
                local.deleted == remote.deleted
    }

    private fun isPaymentContentEqual(local: RentPaymentEntity, remote: PaymentDto): Boolean {
        return local.tenantId == remote.tenantId &&
                local.roomNumber == remote.roomNumber &&
                local.billingMonth == remote.billingMonth &&
                local.amount == remote.amount &&
                local.amountPaid == remote.amountPaid &&
                local.dueDate == remote.dueDate &&
                local.paymentDate == remote.paymentDate &&
                local.paymentMode == remote.paymentMode &&
                local.status == remote.status &&
                local.deleted == remote.deleted
    }

    private fun isExpenseContentEqual(local: ExpenseEntity, remote: ExpenseDto): Boolean {
        return local.amount == remote.amount &&
                local.category == remote.category &&
                local.date == remote.date &&
                local.notes == remote.notes &&
                local.title == remote.title &&
                local.paymentMethod == remote.paymentMethod &&
                local.deleted == remote.deleted
    }

    private fun isPropertyContentEqual(local: PropertyEntity, remote: PropertyDto): Boolean {
        return local.propertyName == remote.propertyName &&
                local.address == remote.address &&
                local.city == remote.city &&
                local.state == remote.state &&
                local.postalCode == remote.postalCode &&
                local.contactNumber == remote.contactNumber &&
                local.description == remote.description &&
                local.isActive == remote.isActive &&
                local.deleted == remote.deleted
    }

    suspend fun reconcileRemoteProperties(properties: List<PropertyDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in properties) {
                if (remoteDto.ownerId != ownerId) continue
                val local = propertyDao.getPropertyIncludingDeleted(remoteDto.id)
                if (local == null) {
                    propertyDao.insertProperty(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                            deleted = remoteDto.deleted
                        )
                    )
                    continue
                }

                val isContentIdentical = isPropertyContentEqual(local, remoteDto)
                if (isContentIdentical && local.syncStatus == "SYNCED" && local.version == remoteDto.version) {
                    continue
                }

                val resolvedConflict = conflictRecordDao.getResolvedConflictForEntity("PROPERTY", remoteDto.id)
                val isAlreadyResolved = resolvedConflict != null &&
                        (resolvedConflict.remoteVersion >= remoteDto.version && resolvedConflict.remoteUpdatedAt >= remoteDto.updatedAt)

                val decision = conflictResolver.resolve(
                    localUpdatedAt = local.updatedAt,
                    localVersion = local.version,
                    localStatus = local.syncStatus,
                    remoteUpdatedAt = remoteDto.updatedAt,
                    remoteVersion = remoteDto.version,
                    localData = local,
                    remoteData = remoteDto,
                    entityName = "Property ${remoteDto.propertyName}",
                    remoteDeviceId = remoteDto.lastModifiedByDeviceId,
                    localDeviceId = myDeviceId,
                    isContentIdentical = isContentIdentical,
                    isConflictAlreadyResolved = isAlreadyResolved
                )

                when (decision) {
                    is ConflictResult.UseRemote -> {
                        propertyDao.updateProperty(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                                deleted = remoteDto.deleted
                            )
                        )
                        if (isContentIdentical || remoteDto.lastModifiedByDeviceId == myDeviceId) {
                            syncQueueDao.deleteByEntity("PROPERTY", remoteDto.id)
                        }
                    }
                    is ConflictResult.ConflictDetected -> {
                        val activeConflict = conflictRecordDao.getConflictForEntity("PROPERTY", local.propertyId)
                        val record = activeConflict?.copy(
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Property ${local.propertyName}, Address ${local.address}",
                            remoteDataJson = "Property ${remoteDto.propertyName}, Address ${remoteDto.address}",
                            status = "CONFLICT"
                        ) ?: ConflictRecordEntity(
                            entityId = local.propertyId,
                            entityType = "PROPERTY",
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Property ${local.propertyName}, Address ${local.address}",
                            remoteDataJson = "Property ${remoteDto.propertyName}, Address ${remoteDto.address}",
                            status = "CONFLICT"
                        )
                        if (activeConflict != null) {
                            conflictRecordDao.updateConflict(record)
                        } else {
                            conflictRecordDao.insertConflict(record)
                        }
                        propertyDao.updateProperty(local.copy(syncStatus = "CONFLICT"))
                    }
                    is ConflictResult.UseLocal -> { /* Retain local */ }
                }
            }
        }
    }

    suspend fun reconcileRemoteRooms(rooms: List<RoomDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in rooms) {
                if (remoteDto.ownerId != ownerId) continue
                val local = roomDao.getRoomIncludingDeleted(remoteDto.roomNumber)
                if (local == null) {
                    roomDao.insertRoom(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                            deleted = remoteDto.deleted
                        )
                    )
                    continue
                }

                val isContentIdentical = isRoomContentEqual(local, remoteDto)
                if (isContentIdentical && local.syncStatus == "SYNCED" && local.version == remoteDto.version) {
                    continue
                }

                val resolvedConflict = conflictRecordDao.getResolvedConflictForEntity("ROOM", remoteDto.roomNumber)
                val isAlreadyResolved = resolvedConflict != null &&
                        (resolvedConflict.remoteVersion >= remoteDto.version && resolvedConflict.remoteUpdatedAt >= remoteDto.updatedAt)

                val decision = conflictResolver.resolve(
                    localUpdatedAt = local.updatedAt,
                    localVersion = local.version,
                    localStatus = local.syncStatus,
                    remoteUpdatedAt = remoteDto.updatedAt,
                    remoteVersion = remoteDto.version,
                    localData = local,
                    remoteData = remoteDto,
                    entityName = "Room ${remoteDto.roomNumber}",
                    remoteDeviceId = remoteDto.lastModifiedByDeviceId,
                    localDeviceId = myDeviceId,
                    isContentIdentical = isContentIdentical,
                    isConflictAlreadyResolved = isAlreadyResolved
                )

                when (decision) {
                    is ConflictResult.UseRemote -> {
                        roomDao.insertRoom(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                                deleted = remoteDto.deleted
                            )
                        )
                        if (isContentIdentical || remoteDto.lastModifiedByDeviceId == myDeviceId) {
                            syncQueueDao.deleteByEntity("ROOM", remoteDto.roomNumber)
                        }
                    }
                    is ConflictResult.ConflictDetected -> {
                        val activeConflict = conflictRecordDao.getConflictForEntity("ROOM", local.roomNumber)
                        val record = activeConflict?.copy(
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Room ${local.roomNumber}, Beds ${local.capacity}, Rent ₹${local.ratePerBed}",
                            remoteDataJson = "Room ${remoteDto.roomNumber}, Beds ${remoteDto.capacity}, Rent ₹${remoteDto.ratePerBed}",
                            status = "CONFLICT"
                        ) ?: ConflictRecordEntity(
                            entityId = local.roomNumber,
                            entityType = "ROOM",
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Room ${local.roomNumber}, Beds ${local.capacity}, Rent ₹${local.ratePerBed}",
                            remoteDataJson = "Room ${remoteDto.roomNumber}, Beds ${remoteDto.capacity}, Rent ₹${remoteDto.ratePerBed}",
                            status = "CONFLICT"
                        )
                        if (activeConflict != null) {
                            conflictRecordDao.updateConflict(record)
                        } else {
                            conflictRecordDao.insertConflict(record)
                        }
                        roomDao.updateRoom(local.copy(syncStatus = "CONFLICT"))
                    }
                    is ConflictResult.UseLocal -> { /* Retain local un-uploaded changes */ }
                }
            }
        }
    }

    suspend fun reconcileRemoteTenants(tenants: List<TenantDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in tenants) {
                if (remoteDto.ownerId != ownerId) continue
                val local = tenantDao.getTenantByIdIncludingDeleted(remoteDto.localId)
                if (local == null) {
                    tenantDao.insertTenant(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                            deleted = remoteDto.deleted
                        )
                    )
                    continue
                }

                val isContentIdentical = isTenantContentEqual(local, remoteDto)
                if (isContentIdentical && local.syncStatus == "SYNCED" && local.version == remoteDto.version) {
                    continue
                }

                val resolvedConflict = conflictRecordDao.getResolvedConflictForEntity("TENANT", local.id.toString())
                val isAlreadyResolved = resolvedConflict != null &&
                        (resolvedConflict.remoteVersion >= remoteDto.version && resolvedConflict.remoteUpdatedAt >= remoteDto.updatedAt)

                val decision = conflictResolver.resolve(
                    localUpdatedAt = local.updatedAt,
                    localVersion = local.version,
                    localStatus = local.syncStatus,
                    remoteUpdatedAt = remoteDto.updatedAt,
                    remoteVersion = remoteDto.version,
                    localData = local,
                    remoteData = remoteDto,
                    entityName = "Tenant ${remoteDto.name}",
                    remoteDeviceId = remoteDto.lastModifiedByDeviceId,
                    localDeviceId = myDeviceId,
                    isContentIdentical = isContentIdentical,
                    isConflictAlreadyResolved = isAlreadyResolved
                )

                when (decision) {
                    is ConflictResult.UseRemote -> {
                        tenantDao.updateTenant(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                                deleted = remoteDto.deleted
                            )
                        )
                        if (isContentIdentical || remoteDto.lastModifiedByDeviceId == myDeviceId) {
                            syncQueueDao.deleteByEntity("TENANT", local.id.toString())
                        }
                    }
                    is ConflictResult.ConflictDetected -> {
                        val activeConflict = conflictRecordDao.getConflictForEntity("TENANT", local.id.toString())
                        val record = activeConflict?.copy(
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Tenant ${local.name}, Room ${local.roomNumber}, Rent ₹${local.monthlyRent}",
                            remoteDataJson = "Tenant ${remoteDto.name}, Room ${remoteDto.roomNumber}, Rent ₹${remoteDto.monthlyRent}",
                            status = "CONFLICT"
                        ) ?: ConflictRecordEntity(
                            entityId = local.id.toString(),
                            entityType = "TENANT",
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Tenant ${local.name}, Room ${local.roomNumber}, Rent ₹${local.monthlyRent}",
                            remoteDataJson = "Tenant ${remoteDto.name}, Room ${remoteDto.roomNumber}, Rent ₹${remoteDto.monthlyRent}",
                            status = "CONFLICT"
                        )
                        if (activeConflict != null) {
                            conflictRecordDao.updateConflict(record)
                        } else {
                            conflictRecordDao.insertConflict(record)
                        }
                        tenantDao.updateTenant(local.copy(syncStatus = "CONFLICT"))
                    }
                    is ConflictResult.UseLocal -> { /* Retain local */ }
                }
            }
        }
    }

    suspend fun reconcileRemotePayments(payments: List<PaymentDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in payments) {
                if (remoteDto.ownerId != ownerId) continue
                val local = rentPaymentDao.getPaymentByIdIncludingDeleted(remoteDto.localId)
                if (local == null) {
                    rentPaymentDao.insertPayment(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                            deleted = remoteDto.deleted
                        )
                    )
                    continue
                }

                val isContentIdentical = isPaymentContentEqual(local, remoteDto)
                if (isContentIdentical && local.syncStatus == "SYNCED" && local.version == remoteDto.version) {
                    continue
                }

                val resolvedConflict = conflictRecordDao.getResolvedConflictForEntity("PAYMENT", local.id.toString())
                val isAlreadyResolved = resolvedConflict != null &&
                        (resolvedConflict.remoteVersion >= remoteDto.version && resolvedConflict.remoteUpdatedAt >= remoteDto.updatedAt)

                val decision = conflictResolver.resolve(
                    localUpdatedAt = local.updatedAt,
                    localVersion = local.version,
                    localStatus = local.syncStatus,
                    remoteUpdatedAt = remoteDto.updatedAt,
                    remoteVersion = remoteDto.version,
                    localData = local,
                    remoteData = remoteDto,
                    entityName = "Payment ${remoteDto.localId}",
                    remoteDeviceId = remoteDto.lastModifiedByDeviceId,
                    localDeviceId = myDeviceId,
                    isContentIdentical = isContentIdentical,
                    isConflictAlreadyResolved = isAlreadyResolved
                )

                when (decision) {
                    is ConflictResult.UseRemote -> {
                        rentPaymentDao.updatePayment(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                                deleted = remoteDto.deleted
                            )
                        )
                        if (isContentIdentical || remoteDto.lastModifiedByDeviceId == myDeviceId) {
                            syncQueueDao.deleteByEntity("PAYMENT", local.id.toString())
                        }
                    }
                    is ConflictResult.ConflictDetected -> {
                        val activeConflict = conflictRecordDao.getConflictForEntity("PAYMENT", local.id.toString())
                        val record = activeConflict?.copy(
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Payment #${local.id}, Tenant ${local.tenantId}, ₹${local.amountPaid}, Status ${local.status}",
                            remoteDataJson = "Payment #${remoteDto.localId}, Tenant ${remoteDto.tenantId}, ₹${remoteDto.amountPaid}, Status ${remoteDto.status}",
                            status = "CONFLICT"
                        ) ?: ConflictRecordEntity(
                            entityId = local.id.toString(),
                            entityType = "PAYMENT",
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Payment #${local.id}, Tenant ${local.tenantId}, ₹${local.amountPaid}, Status ${local.status}",
                            remoteDataJson = "Payment #${remoteDto.localId}, Tenant ${remoteDto.tenantId}, ₹${remoteDto.amountPaid}, Status ${remoteDto.status}",
                            status = "CONFLICT"
                        )
                        if (activeConflict != null) {
                            conflictRecordDao.updateConflict(record)
                        } else {
                            conflictRecordDao.insertConflict(record)
                        }
                        rentPaymentDao.updatePayment(local.copy(syncStatus = "CONFLICT"))
                    }
                    is ConflictResult.UseLocal -> { /* Retain local */ }
                }
            }
        }
    }

    suspend fun reconcileRemoteExpenses(expenses: List<ExpenseDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in expenses) {
                if (remoteDto.ownerId != ownerId) continue
                val local = expenseDao.getExpenseByIdIncludingDeleted(remoteDto.localId)
                if (local == null) {
                    expenseDao.insertExpense(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                            deleted = remoteDto.deleted
                        )
                    )
                    continue
                }

                val isContentIdentical = isExpenseContentEqual(local, remoteDto)
                if (isContentIdentical && local.syncStatus == "SYNCED" && local.version == remoteDto.version) {
                    continue
                }

                val resolvedConflict = conflictRecordDao.getResolvedConflictForEntity("EXPENSE", local.id.toString())
                val isAlreadyResolved = resolvedConflict != null &&
                        (resolvedConflict.remoteVersion >= remoteDto.version && resolvedConflict.remoteUpdatedAt >= remoteDto.updatedAt)

                val decision = conflictResolver.resolve(
                    localUpdatedAt = local.updatedAt,
                    localVersion = local.version,
                    localStatus = local.syncStatus,
                    remoteUpdatedAt = remoteDto.updatedAt,
                    remoteVersion = remoteDto.version,
                    localData = local,
                    remoteData = remoteDto,
                    entityName = "Expense ${remoteDto.localId}",
                    remoteDeviceId = remoteDto.lastModifiedByDeviceId,
                    localDeviceId = myDeviceId,
                    isContentIdentical = isContentIdentical,
                    isConflictAlreadyResolved = isAlreadyResolved
                )

                when (decision) {
                    is ConflictResult.UseRemote -> {
                        expenseDao.updateExpense(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                lastModifiedByDeviceId = remoteDto.lastModifiedByDeviceId,
                                deleted = remoteDto.deleted
                            )
                        )
                        if (isContentIdentical || remoteDto.lastModifiedByDeviceId == myDeviceId) {
                            syncQueueDao.deleteByEntity("EXPENSE", local.id.toString())
                        }
                    }
                    is ConflictResult.ConflictDetected -> {
                        val activeConflict = conflictRecordDao.getConflictForEntity("EXPENSE", local.id.toString())
                        val record = activeConflict?.copy(
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Expense #${local.id}, ${local.category}, ₹${local.amount}, ${local.notes}",
                            remoteDataJson = "Expense #${remoteDto.localId}, ${remoteDto.category}, ₹${remoteDto.amount}, ${remoteDto.notes}",
                            status = "CONFLICT"
                        ) ?: ConflictRecordEntity(
                            entityId = local.id.toString(),
                            entityType = "EXPENSE",
                            deviceA = myDeviceId,
                            deviceB = decision.remoteDeviceId.ifBlank { remoteDto.lastModifiedByDeviceId },
                            localVersion = decision.localVersion,
                            remoteVersion = decision.remoteVersion,
                            localUpdatedAt = decision.localUpdatedAt,
                            remoteUpdatedAt = decision.remoteUpdatedAt,
                            localDataJson = "Expense #${local.id}, ${local.category}, ₹${local.amount}, ${local.notes}",
                            remoteDataJson = "Expense #${remoteDto.localId}, ${remoteDto.category}, ₹${remoteDto.amount}, ${remoteDto.notes}",
                            status = "CONFLICT"
                        )
                        if (activeConflict != null) {
                            conflictRecordDao.updateConflict(record)
                        } else {
                            conflictRecordDao.insertConflict(record)
                        }
                        expenseDao.updateExpense(local.copy(syncStatus = "CONFLICT"))
                    }
                    is ConflictResult.UseLocal -> { /* Retain local */ }
                }
            }
        }
    }

    suspend fun resolveConflict(conflictId: String, strategy: ConflictResolutionStrategy): PgResult<Unit> {
        val conflict = conflictRecordDao.getActiveConflicts().find { it.id == conflictId }
            ?: return PgResult.Failure(PgError.DatabaseError("Conflict record not found"))

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val ownerId = currentOwnerId ?: "default_owner"

        return try {
            when (strategy) {
                ConflictResolutionStrategy.KEEP_LOCAL -> {
                    // Mark local version for re-upload with incremented version and enqueue
                    when (conflict.entityType) {
                        "ROOM" -> {
                            val local = roomDao.getRoomIncludingDeleted(conflict.entityId)
                            if (local != null) {
                                val nextVersion = maxOf(local.version, conflict.remoteVersion) + 1
                                val now = System.currentTimeMillis()
                                roomDao.updateRoom(
                                    local.copy(
                                        syncStatus = "PENDING_UPLOAD",
                                        version = nextVersion,
                                        updatedAt = now,
                                        lastModifiedByDeviceId = myDeviceId
                                    )
                                )
                                syncQueueDao.enqueue(
                                    SyncOperationEntity(
                                        entityType = "ROOM",
                                        entityId = local.roomNumber,
                                        operationType = if (local.deleted) "DELETE" else "UPDATE"
                                    )
                                )
                            }
                        }
                        "TENANT" -> {
                            val id = conflict.entityId.toIntOrNull()
                            val local = id?.let { tenantDao.getTenantByIdIncludingDeleted(it) }
                            if (local != null) {
                                val nextVersion = maxOf(local.version, conflict.remoteVersion) + 1
                                val now = System.currentTimeMillis()
                                tenantDao.updateTenant(
                                    local.copy(
                                        syncStatus = "PENDING_UPLOAD",
                                        version = nextVersion,
                                        updatedAt = now,
                                        lastModifiedByDeviceId = myDeviceId
                                    )
                                )
                                syncQueueDao.enqueue(
                                    SyncOperationEntity(
                                        entityType = "TENANT",
                                        entityId = local.id.toString(),
                                        operationType = if (local.deleted) "DELETE" else "UPDATE"
                                    )
                                )
                            }
                        }
                        "PAYMENT" -> {
                            val id = conflict.entityId.toIntOrNull()
                            val local = id?.let { rentPaymentDao.getPaymentByIdIncludingDeleted(it) }
                            if (local != null) {
                                val nextVersion = maxOf(local.version, conflict.remoteVersion) + 1
                                val now = System.currentTimeMillis()
                                rentPaymentDao.updatePayment(
                                    local.copy(
                                        syncStatus = "PENDING_UPLOAD",
                                        version = nextVersion,
                                        updatedAt = now,
                                        lastModifiedByDeviceId = myDeviceId
                                    )
                                )
                                syncQueueDao.enqueue(
                                    SyncOperationEntity(
                                        entityType = "PAYMENT",
                                        entityId = local.id.toString(),
                                        operationType = if (local.deleted) "DELETE" else "UPDATE"
                                    )
                                )
                            }
                        }
                        "EXPENSE" -> {
                            val id = conflict.entityId.toIntOrNull()
                            val local = id?.let { expenseDao.getExpenseByIdIncludingDeleted(it) }
                            if (local != null) {
                                val nextVersion = maxOf(local.version, conflict.remoteVersion) + 1
                                val now = System.currentTimeMillis()
                                expenseDao.updateExpense(
                                    local.copy(
                                        syncStatus = "PENDING_UPLOAD",
                                        version = nextVersion,
                                        updatedAt = now,
                                        lastModifiedByDeviceId = myDeviceId
                                    )
                                )
                                syncQueueDao.enqueue(
                                    SyncOperationEntity(
                                        entityType = "EXPENSE",
                                        entityId = local.id.toString(),
                                        operationType = if (local.deleted) "DELETE" else "UPDATE"
                                    )
                                )
                            }
                        }
                    }
                }
                ConflictResolutionStrategy.KEEP_REMOTE -> {
                    // Fetch remote copy from Firestore, overwrite local Room, and remove any pending local upload from queue
                    when (conflict.entityType) {
                        "ROOM" -> {
                            val res = firestoreRoomRepository.getRoom(conflict.entityId)
                            if (res is PgResult.Success && res.data != null) {
                                val r = res.data
                                roomDao.insertRoom(
                                    r.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        version = r.version,
                                        updatedAt = r.updatedAt,
                                        lastSyncedAt = System.currentTimeMillis(),
                                        lastModifiedByDeviceId = r.lastModifiedByDeviceId,
                                        deleted = r.deleted
                                    )
                                )
                            }
                            syncQueueDao.deleteByEntity("ROOM", conflict.entityId)
                        }
                        "TENANT" -> {
                            val res = firestoreTenantRepository.getTenant("tenant_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                val t = res.data
                                tenantDao.insertTenant(
                                    t.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        version = t.version,
                                        updatedAt = t.updatedAt,
                                        lastSyncedAt = System.currentTimeMillis(),
                                        lastModifiedByDeviceId = t.lastModifiedByDeviceId,
                                        deleted = t.deleted
                                    )
                                )
                            }
                            syncQueueDao.deleteByEntity("TENANT", conflict.entityId)
                        }
                        "PAYMENT" -> {
                            val res = firestorePaymentRepository.getPayment("payment_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                val p = res.data
                                rentPaymentDao.insertPayment(
                                    p.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        version = p.version,
                                        updatedAt = p.updatedAt,
                                        lastSyncedAt = System.currentTimeMillis(),
                                        lastModifiedByDeviceId = p.lastModifiedByDeviceId,
                                        deleted = p.deleted
                                    )
                                )
                            }
                            syncQueueDao.deleteByEntity("PAYMENT", conflict.entityId)
                        }
                        "EXPENSE" -> {
                            val res = firestoreExpenseRepository.getExpense("expense_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                val e = res.data
                                expenseDao.insertExpense(
                                    e.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        version = e.version,
                                        updatedAt = e.updatedAt,
                                        lastSyncedAt = System.currentTimeMillis(),
                                        lastModifiedByDeviceId = e.lastModifiedByDeviceId,
                                        deleted = e.deleted
                                    )
                                )
                            }
                            syncQueueDao.deleteByEntity("EXPENSE", conflict.entityId)
                        }
                    }
                }
            }
            conflictRecordDao.updateConflict(
                conflict.copy(
                    status = "RESOLVED",
                    resolvedAt = System.currentTimeMillis()
                )
            )
            PgResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed resolving conflict: ${e.message}", e)
            PgResult.Failure(PgError.DatabaseError(e.localizedMessage ?: "Failed resolving conflict"))
        }
    }

    suspend fun clearLocalUserData() {
        logger.i(TAG, "Purging all local Room data for account isolation")
        database.clearAllUserData()
    }

    companion object {
        private const val TAG = "SyncEngine"
    }
}
