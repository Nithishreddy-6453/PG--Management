package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.PgLoggerImpl
import com.example.core.integrity.DataIntegrityManager
import com.example.core.util.BedIdentityUtil
import com.example.data.database.*
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rooms.data.repository.RoomRepositoryImpl
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.tenants.data.repository.TenantRepositoryImpl
import com.example.features.tenants.domain.repository.TenantRepository
import com.example.features.tenants.domain.usecase.AddTenantUseCase
import com.example.features.tenants.domain.usecase.TenantValidationResult
import com.example.features.tenants.domain.usecase.ValidateTenantUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class RoomBedIdentityConsistencyTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var bedDao: BedDao
    private lateinit var bedAssignmentDao: BedAssignmentDao
    private lateinit var propertyDao: PropertyDao
    private lateinit var roomRepository: RoomRepository
    private lateinit var tenantRepository: TenantRepository
    private lateinit var integrityManager: DataIntegrityManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        roomDao = db.roomDao()
        tenantDao = db.tenantDao()
        rentPaymentDao = db.rentPaymentDao()
        bedDao = db.bedDao()
        bedAssignmentDao = db.bedAssignmentDao()
        propertyDao = db.propertyDao()

        val currentPropertyManager = CurrentPropertyManager(context, propertyDao)
        runBlocking {
            currentPropertyManager.setCurrentPropertyId("property_default")
        }
        val logger = PgLoggerImpl()

        roomRepository = RoomRepositoryImpl(roomDao, tenantDao, rentPaymentDao, bedDao, bedAssignmentDao, currentPropertyManager, null, db)
        tenantRepository = TenantRepositoryImpl(roomDao, tenantDao, rentPaymentDao, bedDao, bedAssignmentDao, currentPropertyManager, null)
        integrityManager = DataIntegrityManager(roomDao, bedDao, bedAssignmentDao, tenantDao, propertyDao, logger, null)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testBedIdentityUtilParsingAndMatching() {
        // Numeric formats
        assertEquals(1, BedIdentityUtil.parseBedIndex("Bed 1"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("Bed1"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("B1"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("1"))
        assertEquals(2, BedIdentityUtil.parseBedIndex("Bed 2"))
        assertEquals(2, BedIdentityUtil.parseBedIndex("B2"))

        // Letter formats
        assertEquals(1, BedIdentityUtil.parseBedIndex("Bed A"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("BedA"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("BA"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("A"))
        assertEquals(1, BedIdentityUtil.parseBedIndex("a"))
        assertEquals(2, BedIdentityUtil.parseBedIndex("Bed B"))
        assertEquals(2, BedIdentityUtil.parseBedIndex("B"))

        // Cross-matching (Bed 1 == Bed A, Bed 2 == Bed B)
        assertTrue(BedIdentityUtil.matchesSameBed("Bed 1", "Bed A"))
        assertTrue(BedIdentityUtil.matchesSameBed("Bed 1", "B1"))
        assertTrue(BedIdentityUtil.matchesSameBed("Bed 1", "1"))
        assertTrue(BedIdentityUtil.matchesSameBed("Bed 2", "Bed B"))
        assertTrue(BedIdentityUtil.matchesSameBed("Bed B", "B2"))

        // Non-matches
        assertFalse(BedIdentityUtil.matchesSameBed("Bed 1", "Bed 2"))
        assertFalse(BedIdentityUtil.matchesSameBed("Bed 1", "Bed B"))
        assertFalse(BedIdentityUtil.matchesSameBed("Bed A", "Bed B"))
    }

    @Test
    fun testRoomDetailsConsistency_SingleBedRoomFull_NoExtraAvailableCard() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

        // Room 102 has capacity 1
        roomRepository.insertRoom(RoomEntity(roomNumber = "102", floor = "1st", capacity = 1, ratePerBed = 12500.0, propertyId = propId))

        // Existing physical bed table has "Bed 1"
        bedDao.insertBed(BedEntity(roomNumber = "102", bedId = "Bed 1", status = "AVAILABLE", propertyId = propId))

        // Tenant was registered with bedId = "Bed A"
        val tenantId = tenantDao.insertTenant(
            TenantEntity(
                name = "Priya Singh",
                phone = "9812345678",
                email = "priya@test.com",
                emergencyContact = "9812345679",
                roomNumber = "102",
                bedId = "Bed A",
                monthlyRent = 12500.0,
                securityDeposit = 12500.0,
                moveInDate = "2026-05-15",
                isKycUploaded = true,
                kycDocType = "Aadhaar Card",
                propertyId = propId
            )
        ).toInt()

        // Active assignment recorded for "Bed A"
        bedAssignmentDao.insertAssignment(
            BedAssignmentEntity(
                assignmentId = UUID.randomUUID().toString(),
                tenantId = tenantId,
                roomNumber = "102",
                bedId = "Bed A",
                startDate = "2026-05-15",
                endDate = null,
                agreedRent = 12500.0,
                propertyId = propId
            )
        )

        // 1. Fetch Room Summary
        val summary = roomRepository.getRoomSummaryFlow("102").first()
        assertNotNull(summary)
        assertEquals(1, summary?.totalBeds)
        assertEquals(1, summary?.occupiedBeds)
        assertEquals(0, summary?.availableBeds)
        assertEquals("Full", summary?.occupancyStatus)

        // 2. Canonical beds list strictly conforms to capacity 1
        val canonicalBeds = summary!!.canonicalPhysicalBeds
        assertEquals("Room 102 with capacity 1 must have exactly 1 canonical bed", 1, canonicalBeds.size)

        // 3. The single bed matches the active tenant assignment via BedIdentityUtil
        val singleBed = canonicalBeds.first()
        val assignment = summary.activeAssignments.firstOrNull {
            BedIdentityUtil.matchesSameBed(it.bedId, singleBed.bedId)
        }
        assertNotNull("Active assignment must match the single physical bed slot", assignment)
        assertEquals(tenantId, assignment?.tenantId)

        // 4. Verify post-repair normalizes bedId in assignment & tenant to match Bed 1
        integrityManager.performSafeRepair(propId, "2026-10-10")
        val postRepairSummary = roomRepository.getRoomSummaryFlow("102").first()
        assertEquals(1, postRepairSummary?.canonicalPhysicalBeds?.size)
        assertEquals(1, postRepairSummary?.occupiedBeds)
        assertEquals(0, postRepairSummary?.availableBeds)
    }

    @Test
    fun testRoomCapacityStrictlyEnforced_NoConflictingDuplicateCards() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

        // Room 101 has capacity 2
        roomRepository.insertRoom(RoomEntity(roomNumber = "101", floor = "1st", capacity = 2, ratePerBed = 6000.0, propertyId = propId))

        // Suppose database contains both Bed 1, Bed 2 AND a duplicate Bed A
        bedDao.insertBed(BedEntity(roomNumber = "101", bedId = "Bed 1", status = "AVAILABLE", propertyId = propId))
        bedDao.insertBed(BedEntity(roomNumber = "101", bedId = "Bed 2", status = "AVAILABLE", propertyId = propId))
        bedDao.insertBed(BedEntity(roomNumber = "101", bedId = "Bed A", status = "OCCUPIED", propertyId = propId))

        val tenantId = tenantDao.insertTenant(
            TenantEntity(
                name = "Rahul Sharma",
                phone = "9876543210",
                email = "rahul@test.com",
                emergencyContact = "9876543211",
                roomNumber = "101",
                bedId = "Bed A",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-10-01",
                isKycUploaded = false,
                kycDocType = "None",
                propertyId = propId
            )
        ).toInt()

        bedAssignmentDao.insertAssignment(
            BedAssignmentEntity(
                assignmentId = UUID.randomUUID().toString(),
                tenantId = tenantId,
                roomNumber = "101",
                bedId = "Bed A",
                startDate = "2026-10-01",
                endDate = null,
                agreedRent = 6000.0,
                propertyId = propId
            )
        )

        // Verify summary before repair
        val summary = roomRepository.getRoomSummaryFlow("101").first()
        assertNotNull(summary)
        assertEquals(2, summary?.totalBeds)
        // Canonical physical beds must never exceed totalBeds (2)
        assertEquals("Canonical physical beds must be exactly 2 for capacity 2", 2, summary?.canonicalPhysicalBeds?.size)
        assertEquals(1, summary?.occupiedBeds)
        assertEquals(1, summary?.availableBeds)

        // Slot 1 must be occupied by Rahul
        val slot1Bed = summary!!.canonicalPhysicalBeds.first { BedIdentityUtil.parseBedIndex(it.bedId) == 1 }
        val slot1Assign = summary.activeAssignments.firstOrNull { BedIdentityUtil.matchesSameBed(it.bedId, slot1Bed.bedId) }
        assertNotNull("Slot 1 must have an active assignment", slot1Assign)
        assertEquals(tenantId, slot1Assign?.tenantId)

        // Slot 2 must be available
        val slot2Bed = summary.canonicalPhysicalBeds.first { BedIdentityUtil.parseBedIndex(it.bedId) == 2 }
        val slot2Assign = summary.activeAssignments.firstOrNull { BedIdentityUtil.matchesSameBed(it.bedId, slot2Bed.bedId) }
        assertNull("Slot 2 must be free", slot2Assign)

        // Safe repair cleans up the duplicate bed entity from database
        val repairReport = integrityManager.performSafeRepair(propId, "2026-10-10")
        assertTrue(repairReport.automaticallyRepairedCount > 0)

        val bedsAfterRepair = bedDao.getBedsForRoom(propId, "101").filter { !it.deleted }
        assertEquals("Database must contain exactly 2 beds after repair", 2, bedsAfterRepair.size)
    }

    @Test
    fun testAddTenantUseCase_ReusesExistingBedMatchingSlot() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

        // Create Room 201 with capacity 2
        roomRepository.insertRoom(RoomEntity(roomNumber = "201", floor = "2nd", capacity = 2, ratePerBed = 5000.0, propertyId = propId))

        val validateTenantUseCase = ValidateTenantUseCase()
        val addTenantUseCase = AddTenantUseCase(tenantRepository, validateTenantUseCase)

        // Add tenant specifying "Bed A" when room has beds "Bed 1", "Bed 2"
        val result1 = addTenantUseCase.execute(
            name = "Karthik",
            phone = "9111111111",
            email = "k@test.com",
            emergencyContact = "9111111112",
            roomNumber = "201",
            bedId = "Bed A",
            monthlyRent = 5000.0,
            securityDeposit = 5000.0,
            advancePaid = 0.0,
            moveInDate = "2026-10-01",
            kycDocType = "None"
        )
        assertTrue(result1 is TenantValidationResult.Success)

        // Attempt to assign another tenant to "Bed 1" in Room 201 (which is the same physical slot as Bed A)
        val result2 = addTenantUseCase.execute(
            name = "Suresh",
            phone = "9222222222",
            email = "s@test.com",
            emergencyContact = "9222222223",
            roomNumber = "201",
            bedId = "Bed 1",
            monthlyRent = 5000.0,
            securityDeposit = 5000.0,
            advancePaid = 0.0,
            moveInDate = "2026-10-01",
            kycDocType = "None"
        )
        // Must be rejected because Bed 1 is already occupied by Karthik (who was assigned to Bed A)
        assertTrue("Attempt to double-assign Bed 1 when Bed A is occupied must fail", result2 is TenantValidationResult.Error)

        // Assigning to Bed 2 should succeed
        val result3 = addTenantUseCase.execute(
            name = "Ramesh",
            phone = "9333333333",
            email = "r@test.com",
            emergencyContact = "9333333334",
            roomNumber = "201",
            bedId = "Bed 2",
            monthlyRent = 5000.0,
            securityDeposit = 5000.0,
            advancePaid = 0.0,
            moveInDate = "2026-10-01",
            kycDocType = "None"
        )
        assertTrue(result3 is TenantValidationResult.Success)

        val summary = roomRepository.getRoomSummaryFlow("201").first()
        assertEquals(2, summary?.occupiedBeds)
        assertEquals(0, summary?.availableBeds)
        assertEquals("Full", summary?.occupancyStatus)
    }

    @Test
    fun testAssignTenantToBed_PreventsDuplicateOccupancyViaAlias() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))
        roomRepository.insertRoom(RoomEntity(roomNumber = "301", floor = "3rd", capacity = 2, ratePerBed = 7000.0, propertyId = propId))

        val t1Id = tenantDao.insertTenant(TenantEntity(name = "T1", phone = "911", email = "t1@t.com", emergencyContact = "123", roomNumber = "301", bedId = "Bed 1", monthlyRent = 7000.0, securityDeposit = 7000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()
        val t2Id = tenantDao.insertTenant(TenantEntity(name = "T2", phone = "922", email = "t2@t.com", emergencyContact = "123", roomNumber = "301", bedId = "Bed A", monthlyRent = 7000.0, securityDeposit = 7000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()

        val res1 = roomRepository.assignTenantToBed("301", "Bed 1", t1Id, "2026-10-01", 7000.0)
        assertTrue(res1 is RoomValidationResult.Success)

        // Attempting to assign T2 to "Bed A" (alias of Bed 1) must be rejected
        val res2 = roomRepository.assignTenantToBed("301", "Bed A", t2Id, "2026-10-01", 7000.0)
        assertTrue("Assigning to Bed A when Bed 1 is occupied must fail", res2 is RoomValidationResult.Error)
    }
}
