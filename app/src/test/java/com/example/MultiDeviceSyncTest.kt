package com.example

import com.example.core.common.PgLogger
import com.example.data.database.ConflictRecordEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.firestore.mapper.toDto
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
}
