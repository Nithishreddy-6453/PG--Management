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
import com.example.data.database.DriveFolderDao
import com.example.data.database.TenantMediaDao
import com.example.data.database.TenantMediaEntity
import com.example.data.firestore.mapper.toDto
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.mapper.toOwnerProfileEntity
import com.example.data.firestore.mapper.toPropertyDto
import com.example.data.firestore.model.ExpenseDto
import com.example.data.firestore.model.PaymentDto
import com.example.data.firestore.model.PropertyDto
import com.example.data.firestore.model.RoomDto
import com.example.data.firestore.model.TenantDto
import com.example.data.firestore.model.TenantMediaDto
import com.example.data.firestore.repository.FirestoreExpenseRepository
import com.example.data.firestore.repository.FirestorePaymentRepository
import com.example.data.firestore.repository.FirestorePropertyRepository
import com.example.data.firestore.repository.FirestoreRoomRepository
import com.example.data.firestore.repository.FirestoreSettingsRepository
import com.example.data.firestore.repository.FirestoreTenantMediaRepository
import com.example.data.firestore.repository.FirestoreTenantRepository
import com.example.data.firestore.repository.FirestoreUserRepository
import com.example.features.properties.data.CurrentPropertyManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
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
    private val tenantMediaDao: TenantMediaDao,
    private val driveFolderDao: DriveFolderDao,
    private val firestoreTenantMediaRepository: FirestoreTenantMediaRepository,
    private val currentPropertyManager: CurrentPropertyManager,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver,
    private val logger: PgLogger,
    private val tenantMediaRepositoryProvider: javax.inject.Provider<com.example.features.tenants.domain.repository.TenantMediaRepository>? = null,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private val syncMutex = Mutex()

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
                        val propId = if (dto.propertyId.isNotBlank() && dto.propertyId != "property_default") {
                            dto.propertyId
                        } else {
                            logger.w(TAG, "Bootstrap: Skipping tenant ${dto.cloudId.take(8)} missing valid propertyId to preserve property isolation")
                            continue
                        }
                        tenantDao.insertTenant(
                            dto.toEntity().copy(
                                id = 0,
                                cloudId = dto.cloudId.ifBlank { dto.id },
                                ownerId = ownerId,
                                propertyId = propId,
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
                                id = 0,
                                cloudId = dto.cloudId.ifBlank { dto.id },
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
                                id = 0,
                                cloudId = dto.cloudId.ifBlank { dto.id },
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

            // 7.5. Download and insert Tenant Media Metadata (Profile Photos etc.)
            when (val mediaRes = firestoreTenantMediaRepository.getAllTenantMedia(ownerId, includeDeleted = true)) {
                is PgResult.Success -> {
                    val mediaList = mediaRes.data
                    logger.i(TAG, "Bootstrap: Restoring ${mediaList.size} tenant media records from cloud")
                    for (dto in mediaList) {
                        val canonicalCloudId = if (dto.cloudId.isNotBlank()) dto.cloudId
                            else if (dto.mediaType == "PROFILE_PHOTO") "${dto.tenantCloudId}_PROFILE_PHOTO"
                            else "${dto.tenantCloudId}_DOC_${java.util.UUID.randomUUID().toString().take(8)}"

                        val existing = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
                            ?: if (dto.tenantCloudId.isNotBlank() && dto.mediaType == "PROFILE_PHOTO") tenantMediaDao.getProfilePhoto(dto.tenantCloudId) else null
                        val keepLocal = if (existing != null && existing.localFilePath.isNotBlank() && java.io.File(existing.localFilePath).exists() && existing.driveFileId == dto.driveFileId) {
                            existing.localFilePath
                        } else ""
                        if (dto.tenantCloudId.isNotBlank() && dto.mediaType == "PROFILE_PHOTO") {
                            tenantMediaDao.deleteNonCanonicalProfilePhotos(dto.tenantCloudId, canonicalCloudId)
                        }
                        tenantMediaDao.insertOrUpdate(
                            dto.toEntity(localFilePath = keepLocal).copy(
                                cloudId = canonicalCloudId,
                                syncStatus = "SYNCED",
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading tenant media: ${mediaRes.error.message}")
            }

            // 7.6. Download and insert Drive Folder Mappings
            when (val mappingsRes = firestoreTenantMediaRepository.getAllFolderMappings(ownerId)) {
                is PgResult.Success -> {
                    logger.i(TAG, "Bootstrap: Restoring ${mappingsRes.data.size} drive folder mappings from cloud")
                    reconcileRemoteFolderMappings(mappingsRes.data)
                }
                is PgResult.Failure -> logger.w(TAG, "Bootstrap: Failed downloading drive folder mappings: ${mappingsRes.error.message}")
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

        if (!syncMutex.tryLock()) {
            logger.i(TAG, "Sync already in progress (mutex locked)")
            return PgResult.Success(Unit)
        }

        _isSyncing.value = true
        _lastError.value = null
        logger.i(TAG, "Starting synchronization for user: $ownerId [Device: ${deviceIdentityManager.getDeviceId()}]")

        return try {
            syncQueueDao.resetSyncingOperations()

            // 1. Download remote updates first to reconcile state and detect conflicts
            downloadRemoteChanges(ownerId)

            // 2. Upload local changes queued in sync_queue (multi-pass)
            val uploadSuccess = uploadPendingChanges(ownerId)

            // 3. Final download pass to confirm uploads and self-echoes
            downloadRemoteChanges(ownerId)

            if (!uploadSuccess) {
                val errorMsg = "Synchronization incomplete: one or more upload operations failed"
                logger.w(TAG, errorMsg)
                _lastError.value = errorMsg
                PgResult.Failure(PgError.NetworkError(errorMsg))
            } else {
                _lastSyncedAt.value = System.currentTimeMillis()
                logger.i(TAG, "Synchronization completed successfully")
                PgResult.Success(Unit)
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown synchronization error"
            logger.e(TAG, "Synchronization failed: $errorMsg", e)
            _lastError.value = errorMsg
            PgResult.Failure(PgError.UnknownError(errorMsg, e))
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun uploadPendingChanges(ownerId: String): Boolean {
        var hasFailures = false
        var passes = 0
        val maxPasses = 3

        while (passes < maxPasses) {
            val pendingOps = syncQueueDao.getPendingOperations().filter {
                if (passes == 0) true else it.status == "PENDING_UPLOAD"
            }
            if (pendingOps.isEmpty()) break
            passes++
            logger.i(TAG, "Processing ${pendingOps.size} pending upload operations (pass $passes)")

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
                        "MEDIA" -> processMediaUpload(op, ownerId)
                        "BED" -> processBedUpload(op, ownerId)
                        "BED_ASSIGNMENT" -> processBedAssignmentUpload(op, ownerId)
                        else -> logger.w(TAG, "Unknown entity type in sync queue: ${op.entityType}")
                    }
                } catch (e: Exception) {
                    hasFailures = true
                    val realCause = e.cause ?: e
                    val exceptionClass = realCause.javaClass.name
                    val errorMsg = e.message ?: e.localizedMessage ?: "Upload failed"
                    val detailedError = "[$exceptionClass] $errorMsg"
                    logger.e(TAG, "Failed uploading operation ${op.id} (${op.entityType}): $detailedError", e)
                    syncQueueDao.update(
                        op.copy(
                            status = "FAILED",
                            retryCount = op.retryCount + 1,
                            error = detailedError,
                            lastAttemptAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return !hasFailures
    }

    private suspend fun processRoomUpload(op: SyncOperationEntity, ownerId: String) {
        val room = roomDao.getRoomIncludingDeleted(op.entityId)
        if (room == null) {
            logger.w(TAG, "Sync: Dropping operation ${op.id} for missing ROOM ${op.entityId}")
            syncQueueDao.deleteOperation(op.id)
            return
        }
        if (room.syncStatus == "CONFLICT") {
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = room.roomNumber
        val firestorePath = "rooms/$docId"
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
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
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
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        }
    }

    internal suspend fun processTenantUpload(op: SyncOperationEntity, ownerId: String) {
        val tenant = op.entityId.toIntOrNull()?.let { tenantDao.getTenantByIdIncludingDeleted(it) }
            ?: tenantDao.getTenantByCloudIdIncludingDeleted(op.entityId)
        if (tenant == null) {
            logger.w(TAG, "Sync: Dropping operation ${op.id} for missing TENANT ${op.entityId}")
            syncQueueDao.deleteOperation(op.id)
            return
        }
        if (tenant.syncStatus == "CONFLICT") {
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = if (tenant.cloudId.isNotBlank()) tenant.cloudId else "tenant_${java.util.UUID.randomUUID()}"
        val firestorePath = "tenants/$docId"
        if (op.operationType == "DELETE" || tenant.deleted) {
            when (val res = firestoreTenantRepository.deleteTenant(docId, myDeviceId)) {
                is PgResult.Success -> {
                    tenantDao.updateTenant(
                        tenant.copy(
                            cloudId = docId,
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("TENANT", tenant.id.toString())
                    syncQueueDao.deleteByEntity("TENANT", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        } else {
            val hasValidProp = tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default"
            val resolvedPropId = if (hasValidProp) {
                tenant.propertyId
            } else {
                val ownerProps = propertyDao.getProperties(ownerId).filter { !it.deleted && it.isActive && it.propertyId != "property_default" && it.propertyId.isNotBlank() }
                if (ownerProps.size == 1) {
                    ownerProps.first().propertyId
                } else {
                    logger.w(TAG, "Sync: Skipping upload for legacy tenant ${tenant.id} (${tenant.name}) - ambiguous property ownership across ${ownerProps.size} properties; preserving property isolation")
                    return
                }
            }
            val nextVersion = tenant.version + 1
            val now = System.currentTimeMillis()
            val dto = tenant.toDto(ownerId, myDeviceId).copy(
                id = docId,
                cloudId = docId,
                propertyId = resolvedPropId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreTenantRepository.saveTenant(dto)) {
                is PgResult.Success -> {
                    tenantDao.updateTenant(
                        tenant.copy(
                            cloudId = docId,
                            propertyId = resolvedPropId,
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("TENANT", tenant.id.toString())
                    syncQueueDao.deleteByEntity("TENANT", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        }
    }

    private suspend fun processPaymentUpload(op: SyncOperationEntity, ownerId: String) {
        val payment = op.entityId.toIntOrNull()?.let { rentPaymentDao.getPaymentByIdIncludingDeleted(it) }
            ?: rentPaymentDao.getPaymentByCloudIdIncludingDeleted(op.entityId)
        if (payment == null) {
            logger.w(TAG, "Sync: Dropping operation ${op.id} for missing PAYMENT ${op.entityId}")
            syncQueueDao.deleteOperation(op.id)
            return
        }
        if (payment.syncStatus == "CONFLICT") {
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = if (payment.cloudId.isNotBlank()) payment.cloudId else "payment_${java.util.UUID.randomUUID()}"
        val firestorePath = "payments/$docId"
        if (op.operationType == "DELETE" || payment.deleted) {
            when (val res = firestorePaymentRepository.deletePayment(docId, myDeviceId)) {
                is PgResult.Success -> {
                    rentPaymentDao.updatePayment(
                        payment.copy(
                            cloudId = docId,
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PAYMENT", payment.id.toString())
                    syncQueueDao.deleteByEntity("PAYMENT", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        } else {
            val nextVersion = payment.version + 1
            val now = System.currentTimeMillis()
            val tenant = tenantDao.getTenantByIdIncludingDeleted(payment.tenantId)
            val tenantCloudId = tenant?.cloudId ?: ""
            val dto = payment.toDto(ownerId, myDeviceId, tenantCloudId).copy(
                id = docId,
                cloudId = docId,
                tenantCloudId = tenantCloudId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestorePaymentRepository.savePayment(dto)) {
                is PgResult.Success -> {
                    rentPaymentDao.updatePayment(
                        payment.copy(
                            cloudId = docId,
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("PAYMENT", payment.id.toString())
                    syncQueueDao.deleteByEntity("PAYMENT", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        }
    }

    private suspend fun processExpenseUpload(op: SyncOperationEntity, ownerId: String) {
        val expense = op.entityId.toIntOrNull()?.let { expenseDao.getExpenseByIdIncludingDeleted(it) }
            ?: expenseDao.getExpenseByCloudIdIncludingDeleted(op.entityId)
        if (expense == null) {
            logger.w(TAG, "Sync: Dropping operation ${op.id} for missing EXPENSE ${op.entityId}")
            syncQueueDao.deleteOperation(op.id)
            return
        }
        if (expense.syncStatus == "CONFLICT") {
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val docId = if (expense.cloudId.isNotBlank()) expense.cloudId else "expense_${java.util.UUID.randomUUID()}"
        val firestorePath = "expenses/$docId"
        if (op.operationType == "DELETE" || expense.deleted) {
            when (val res = firestoreExpenseRepository.deleteExpense(docId, myDeviceId)) {
                is PgResult.Success -> {
                    expenseDao.updateExpense(
                        expense.copy(
                            cloudId = docId,
                            syncStatus = "SYNCED",
                            deleted = true,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("EXPENSE", expense.id.toString())
                    syncQueueDao.deleteByEntity("EXPENSE", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        } else {
            val nextVersion = expense.version + 1
            val now = System.currentTimeMillis()
            val dto = expense.toDto(ownerId, myDeviceId).copy(
                id = docId,
                cloudId = docId,
                updatedAt = now,
                version = nextVersion,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreExpenseRepository.saveExpense(dto)) {
                is PgResult.Success -> {
                    expenseDao.updateExpense(
                        expense.copy(
                            cloudId = docId,
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = nextVersion,
                            updatedAt = now,
                            lastSyncedAt = System.currentTimeMillis(),
                            lastModifiedByDeviceId = myDeviceId
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
                    syncQueueDao.deleteByEntity("EXPENSE", expense.id.toString())
                    syncQueueDao.deleteByEntity("EXPENSE", docId)
                }
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
            }
        }
    }

    private suspend fun processPropertyUpload(op: SyncOperationEntity, ownerId: String) {
        val property = propertyDao.getProperty(op.entityId)
        if (property == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }
        if (property.syncStatus == "CONFLICT") {
            return
        }

        val myDeviceId = deviceIdentityManager.getDeviceId()
        val firestorePath = "properties/${property.propertyId}"
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
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
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
                is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
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
        val firestorePath = "properties/${dto.id}"
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
            is PgResult.Failure -> throw Exception("[Firestore path: $firestorePath] ${res.error.message}")
        }
    }

    private suspend fun processMediaUpload(op: SyncOperationEntity, ownerId: String) {
        val media = tenantMediaDao.getMediaByCloudId(op.entityId)
        if (media == null) {
            syncQueueDao.deleteOperation(op.id)
            return
        }

        if (op.operationType == "DELETE" || media.deleted) {
            val repo = tenantMediaRepositoryProvider?.get()
            if (repo != null) {
                when (val res = repo.deleteMedia(media.cloudId)) {
                    is PgResult.Success -> {
                        syncQueueDao.deleteOperation(op.id)
                        syncQueueDao.deleteByEntity("MEDIA", media.cloudId)
                    }
                    is PgResult.Failure -> throw Exception("Failed to delete media: ${res.error.message}")
                }
            } else {
                syncQueueDao.deleteOperation(op.id)
            }
        } else {
            val repo = tenantMediaRepositoryProvider?.get()
            if (repo != null) {
                when (val res = repo.syncPendingMedia(media.cloudId)) {
                    is PgResult.Success -> {
                        syncQueueDao.deleteOperation(op.id)
                        syncQueueDao.deleteByEntity("MEDIA", media.cloudId)
                    }
                    is PgResult.Failure -> throw Exception("Failed to sync media: ${res.error.message}")
                }
            } else {
                syncQueueDao.deleteOperation(op.id)
            }
        }
    }

    private suspend fun processBedUpload(op: SyncOperationEntity, ownerId: String) {
        val parts = op.entityId.split("_")
        val bed = if (parts.size >= 3) {
            database.bedDao().getBed(parts[0], parts[1], parts[2])
        } else {
            val propId = currentPropertyManager.getCurrentPropertyId()
            database.bedDao().getAllBedsForProperty(propId).find { it.bedId == op.entityId }
        }
        if (bed != null) {
            database.bedDao().updateBed(bed.copy(syncStatus = "SYNCED", lastSyncedAt = System.currentTimeMillis()))
        }
        syncQueueDao.deleteOperation(op.id)
        syncQueueDao.deleteByEntity("BED", op.entityId)
    }

    private suspend fun processBedAssignmentUpload(op: SyncOperationEntity, ownerId: String) {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val assignment = database.bedAssignmentDao().getAllAssignments(propId).find { it.assignmentId == op.entityId }
        if (assignment != null) {
            database.bedAssignmentDao().updateAssignment(assignment.copy(syncStatus = "SYNCED", lastSyncedAt = System.currentTimeMillis()))
        }
        syncQueueDao.deleteOperation(op.id)
        syncQueueDao.deleteByEntity("BED_ASSIGNMENT", op.entityId)
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

        // 6. Sync Tenant Media Metadata (Profile Photos etc.)
        when (val mediaResult = firestoreTenantMediaRepository.getAllTenantMedia(ownerId, includeDeleted = true)) {
            is PgResult.Success -> reconcileRemoteTenantMedia(mediaResult.data, ownerId)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote tenant media: ${mediaResult.error.message}")
        }

        // 7. Sync Drive Folder Mappings across devices
        when (val mappingsResult = firestoreTenantMediaRepository.getAllFolderMappings(ownerId)) {
            is PgResult.Success -> reconcileRemoteFolderMappings(mappingsResult.data)
            is PgResult.Failure -> logger.w(TAG, "Could not fetch remote folder mappings: ${mappingsResult.error.message}")
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
                val propId = if (remoteDto.propertyId.isNotBlank() && remoteDto.propertyId != "property_default") {
                    remoteDto.propertyId
                } else if (remoteDto.id.contains("_")) {
                    remoteDto.id.substringBefore("_")
                } else {
                    "property_default"
                }
                val local = roomDao.getRoomIncludingDeleted(propId, remoteDto.roomNumber)
                    ?: roomDao.getRoomIncludingDeleted(remoteDto.roomNumber)
                if (local == null) {
                    roomDao.insertRoom(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            propertyId = propId,
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
                                propertyId = propId,
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
                val cloudId = remoteDto.cloudId.ifBlank { remoteDto.id }
                val propId = if (remoteDto.propertyId.isNotBlank() && remoteDto.propertyId != "property_default") {
                    remoteDto.propertyId
                } else {
                    logger.w(TAG, "Sync: Dropping tenant ${cloudId.take(8)} missing valid propertyId to preserve isolation")
                    continue
                }
                val candidateByCloudId = if (cloudId.isNotBlank()) tenantDao.getTenantByCloudIdIncludingDeleted(cloudId) else null
                val local = if (candidateByCloudId != null && candidateByCloudId.propertyId == propId) {
                    candidateByCloudId
                } else if (candidateByCloudId != null) {
                    logger.w(TAG, "Sync: Tenant cloudId $cloudId belongs to property ${candidateByCloudId.propertyId}, rejecting cross-property match with remote property $propId")
                    null
                } else if (remoteDto.phone.isNotBlank()) {
                    tenantDao.getAllTenants(propId).find { it.phone == remoteDto.phone && it.name.trim().equals(remoteDto.name.trim(), ignoreCase = true) }
                } else null
                if (local == null) {
                    tenantDao.insertTenant(
                        remoteDto.toEntity().copy(
                            id = 0,
                            cloudId = cloudId,
                            ownerId = ownerId,
                            propertyId = propId,
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
                        val targetPropId = if (remoteDto.propertyId.isNotBlank() && remoteDto.propertyId != "property_default") {
                            remoteDto.propertyId
                        } else {
                            local.propertyId
                        }
                        tenantDao.updateTenant(
                            remoteDto.toEntity().copy(
                                id = local.id,
                                cloudId = local.cloudId.ifBlank { cloudId },
                                ownerId = ownerId,
                                propertyId = targetPropId,
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
                val cloudId = remoteDto.cloudId.ifBlank { remoteDto.id }
                val local = (if (cloudId.isNotBlank()) rentPaymentDao.getPaymentByCloudIdIncludingDeleted(cloudId) else null)
                    ?: if (remoteDto.tenantName.isNotBlank() && remoteDto.billingMonth.isNotBlank()) {
                        rentPaymentDao.getPaymentForTenantNamePropertyAndMonth(remoteDto.tenantName, remoteDto.propertyId, remoteDto.billingMonth)
                    } else null

                // Stably map tenantId across devices using stable tenantCloudId or tenantName within the same property
                val candidateTenant = if (remoteDto.tenantCloudId.isNotBlank()) {
                    tenantDao.getTenantByCloudIdIncludingDeleted(remoteDto.tenantCloudId)
                } else null
                val resolvedTenant = if (candidateTenant != null && candidateTenant.propertyId == remoteDto.propertyId) {
                    candidateTenant
                } else if (remoteDto.tenantName.isNotBlank() && remoteDto.propertyId.isNotBlank()) {
                    tenantDao.getAllTenants(remoteDto.propertyId).find { it.name.trim().equals(remoteDto.tenantName.trim(), ignoreCase = true) }
                } else null
                val resolvedTenantId = resolvedTenant?.id ?: remoteDto.tenantId

                if (local == null) {
                    rentPaymentDao.insertPayment(
                        remoteDto.toEntity().copy(
                            id = 0,
                            cloudId = cloudId,
                            tenantId = resolvedTenantId,
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
                                id = local.id,
                                cloudId = local.cloudId.ifBlank { cloudId },
                                tenantId = if (resolvedTenant != null) resolvedTenantId else local.tenantId,
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
                val cloudId = remoteDto.cloudId.ifBlank { remoteDto.id }
                val local = if (cloudId.isNotBlank()) expenseDao.getExpenseByCloudIdIncludingDeleted(cloudId) else null
                if (local == null) {
                    expenseDao.insertExpense(
                        remoteDto.toEntity().copy(
                            id = 0,
                            cloudId = cloudId,
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
                                id = local.id,
                                cloudId = local.cloudId.ifBlank { cloudId },
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

    suspend fun reconcileRemoteTenantMedia(mediaList: List<TenantMediaDto>, ownerId: String) {
        database.withTransaction {
            val cacheDir = java.io.File(context.cacheDir, "tenant_photos")
            for (remoteDto in mediaList) {
                // Owner verification: Accept media if owner matches, or if owner is default_owner, or if tenant exists in local DB
                if (remoteDto.ownerId.isNotBlank() && ownerId.isNotBlank() && remoteDto.ownerId != ownerId && remoteDto.ownerId != "default_owner") {
                    val localTenant = tenantDao.getTenantByCloudId(remoteDto.tenantCloudId)
                    if (localTenant == null) continue
                }

                // Canonical keying: Profile photo records MUST always be key-mapped to ${tenantCloudId}_PROFILE_PHOTO
                val canonicalCloudId = if (remoteDto.mediaType == "PROFILE_PHOTO") {
                    "${remoteDto.tenantCloudId}_PROFILE_PHOTO"
                } else if (remoteDto.cloudId.isNotBlank()) {
                    remoteDto.cloudId
                } else {
                    "${remoteDto.tenantCloudId}_DOC_${java.util.UUID.randomUUID().toString().take(8)}"
                }

                val local = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
                    ?: if (remoteDto.tenantCloudId.isNotBlank() && remoteDto.mediaType == "PROFILE_PHOTO") tenantMediaDao.getProfilePhoto(remoteDto.tenantCloudId) else null

                if (remoteDto.tenantCloudId.isNotBlank() && remoteDto.mediaType == "PROFILE_PHOTO") {
                    tenantMediaDao.deleteNonCanonicalProfilePhotos(remoteDto.tenantCloudId, canonicalCloudId)
                }

                val docsCacheDir = java.io.File(context.cacheDir, "tenant_docs")
                val safeDocName = remoteDto.fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifBlank { "doc.pdf" }

                val targetFile = if (remoteDto.mediaType == "PROFILE_PHOTO" && remoteDto.driveFileId.isNotBlank()) {
                    java.io.File(cacheDir, "${remoteDto.tenantCloudId}_${remoteDto.driveFileId}.jpg")
                } else if (remoteDto.driveFileId.isNotBlank()) {
                    java.io.File(docsCacheDir, "${canonicalCloudId}_${remoteDto.driveFileId}_$safeDocName")
                } else null

                val localPath = if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                    targetFile.absolutePath
                } else if (local != null && local.localFilePath.isNotBlank() && local.driveFileId == remoteDto.driveFileId && java.io.File(local.localFilePath).exists()) {
                    local.localFilePath
                } else {
                    ""
                }

                if (local == null) {
                    tenantMediaDao.insertOrUpdate(
                        remoteDto.toEntity(localFilePath = localPath).copy(
                            cloudId = canonicalCloudId,
                            syncStatus = "SYNCED",
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Update if remote timestamp is newer OR if driveFileId has changed
                    if (remoteDto.updatedAt >= local.updatedAt || (remoteDto.driveFileId.isNotBlank() && remoteDto.driveFileId != local.driveFileId)) {
                        tenantMediaDao.insertOrUpdate(
                            remoteDto.toEntity(localFilePath = localPath).copy(
                                cloudId = canonicalCloudId,
                                syncStatus = "SYNCED",
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun reconcileRemoteFolderMappings(mappings: Map<String, String>) {
        database.withTransaction {
            for ((key, folderId) in mappings) {
                if (key.isNotBlank() && folderId.isNotBlank()) {
                    driveFolderDao.insertFolderMapping(
                        com.example.data.database.DriveFolderMappingEntity(
                            folderPathKey = key,
                            driveFolderId = folderId,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
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
                            val localTenant = conflict.entityId.toIntOrNull()?.let { tenantDao.getTenantByIdIncludingDeleted(it) }
                            val docId = when {
                                localTenant?.cloudId?.isNotBlank() == true -> localTenant.cloudId
                                conflict.entityId.startsWith("tenant_") -> conflict.entityId
                                else -> "tenant_${conflict.entityId}"
                            }
                            val res = firestoreTenantRepository.getTenant(docId)
                            if (res is PgResult.Success && res.data != null) {
                                val t = res.data
                                tenantDao.insertTenant(
                                    t.toEntity().copy(
                                        id = localTenant?.id ?: 0,
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
                            if (localTenant?.cloudId?.isNotBlank() == true) {
                                syncQueueDao.deleteByEntity("TENANT", localTenant.cloudId)
                            }
                        }
                        "PAYMENT" -> {
                            val localPayment = conflict.entityId.toIntOrNull()?.let { rentPaymentDao.getPaymentByIdIncludingDeleted(it) }
                            val docId = when {
                                localPayment?.cloudId?.isNotBlank() == true -> localPayment.cloudId
                                conflict.entityId.startsWith("payment_") -> conflict.entityId
                                else -> "payment_${conflict.entityId}"
                            }
                            val res = firestorePaymentRepository.getPayment(docId)
                            if (res is PgResult.Success && res.data != null) {
                                val p = res.data
                                rentPaymentDao.insertPayment(
                                    p.toEntity().copy(
                                        id = localPayment?.id ?: 0,
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
                            if (localPayment?.cloudId?.isNotBlank() == true) {
                                syncQueueDao.deleteByEntity("PAYMENT", localPayment.cloudId)
                            }
                        }
                        "EXPENSE" -> {
                            val localExpense = conflict.entityId.toIntOrNull()?.let { expenseDao.getExpenseByIdIncludingDeleted(it) }
                            val docId = when {
                                localExpense?.cloudId?.isNotBlank() == true -> localExpense.cloudId
                                conflict.entityId.startsWith("expense_") -> conflict.entityId
                                else -> "expense_${conflict.entityId}"
                            }
                            val res = firestoreExpenseRepository.getExpense(docId)
                            if (res is PgResult.Success && res.data != null) {
                                val e = res.data
                                expenseDao.insertExpense(
                                    e.toEntity().copy(
                                        id = localExpense?.id ?: 0,
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
                            if (localExpense?.cloudId?.isNotBlank() == true) {
                                syncQueueDao.deleteByEntity("EXPENSE", localExpense.cloudId)
                            }
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
