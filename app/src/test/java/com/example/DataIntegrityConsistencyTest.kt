package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.PgLogger
import com.example.core.common.PgLoggerImpl
import com.example.core.integrity.DataIntegrityManager
import com.example.core.integrity.IntegrityIssueType
import com.example.data.database.*
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rooms.data.repository.RoomRepositoryImpl
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.tenants.data.repository.TenantRepositoryImpl
import com.example.features.tenants.domain.repository.TenantRepository
import com.example.features.tenants.domain.usecase.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class DataIntegrityConsistencyTest {

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
    fun testCurrentBugAcceptance_ActiveTenantsWithZeroBedOccupancy_Repaired() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

        // Create Room 352 with capacity 3, but NO bed entities or assignments
        roomDao.insertRoom(RoomEntity(roomNumber = "352", floor = "3rd", capacity = 3, propertyId = propId))

        // Create 3 active tenants in Room 352 with roomNumber and bedId, but no BedAssignmentEntity
        val t1 = tenantDao.insertTenant(TenantEntity(name = "Arjun", phone = "9876543211", email = "a@a.com", emergencyContact = "123", roomNumber = "352", bedId = "Bed 1", monthlyRent = 8000.0, securityDeposit = 8000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()
        val t2 = tenantDao.insertTenant(TenantEntity(name = "Bharath", phone = "9876543212", email = "b@b.com", emergencyContact = "123", roomNumber = "352", bedId = "Bed 2", monthlyRent = 8000.0, securityDeposit = 8000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()
        val t3 = tenantDao.insertTenant(TenantEntity(name = "Rahul", phone = "9876543213", email = "r@r.com", emergencyContact = "123", roomNumber = "352", bedId = "Bed 3", monthlyRent = 8000.0, securityDeposit = 8000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()

        // 1. Scan before repair: identifies missing physical beds & active tenants without assignments
        val scanReport = integrityManager.performScan(propId, "2026-10-03")
        assertTrue(scanReport.issuesFound.isNotEmpty())
        assertTrue(scanReport.issuesFound.any { it.type == IntegrityIssueType.MISSING_PHYSICAL_BEDS_IN_ROOM || it.type == IntegrityIssueType.A_ACTIVE_TENANT_NO_ASSIGNMENT })

        // 2. Perform safe repair
        val repairReport = integrityManager.performSafeRepair(propId, "2026-10-03")
        assertTrue(repairReport.automaticallyRepairedCount > 0)
        assertEquals(0, repairReport.reviewItemsCount)

        // 3. Verify Room Summary after repair
        val summaries = roomRepository.getRoomsSummariesFlow().first()
        val summary352 = summaries.firstOrNull { it.roomNumber == "352" }
        assertNotNull(summary352)
        assertEquals(3, summary352?.totalBeds)
        assertEquals(3, summary352?.usableBeds)
        assertEquals(3, summary352?.occupiedBeds)
        assertEquals(3, summary352?.activeTenantCount)
        assertEquals(0, summary352?.availableBeds)
        assertEquals("Full", summary352?.occupancyStatus)

        // 4. Verify post-repair scan is clean
        val postScan = integrityManager.performScan(propId, "2026-10-03")
        assertTrue(postScan.isClean)
    }

    @Test
    fun testRequiredAcceptanceTestSection21_Room352_Vacate_Transfer() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

        // Create Room 352 (capacity 3) and Room 404 (capacity 2)
        roomRepository.insertRoom(RoomEntity(roomNumber = "352", floor = "3rd", capacity = 3, ratePerBed = 7000.0, propertyId = propId))
        roomRepository.insertRoom(RoomEntity(roomNumber = "404", floor = "4th", capacity = 2, ratePerBed = 8000.0, propertyId = propId))

        val validateTenantUseCase = ValidateTenantUseCase()
        val addTenantUseCase = AddTenantUseCase(tenantRepository, validateTenantUseCase)

        // Add 3 tenants to Room 352
        val r1 = addTenantUseCase.execute(name = "Arjun", phone = "9111111111", email = "a@a.com", emergencyContact = "9111111112", roomNumber = "352", bedId = "Bed 1", monthlyRent = 7000.0, securityDeposit = 7000.0, advancePaid = 0.0, moveInDate = "2026-10-01", kycDocType = "None")
        val r2 = addTenantUseCase.execute(name = "Bharath", phone = "9222222222", email = "b@b.com", emergencyContact = "9222222223", roomNumber = "352", bedId = "Bed 2", monthlyRent = 7000.0, securityDeposit = 7000.0, advancePaid = 0.0, moveInDate = "2026-10-01", kycDocType = "None")
        val r3 = addTenantUseCase.execute(name = "Rahul", phone = "9333333333", email = "r@r.com", emergencyContact = "9333333334", roomNumber = "352", bedId = "Bed 3", monthlyRent = 7000.0, securityDeposit = 7000.0, advancePaid = 0.0, moveInDate = "2026-10-01", kycDocType = "None")

        assertTrue(r1 is TenantValidationResult.Success)
        assertTrue(r2 is TenantValidationResult.Success)
        assertTrue(r3 is TenantValidationResult.Success)

        // Verify initial state: Room 352 is Full, 3 / 3 occupied, 0 available
        val summary352Initial = roomRepository.getRoomSummaryFlow("352").first()
        assertNotNull(summary352Initial)
        assertEquals(3, summary352Initial?.occupiedBeds)
        assertEquals(3, summary352Initial?.activeTenantCount)
        assertEquals(0, summary352Initial?.availableBeds)
        assertEquals("Full", summary352Initial?.occupancyStatus)

        // Step 2: Vacate Bharath with future leaving date (e.g. Oct 15, current date = Oct 3)
        val bharath = tenantDao.getAllTenants().first { it.name == "Bharath" }
        val vacateResult = roomRepository.vacateTenant(tenantId = bharath.id, leavingDate = "2026-10-15", currentDateOverride = "2026-10-03")
        assertTrue(vacateResult is RoomValidationResult.Success)

        // Before leaving date (Oct 3): Still 3/3 occupied, has upcoming vacancy
        val summaryBeforeLeave = roomRepository.getRoomSummaryFlow("352").first()
        assertEquals(3, summaryBeforeLeave?.occupiedBeds)
        assertEquals(3, summaryBeforeLeave?.activeTenantCount)
        assertEquals(0, summaryBeforeLeave?.availableBeds)
        assertTrue(summaryBeforeLeave?.hasUpcomingVacancy == true)

        // After effective leaving date (Simulate Oct 16): 2 / 3 occupied, 1 available
        val bharathVacatedImmediate = roomRepository.vacateTenant(tenantId = bharath.id, leavingDate = "2026-10-15", currentDateOverride = "2026-10-16")
        assertTrue(bharathVacatedImmediate is RoomValidationResult.Success)

        val summaryAfterLeave = roomRepository.getRoomSummaryFlow("352").first()
        assertEquals(2, summaryAfterLeave?.occupiedBeds)
        assertEquals(2, summaryAfterLeave?.activeTenantCount)
        assertEquals(1, summaryAfterLeave?.availableBeds)
        assertEquals("Partially Occupied", summaryAfterLeave?.occupancyStatus)

        // Step 3: Transfer Arjun: Room 352 / Bed 1 -> Room 404 / Bed 2
        val arjun = tenantDao.getAllTenants().first { it.name == "Arjun" }
        val transferResult = roomRepository.transferTenant(tenantId = arjun.id, newRoomNumber = "404", newBedId = "Bed 2", transferDate = "2026-10-03", newAgreedRent = 8000.0)
        assertTrue(transferResult is RoomValidationResult.Success)

        // Expected: Room 352 decreases by 1 occupant -> 1 / 3 occupied
        val summary352AfterTransfer = roomRepository.getRoomSummaryFlow("352").first()
        assertEquals(1, summary352AfterTransfer?.occupiedBeds)
        assertEquals(1, summary352AfterTransfer?.activeTenantCount)
        assertEquals(2, summary352AfterTransfer?.availableBeds)

        // Expected: Room 404 increases by 1 occupant -> 1 / 2 occupied
        val summary404AfterTransfer = roomRepository.getRoomSummaryFlow("404").first()
        assertEquals(1, summary404AfterTransfer?.occupiedBeds)
        assertEquals(1, summary404AfterTransfer?.activeTenantCount)
        assertEquals(1, summary404AfterTransfer?.availableBeds)

        // Historical assignment remains preserved
        val arjunAssignments = bedAssignmentDao.getAssignmentsForTenant(propId, arjun.id)
        assertEquals(2, arjunAssignments.size)
        assertTrue(arjunAssignments.any { it.roomNumber == "352" && it.bedId == "Bed 1" && it.endDate != null })
        assertTrue(arjunAssignments.any { it.roomNumber == "404" && it.bedId == "Bed 2" && it.endDate == null })
    }

    @Test
    fun testBlockedBedConstraint() = runTest(testDispatcher) {
        val propId = "property_default"
        propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))
        roomRepository.insertRoom(RoomEntity(roomNumber = "201", floor = "2nd", capacity = 2, ratePerBed = 6000.0, propertyId = propId))

        // Block Bed 1
        val blockRes = roomRepository.blockBed("201", "Bed 1", blocked = true)
        assertTrue(blockRes is RoomValidationResult.Success)

        val summary = roomRepository.getRoomSummaryFlow("201").first()
        assertEquals(1, summary?.blockedBeds)
        assertEquals(1, summary?.usableBeds)
        assertEquals(1, summary?.availableBeds)

        // Attempt to assign tenant to blocked bed should fail
        val assignRes = roomRepository.assignTenantToBed("201", "Bed 1", tenantId = 999, startDate = "2026-10-01", agreedRent = 6000.0)
        assertTrue(assignRes is RoomValidationResult.Error)
    }
}
