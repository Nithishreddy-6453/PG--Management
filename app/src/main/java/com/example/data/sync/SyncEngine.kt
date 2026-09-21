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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictResolutionStrategy {
    KEEP_LOCAL,
    KEEP_REMOTE
}

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

    suspend fun syncNow(): PgResult<Unit> {
        val ownerId = currentOwnerId
        if (ownerId.isNullOrBlank()) {
            logger.i(TAG, "Sync skipped: User not authenticated")
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
                    syncQueueDao.deleteOperation(op.id)
                }
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val dto = room.toDto(ownerId, myDeviceId).copy(
                updatedAt = System.currentTimeMillis(),
                version = room.version + 1,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreRoomRepository.saveRoom(dto)) {
                is PgResult.Success -> {
                    roomDao.updateRoom(
                        room.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = room.version + 1,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
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
                is PgResult.Success -> syncQueueDao.deleteOperation(op.id)
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val dto = tenant.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = System.currentTimeMillis(),
                version = tenant.version + 1,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreTenantRepository.saveTenant(dto)) {
                is PgResult.Success -> {
                    tenantDao.updateTenant(
                        tenant.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = tenant.version + 1,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
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
                is PgResult.Success -> syncQueueDao.deleteOperation(op.id)
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val dto = payment.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = System.currentTimeMillis(),
                version = payment.version + 1,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestorePaymentRepository.savePayment(dto)) {
                is PgResult.Success -> {
                    rentPaymentDao.updatePayment(
                        payment.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = payment.version + 1,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
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
                is PgResult.Success -> syncQueueDao.deleteOperation(op.id)
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val dto = expense.toDto(ownerId, myDeviceId).copy(
                id = docId,
                updatedAt = System.currentTimeMillis(),
                version = expense.version + 1,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestoreExpenseRepository.saveExpense(dto)) {
                is PgResult.Success -> {
                    expenseDao.updateExpense(
                        expense.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = expense.version + 1,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
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
                is PgResult.Success -> syncQueueDao.deleteOperation(op.id)
                is PgResult.Failure -> throw Exception(res.error.message)
            }
        } else {
            val dto = property.toDto(ownerId, myDeviceId).copy(
                updatedAt = System.currentTimeMillis(),
                version = property.version + 1,
                lastModifiedByDeviceId = myDeviceId
            )
            when (val res = firestorePropertyRepository.saveProperty(dto)) {
                is PgResult.Success -> {
                    propertyDao.updateProperty(
                        property.copy(
                            syncStatus = "SYNCED",
                            ownerId = ownerId,
                            version = property.version + 1,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    syncQueueDao.deleteOperation(op.id)
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

        val dto = profile.toPropertyDto(ownerId).copy(
            updatedAt = System.currentTimeMillis()
        )
        when (val res = firestorePropertyRepository.saveProperty(dto)) {
            is PgResult.Success -> {
                ownerProfileDao.insertProfile(
                    profile.copy(
                        syncStatus = "SYNCED",
                        ownerId = ownerId,
                        lastSyncedAt = System.currentTimeMillis()
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

    suspend fun reconcileRemoteProperties(properties: List<PropertyDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in properties) {
                if (remoteDto.ownerId != ownerId) continue
                val local = propertyDao.getPropertyIncludingDeleted(remoteDto.id)
                if (remoteDto.lastModifiedByDeviceId == myDeviceId && local != null && local.syncStatus == "SYNCED") {
                    continue
                }

                if (local == null) {
                    propertyDao.insertProperty(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            deleted = remoteDto.deleted
                        )
                    )
                } else if (local.syncStatus != "PENDING_UPLOAD") {
                    if (remoteDto.updatedAt > local.updatedAt || remoteDto.version > local.version) {
                        propertyDao.updateProperty(
                            remoteDto.toEntity().copy(
                                ownerId = ownerId,
                                syncStatus = "SYNCED",
                                version = remoteDto.version,
                                updatedAt = remoteDto.updatedAt,
                                lastSyncedAt = System.currentTimeMillis(),
                                deleted = remoteDto.deleted
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun reconcileRemoteRooms(rooms: List<RoomDto>, ownerId: String) {
        val myDeviceId = deviceIdentityManager.getDeviceId()
        database.withTransaction {
            for (remoteDto in rooms) {
                if (remoteDto.ownerId != ownerId) continue
                // Skip echo if change originated on this device and is already marked synced
                val local = roomDao.getRoomIncludingDeleted(remoteDto.roomNumber)
                if (remoteDto.lastModifiedByDeviceId == myDeviceId && local != null && local.syncStatus == "SYNCED") {
                    continue
                }

                if (local == null) {
                    roomDao.insertRoom(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            deleted = remoteDto.deleted
                        )
                    )
                } else {
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
                        localDeviceId = myDeviceId
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
                                    deleted = remoteDto.deleted
                                )
                            )
                        }
                        is ConflictResult.ConflictDetected -> {
                            val record = ConflictRecordEntity(
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
                            conflictRecordDao.insertConflict(record)
                            roomDao.updateRoom(local.copy(syncStatus = "CONFLICT"))
                        }
                        is ConflictResult.UseLocal -> { /* Retain local un-uploaded changes */ }
                    }
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
                if (remoteDto.lastModifiedByDeviceId == myDeviceId && local != null && local.syncStatus == "SYNCED") {
                    continue
                }

                if (local == null) {
                    tenantDao.insertTenant(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            deleted = remoteDto.deleted
                        )
                    )
                } else {
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
                        localDeviceId = myDeviceId
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
                                    deleted = remoteDto.deleted
                                )
                            )
                        }
                        is ConflictResult.ConflictDetected -> {
                            val record = ConflictRecordEntity(
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
                            conflictRecordDao.insertConflict(record)
                            tenantDao.updateTenant(local.copy(syncStatus = "CONFLICT"))
                        }
                        is ConflictResult.UseLocal -> { /* Retain local */ }
                    }
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
                if (remoteDto.lastModifiedByDeviceId == myDeviceId && local != null && local.syncStatus == "SYNCED") {
                    continue
                }

                if (local == null) {
                    rentPaymentDao.insertPayment(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            deleted = remoteDto.deleted
                        )
                    )
                } else {
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
                        localDeviceId = myDeviceId
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
                                    deleted = remoteDto.deleted
                                )
                            )
                        }
                        is ConflictResult.ConflictDetected -> {
                            val record = ConflictRecordEntity(
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
                            conflictRecordDao.insertConflict(record)
                            rentPaymentDao.updatePayment(local.copy(syncStatus = "CONFLICT"))
                        }
                        is ConflictResult.UseLocal -> { /* Retain local */ }
                    }
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
                if (remoteDto.lastModifiedByDeviceId == myDeviceId && local != null && local.syncStatus == "SYNCED") {
                    continue
                }

                if (local == null) {
                    expenseDao.insertExpense(
                        remoteDto.toEntity().copy(
                            ownerId = ownerId,
                            syncStatus = "SYNCED",
                            version = remoteDto.version,
                            updatedAt = remoteDto.updatedAt,
                            lastSyncedAt = System.currentTimeMillis(),
                            deleted = remoteDto.deleted
                        )
                    )
                } else {
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
                        localDeviceId = myDeviceId
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
                                    deleted = remoteDto.deleted
                                )
                            )
                        }
                        is ConflictResult.ConflictDetected -> {
                            val record = ConflictRecordEntity(
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
                            conflictRecordDao.insertConflict(record)
                            expenseDao.updateExpense(local.copy(syncStatus = "CONFLICT"))
                        }
                        is ConflictResult.UseLocal -> { /* Retain local */ }
                    }
                }
            }
        }
    }

    suspend fun resolveConflict(conflictId: String, strategy: ConflictResolutionStrategy): PgResult<Unit> {
        val conflict = conflictRecordDao.getActiveConflicts().find { it.id == conflictId }
            ?: return PgResult.Failure(PgError.DatabaseError("Conflict record not found"))

        val myDeviceId = deviceIdentityManager.getDeviceId()
        return try {
            when (strategy) {
                ConflictResolutionStrategy.KEEP_LOCAL -> {
                    // Mark local version for re-upload and enqueue
                    when (conflict.entityType) {
                        "ROOM" -> {
                            val local = roomDao.getRoomIncludingDeleted(conflict.entityId)
                            if (local != null) {
                                roomDao.updateRoom(local.copy(syncStatus = "PENDING_UPLOAD", updatedAt = System.currentTimeMillis()))
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
                                tenantDao.updateTenant(local.copy(syncStatus = "PENDING_UPLOAD", updatedAt = System.currentTimeMillis()))
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
                                rentPaymentDao.updatePayment(local.copy(syncStatus = "PENDING_UPLOAD", updatedAt = System.currentTimeMillis()))
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
                                expenseDao.updateExpense(local.copy(syncStatus = "PENDING_UPLOAD", updatedAt = System.currentTimeMillis()))
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
                    // Fetch remote copy from Firestore and overwrite local Room
                    val ownerId = currentOwnerId ?: "default_owner"
                    when (conflict.entityType) {
                        "ROOM" -> {
                            val res = firestoreRoomRepository.getRoom(conflict.entityId)
                            if (res is PgResult.Success && res.data != null) {
                                roomDao.insertRoom(
                                    res.data.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        "TENANT" -> {
                            val res = firestoreTenantRepository.getTenant("tenant_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                tenantDao.insertTenant(
                                    res.data.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        "PAYMENT" -> {
                            val res = firestorePaymentRepository.getPayment("payment_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                rentPaymentDao.insertPayment(
                                    res.data.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        "EXPENSE" -> {
                            val res = firestoreExpenseRepository.getExpense("expense_${conflict.entityId}")
                            if (res is PgResult.Success && res.data != null) {
                                expenseDao.insertExpense(
                                    res.data.toEntity().copy(
                                        ownerId = ownerId,
                                        syncStatus = "SYNCED",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }
            conflictRecordDao.updateConflict(conflict.copy(status = "RESOLVED"))
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
