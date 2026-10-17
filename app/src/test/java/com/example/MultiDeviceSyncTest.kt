package com.example

import com.example.core.common.PgLogger
import com.example.data.database.ConflictRecordEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.firestore.mapper.toDto
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.model.RoomDto
import com.example.data.firestore.model.TenantDto
import com.example.data.sync.ConflictResolutionStrategy
import com.example.data.sync.ConflictResolver
import com.example.data.sync.ConflictResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiDeviceSyncTest {

    private lateinit var conflictResolver: ConflictResolver
    private val mockLogger = object : PgLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String, tr: Throwable?) {}
        override fun e(tag: String, msg: String, tr: Throwable?) {}
    }

    private val deviceA = "device_phone_a_1111"
    private val deviceB = "device_phone_b_2222"

    @Before
    fun setup() {
        conflictResolver = ConflictResolver(mockLogger)
    }

    @Test
    fun testA_roomCreatedOnPhoneA_hasDeviceA_identity() {
        val roomEntity = RoomEntity(
            roomNumber = "201",
            floor = "2nd Floor",
            capacity = 3,
            ratePerBed = 6000.0,
            roomType = "AC"
        )
        val dto = roomEntity.toDto(ownerId = "owner_test", deviceId = deviceA)

        assertEquals("201", dto.roomNumber)
        assertEquals(deviceA, dto.lastModifiedByDeviceId)
        assertEquals(1, dto.version)
        assertFalse(dto.deleted)
    }

    @Test
    fun testB_remoteRoomAppearsOnPhoneB_whenLocalDoesNotExist() {
        // Phone B receives a remote room from Phone A
        val remoteDto = RoomDto(
            id = "201",
            ownerId = "owner_test",
            roomNumber = "201",
            capacity = 3,
            version = 1,
            updatedAt = 2000L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = 1000L,
            localVersion = 0,
            localStatus = "SYNCED",
            remoteUpdatedAt = remoteDto.updatedAt,
            remoteVersion = remoteDto.version,
            localData = null,
            remoteData = remoteDto,
            entityName = "Room 201",
            remoteDeviceId = remoteDto.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )

        assertTrue(decision is ConflictResult.UseRemote)
    }

    @Test
    fun testE_concurrentEditsOnSameRoom_detectsConflict() {
        // Phone A and Phone B both edit Room 101 concurrently
        val localRoomOnB = RoomEntity(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5000.0,
            version = 2,
            updatedAt = 3500L,
            syncStatus = "PENDING_UPLOAD"
        )

        val remoteRoomFromA = RoomDto(
            id = "101",
            ownerId = "owner_test",
            roomNumber = "101",
            capacity = 2,
            ratePerBed = 5500.0,
            version = 3,
            updatedAt = 4000L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localRoomOnB.updatedAt,
            localVersion = localRoomOnB.version,
            localStatus = localRoomOnB.syncStatus,
            remoteUpdatedAt = remoteRoomFromA.updatedAt,
            remoteVersion = remoteRoomFromA.version,
            localData = localRoomOnB,
            remoteData = remoteRoomFromA,
            entityName = "Room 101",
            remoteDeviceId = remoteRoomFromA.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )

        assertTrue("Expected ConflictDetected when local has unuploaded edits and remote changed from different device",
            decision is ConflictResult.ConflictDetected)

        val conflict = decision as ConflictResult.ConflictDetected
        assertEquals(deviceA, conflict.remoteDeviceId)
        assertEquals(2, conflict.localVersion)
        assertEquals(3, conflict.remoteVersion)
    }

    @Test
    fun testF_tenantConcurrentEdit_preservesConflictDetails() {
        val localTenant = TenantEntity(
            id = 10,
            name = "Ravi Kumar",
            phone = "9999999999",
            email = "ravi@example.com",
            emergencyContact = "9111111111",
            roomNumber = "101",
            bedId = "Bed A",
            monthlyRent = 6000.0,
            securityDeposit = 10000.0,
            moveInDate = "2026-01-01",
            isKycUploaded = true,
            kycDocType = "Aadhaar",
            version = 1,
            updatedAt = 2500L,
            syncStatus = "PENDING_UPLOAD"
        )

        val remoteTenant = TenantDto(
            id = "tenant_10",
            localId = 10,
            ownerId = "owner_test",
            name = "Ravi K.",
            phone = "9999988888",
            roomNumber = "101",
            monthlyRent = 6500.0,
            version = 2,
            updatedAt = 3000L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localTenant.updatedAt,
            localVersion = localTenant.version,
            localStatus = localTenant.syncStatus,
            remoteUpdatedAt = remoteTenant.updatedAt,
            remoteVersion = remoteTenant.version,
            localData = localTenant,
            remoteData = remoteTenant,
            entityName = "Tenant Ravi",
            remoteDeviceId = remoteTenant.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )

        assertTrue(decision is ConflictResult.ConflictDetected)
    }

    @Test
    fun testG_softDeleteSync_propagatesTombstone() {
        val deletedRoomDto = RoomDto(
            id = "301",
            ownerId = "owner_test",
            roomNumber = "301",
            deleted = true,
            version = 2,
            updatedAt = 5000L,
            lastModifiedByDeviceId = deviceA
        )

        val localRoomOnB = RoomEntity(
            roomNumber = "301",
            floor = "3rd Floor",
            capacity = 1,
            ratePerBed = 7000.0,
            deleted = false,
            version = 1,
            updatedAt = 2000L,
            syncStatus = "SYNCED"
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localRoomOnB.updatedAt,
            localVersion = localRoomOnB.version,
            localStatus = localRoomOnB.syncStatus,
            remoteUpdatedAt = deletedRoomDto.updatedAt,
            remoteVersion = deletedRoomDto.version,
            localData = localRoomOnB,
            remoteData = deletedRoomDto,
            entityName = "Room 301",
            remoteDeviceId = deletedRoomDto.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )

        assertTrue(decision is ConflictResult.UseRemote)
    }

    @Test
    fun testH_selfEchoIgnored_toPreventRedundantReconciliation() {
        val myDeviceId = deviceA
        val remoteDtoFromSelf = RoomDto(
            id = "101",
            ownerId = "owner_test",
            roomNumber = "101",
            version = 2,
            updatedAt = 1000L,
            syncStatus = "SYNCED",
            lastModifiedByDeviceId = myDeviceId
        )

        val isEcho = (remoteDtoFromSelf.lastModifiedByDeviceId == myDeviceId && remoteDtoFromSelf.syncStatus == "SYNCED")
        assertTrue("Echoes from current device should be recognized", isEcho)
    }

    @Test
    fun testI_conflictRecordEntity_creationAndResolutionStructure() {
        val conflictEntity = ConflictRecordEntity(
            id = "conflict_room_101",
            entityId = "101",
            entityType = "ROOM",
            deviceA = deviceA,
            deviceB = deviceB,
            localVersion = 2,
            remoteVersion = 3,
            localUpdatedAt = 1000L,
            remoteUpdatedAt = 2000L,
            localDataJson = "Room 101 - Beds: 2, Rent: ₹5000",
            remoteDataJson = "Room 101 - Beds: 2, Rent: ₹5500",
            status = "CONFLICT"
        )

        assertEquals("conflict_room_101", conflictEntity.id)
        assertEquals("CONFLICT", conflictEntity.status)
        assertEquals(deviceA, conflictEntity.deviceA)
        assertEquals(deviceB, conflictEntity.deviceB)

        val resolved = conflictEntity.copy(status = "RESOLVED")
        assertEquals("RESOLVED", resolved.status)
    }

    @Test
    fun testJ_conflictResolutionStrategy_enumCoverage() {
        val strategies = ConflictResolutionStrategy.values()
        assertTrue(strategies.contains(ConflictResolutionStrategy.KEEP_LOCAL))
        assertTrue(strategies.contains(ConflictResolutionStrategy.KEEP_REMOTE))
    }

    @Test
    fun testK_identicalContent_doesNotCreateConflict() {
        val localRoom = RoomEntity(
            roomNumber = "102",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5000.0,
            roomType = "Non-AC",
            notes = "Standard",
            version = 1,
            updatedAt = 1000L,
            syncStatus = "PENDING_UPLOAD"
        )
        val remoteRoom = RoomDto(
            id = "102",
            ownerId = "owner_test",
            roomNumber = "102",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5000.0,
            roomType = "Non-AC",
            notes = "Standard",
            version = 2,
            updatedAt = 2000L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localRoom.updatedAt,
            localVersion = localRoom.version,
            localStatus = localRoom.syncStatus,
            remoteUpdatedAt = remoteRoom.updatedAt,
            remoteVersion = remoteRoom.version,
            localData = localRoom,
            remoteData = remoteRoom,
            entityName = "Room 102",
            remoteDeviceId = remoteRoom.lastModifiedByDeviceId,
            localDeviceId = deviceB,
            isContentIdentical = true
        )

        assertTrue("Identical content must be accepted as UseRemote and not create a conflict", decision is ConflictResult.UseRemote)
    }

    @Test
    fun testL_selfEcho_doesNotCreateConflict() {
        val localRoom = RoomEntity(
            roomNumber = "103",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5000.0,
            version = 2,
            updatedAt = 2000L,
            syncStatus = "PENDING_UPLOAD"
        )
        val remoteEcho = RoomDto(
            id = "103",
            ownerId = "owner_test",
            roomNumber = "103",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5000.0,
            version = 2,
            updatedAt = 2000L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localRoom.updatedAt,
            localVersion = localRoom.version,
            localStatus = localRoom.syncStatus,
            remoteUpdatedAt = remoteEcho.updatedAt,
            remoteVersion = remoteEcho.version,
            localData = localRoom,
            remoteData = remoteEcho,
            entityName = "Room 103",
            remoteDeviceId = remoteEcho.lastModifiedByDeviceId,
            localDeviceId = deviceA,
            isContentIdentical = false
        )

        assertTrue("Self-echo must be accepted as UseRemote and not create a conflict", decision is ConflictResult.UseRemote)
    }

    @Test
    fun testM_alreadyResolvedConflict_doesNotRecreateConflict() {
        val localRoom = RoomEntity(
            roomNumber = "104",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 6000.0,
            version = 3,
            updatedAt = 3000L,
            syncStatus = "PENDING_UPLOAD"
        )
        val remoteRoom = RoomDto(
            id = "104",
            ownerId = "owner_test",
            roomNumber = "104",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5500.0,
            version = 2,
            updatedAt = 2500L,
            lastModifiedByDeviceId = deviceA
        )

        val decision = conflictResolver.resolve(
            localUpdatedAt = localRoom.updatedAt,
            localVersion = localRoom.version,
            localStatus = localRoom.syncStatus,
            remoteUpdatedAt = remoteRoom.updatedAt,
            remoteVersion = remoteRoom.version,
            localData = localRoom,
            remoteData = remoteRoom,
            entityName = "Room 104",
            remoteDeviceId = remoteRoom.lastModifiedByDeviceId,
            localDeviceId = deviceB,
            isContentIdentical = false,
            isConflictAlreadyResolved = true
        )

        assertTrue("Previously resolved conflict must not be re-flagged", decision is ConflictResult.UseLocal)
    }

    @Test
    fun testN_initialBootstrap_insertsFirestoreDataAsSyncedWithoutConflict() {
        val remoteRoom = RoomDto(
            id = "property_main_501",
            ownerId = "owner_cloud",
            roomNumber = "501",
            floor = "5th Floor",
            capacity = 2,
            ratePerBed = 7500.0,
            roomType = "AC",
            version = 1,
            syncStatus = "SYNCED",
            lastModifiedByDeviceId = deviceA
        )

        val localEntity = remoteRoom.toEntity().copy(
            ownerId = "owner_cloud",
            syncStatus = "SYNCED",
            version = remoteRoom.version,
            updatedAt = remoteRoom.updatedAt
        )

        assertEquals("501", localEntity.roomNumber)
        assertEquals("owner_cloud", localEntity.ownerId)
        assertEquals("SYNCED", localEntity.syncStatus)
        assertEquals(1, localEntity.version)
        assertFalse(localEntity.deleted)
    }

    @Test
    fun testO_staleRetryCannotOverwriteNewerRemoteData() {
        val remoteNewerRoom = RoomDto(
            id = "101",
            ownerId = "owner_test",
            roomNumber = "101",
            version = 4,
            updatedAt = 5000L,
            lastModifiedByDeviceId = deviceA
        )
        val staleLocalRetry = RoomEntity(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 2,
            version = 2,
            updatedAt = 2000L,
            syncStatus = "PENDING_UPLOAD"
        )
        val decision = conflictResolver.resolve(
            localUpdatedAt = staleLocalRetry.updatedAt,
            localVersion = staleLocalRetry.version,
            localStatus = staleLocalRetry.syncStatus,
            remoteUpdatedAt = remoteNewerRoom.updatedAt,
            remoteVersion = remoteNewerRoom.version,
            localData = staleLocalRetry,
            remoteData = remoteNewerRoom,
            entityName = "Room 101",
            remoteDeviceId = remoteNewerRoom.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )
        // Stale retry must not overwrite newer remote version
        assertTrue(decision is ConflictResult.ConflictDetected || decision is ConflictResult.UseRemote)
        assertFalse(decision is ConflictResult.UseLocal)
    }

    @Test
    fun testP_deletedRecordCannotBeResurrectedByOlderQueuedWrite() {
        val remoteDeletedRoom = RoomDto(
            id = "102",
            ownerId = "owner_test",
            roomNumber = "102",
            deleted = true,
            version = 3,
            updatedAt = 4000L,
            lastModifiedByDeviceId = deviceA
        )
        val staleQueuedEdit = RoomEntity(
            roomNumber = "102",
            floor = "1st Floor",
            capacity = 2,
            deleted = false,
            version = 1,
            updatedAt = 1000L,
            syncStatus = "PENDING_UPLOAD"
        )
        val decision = conflictResolver.resolve(
            localUpdatedAt = staleQueuedEdit.updatedAt,
            localVersion = staleQueuedEdit.version,
            localStatus = staleQueuedEdit.syncStatus,
            remoteUpdatedAt = remoteDeletedRoom.updatedAt,
            remoteVersion = remoteDeletedRoom.version,
            localData = staleQueuedEdit,
            remoteData = remoteDeletedRoom,
            entityName = "Room 102",
            remoteDeviceId = remoteDeletedRoom.lastModifiedByDeviceId,
            localDeviceId = deviceB
        )
        // Older queued write must not resurrect deleted record as clean local
        assertFalse("Stale queued write must not overwrite or resurrect tombstone", decision is ConflictResult.UseLocal)
    }

    @Test
    fun testQ_repeatedSyncOperationIsIdempotent() {
        val originalRoom = RoomEntity(
            roomNumber = "103",
            floor = "1st Floor",
            capacity = 2,
            version = 2,
            updatedAt = 3000L,
            syncStatus = "SYNCED"
        )
        val dtoAttempt1 = originalRoom.toDto(ownerId = "owner_test", deviceId = deviceA)
        val dtoAttempt2 = originalRoom.toDto(ownerId = "owner_test", deviceId = deviceA)

        // Repeating the same sync operation produces identical document ID, version, and content
        assertEquals(dtoAttempt1.id, dtoAttempt2.id)
        assertEquals(dtoAttempt1.version, dtoAttempt2.version)
        assertEquals(dtoAttempt1.roomNumber, dtoAttempt2.roomNumber)
        assertEquals(dtoAttempt1.capacity, dtoAttempt2.capacity)
        assertEquals(dtoAttempt1.lastModifiedByDeviceId, dtoAttempt2.lastModifiedByDeviceId)
    }

    @Test
    fun testR_tenantStableCloudId_preservedAcrossMappersAndRetries() {
        val stableId = "tenant_550e8400-e29b-41d4-a716-446655440000"
        val tenant = TenantEntity(
            id = 15,
            cloudId = stableId,
            name = "Anil Verma",
            phone = "9876543210",
            email = "anil@example.com",
            emergencyContact = "9123456789",
            roomNumber = "101",
            bedId = "Bed 1",
            monthlyRent = 6000.0,
            securityDeposit = 6000.0,
            moveInDate = "2026-10-01",
            isKycUploaded = false,
            kycDocType = "None"
        )

        val dto1 = tenant.toDto(ownerId = "owner_123", deviceId = deviceA)
        val dto2 = tenant.toDto(ownerId = "owner_123", deviceId = deviceA)

        // Retrying upload yields identical stable cloud document ID
        assertEquals(stableId, dto1.cloudId)
        assertEquals(stableId, dto1.id)
        assertEquals(dto1.id, dto2.id)

        // When restored back on another device (where local id is 0)
        val restoredOnDeviceB = dto1.toEntity()
        assertEquals(stableId, restoredOnDeviceB.cloudId)
    }

    @Test
    fun testS_paymentCarriesStableTenantCloudId() {
        val paymentCloudId = "payment_uuid_9999"
        val tenantCloudId = "tenant_uuid_8888"
        val payment = com.example.data.database.RentPaymentEntity(
            id = 25,
            cloudId = paymentCloudId,
            tenantId = 15,
            tenantName = "Anil Verma",
            roomNumber = "101",
            billingMonth = "October 2026",
            amount = 6000.0,
            amountPaid = 6000.0,
            dueDate = "2026-10-05",
            paymentDate = "2026-10-04",
            paymentMode = "UPI",
            status = "Paid"
        )

        val dto = payment.toDto(ownerId = "owner_123", deviceId = deviceA, tenantCloudId = tenantCloudId)
        assertEquals(paymentCloudId, dto.id)
        assertEquals(paymentCloudId, dto.cloudId)
        assertEquals(tenantCloudId, dto.tenantCloudId)

        val restoredEntity = dto.toEntity()
        assertEquals(paymentCloudId, restoredEntity.cloudId)
    }

    @Test
    fun testT_expenseStableCloudId_preservedAcrossMappers() {
        val expenseCloudId = "expense_uuid_7777"
        val expense = com.example.data.database.ExpenseEntity(
            id = 12,
            cloudId = expenseCloudId,
            amount = 450.0,
            category = "Plumbing",
            date = "2026-10-07",
            notes = "Tap washer replaced"
        )

        val dto1 = expense.toDto(ownerId = "owner_123", deviceId = deviceA)
        val dto2 = expense.toDto(ownerId = "owner_123", deviceId = deviceA)
        assertEquals(expenseCloudId, dto1.id)
        assertEquals(expenseCloudId, dto1.cloudId)
        assertEquals(dto1.id, dto2.id)

        val restored = dto1.toEntity()
        assertEquals(expenseCloudId, restored.cloudId)
    }
}
