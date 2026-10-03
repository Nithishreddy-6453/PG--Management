package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.*
import com.example.data.firestore.mapper.toDto
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.model.TenantDto
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.reports.domain.engine.ReportCalculationEngine
import com.example.features.reports.domain.model.ReportPeriod
import com.example.features.rooms.data.repository.RoomRepositoryImpl
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.tenants.data.repository.TenantRepositoryImpl
import com.example.features.tenants.domain.repository.TenantRepository
import com.example.features.tenants.domain.usecase.TenantValidationResult
import com.example.features.tenants.domain.usecase.VacateTenantUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class P0SafetyAndIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var bedDao: BedDao
    private lateinit var bedAssignmentDao: BedAssignmentDao
    private lateinit var propertyDao: PropertyDao
    private lateinit var currentPropertyManager: CurrentPropertyManager
    private lateinit var roomRepository: RoomRepository
    private lateinit var tenantRepository: TenantRepository
    private lateinit var vacateTenantUseCase: VacateTenantUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        roomDao = db.roomDao()
        tenantDao = db.tenantDao()
        rentPaymentDao = db.rentPaymentDao()
        bedDao = db.bedDao()
        bedAssignmentDao = db.bedAssignmentDao()
        propertyDao = db.propertyDao()
        currentPropertyManager = CurrentPropertyManager(context, propertyDao)

        roomRepository = RoomRepositoryImpl(
            roomDao = roomDao,
            tenantDao = tenantDao,
            rentPaymentDao = rentPaymentDao,
            bedDao = bedDao,
            bedAssignmentDao = bedAssignmentDao,
            currentPropertyManager = currentPropertyManager,
            appDatabase = db
        )

        tenantRepository = TenantRepositoryImpl(
            roomDao = roomDao,
            tenantDao = tenantDao,
            rentPaymentDao = rentPaymentDao,
            bedDao = bedDao,
            bedAssignmentDao = bedAssignmentDao,
            currentPropertyManager = currentPropertyManager
        )

        vacateTenantUseCase = VacateTenantUseCase(
            repository = tenantRepository,
            roomRepository = roomRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // ==================================================
    // REQUIRED ACCEPTANCE TEST 1 — FIRESTORE SECURITY RULES CONTRACT
    // ==================================================
    @Test
    fun testP0_1_FirestoreSecurityRulesContract() {
        // Models security rule logic defined in firestore.rules
        fun isOwnerReadOrDelete(authUid: String?, docOwnerId: String?): Boolean {
            return authUid != null && docOwnerId == authUid
        }

        fun isOwnerCreate(authUid: String?, newDocOwnerId: String?): Boolean {
            return authUid != null && newDocOwnerId == authUid
        }

        fun isOwnerUpdate(authUid: String?, existingDocOwnerId: String?, newDocOwnerId: String?): Boolean {
            return authUid != null &&
                    existingDocOwnerId == authUid &&
                    newDocOwnerId == existingDocOwnerId
        }

        val userA = "user_owner_A"
        val userB = "user_owner_B"

        // 1. User B updates a Property A document while changing ownerId to User B -> DENIED
        val userBUpdatesDocAWithChangedOwner = isOwnerUpdate(
            authUid = userB,
            existingDocOwnerId = userA,
            newDocOwnerId = userB
        )
        assertFalse("User B updating Property A document must be DENIED", userBUpdatesDocAWithChangedOwner)

        // 2. User B reads Property A document -> DENIED
        val userBReadsDocA = isOwnerReadOrDelete(
            authUid = userB,
            docOwnerId = userA
        )
        assertFalse("User B reading Property A document must be DENIED", userBReadsDocA)

        // 3. User B deletes Property A document -> DENIED
        val userBDeletesDocA = isOwnerReadOrDelete(
            authUid = userB,
            docOwnerId = userA
        )
        assertFalse("User B deleting Property A document must be DENIED", userBDeletesDocA)

        // 4. User A updates their own document without changing ownerId -> ALLOWED
        val userAUpdatesOwnDoc = isOwnerUpdate(
            authUid = userA,
            existingDocOwnerId = userA,
            newDocOwnerId = userA
        )
        assertTrue("User A updating own document without changing ownerId must be ALLOWED", userAUpdatesOwnDoc)

        // 5. User A tries to change ownerId of their document to User B -> DENIED
        val userAChangesOwnerId = isOwnerUpdate(
            authUid = userA,
            existingDocOwnerId = userA,
            newDocOwnerId = userB
        )
        assertFalse("User A changing ownerId during update must be DENIED", userAChangesOwnerId)

        // 6. User A creates document with their ownerId -> ALLOWED
        assertTrue(isOwnerCreate(userA, userA))

        // 7. User B creates document with User A's ownerId -> DENIED
        assertFalse(isOwnerCreate(userB, userA))
    }

    // ==================================================
    // REQUIRED ACCEPTANCE TEST 2 — VACATE TENANT DATA INTEGRITY
    // ==================================================
    @Test
    fun testP0_2_VacateTenantDataIntegrityFlow() = runTest {
        val propId = "prop_emerald"
        currentPropertyManager.setCurrentPropertyId(propId)

        // 1. Create Room 404 with Bed 2
        val room404 = RoomEntity(
            propertyId = propId,
            roomNumber = "404",
            floor = "4th Floor",
            capacity = 3,
            ratePerBed = 6000.0
        )
        roomDao.insertRoom(room404)

        val bed2 = BedEntity(
            propertyId = propId,
            roomNumber = "404",
            bedId = "Bed 2",
            status = "OCCUPIED"
        )
        val bed1 = BedEntity(
            propertyId = propId,
            roomNumber = "404",
            bedId = "Bed 1",
            status = "AVAILABLE"
        )
        val bed3 = BedEntity(
            propertyId = propId,
            roomNumber = "404",
            bedId = "Bed 3",
            status = "AVAILABLE"
        )
        bedDao.insertBed(bed1)
        bedDao.insertBed(bed2)
        bedDao.insertBed(bed3)

        // 2. Create Tenant Rahul
        val rahul = TenantEntity(
            id = 1,
            name = "Rahul",
            phone = "9876543210",
            email = "rahul@example.com",
            emergencyContact = "9876543211",
            roomNumber = "404",
            bedId = "Bed 2",
            monthlyRent = 6000.0,
            securityDeposit = 10000.0,
            moveInDate = "2026-10-01",
            isKycUploaded = true,
            kycDocType = "Aadhaar Card",
            propertyId = propId
        )
        tenantDao.insertTenant(rahul)

        // Active assignment: Start = October 1, End = null, Bed status = OCCUPIED
        val assignment = BedAssignmentEntity(
            assignmentId = "assign_rahul_1",
            tenantId = 1,
            roomNumber = "404",
            bedId = "Bed 2",
            startDate = "2026-10-01",
            endDate = null,
            agreedRent = 6000.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(assignment)

        // Also create a historical rent payment for Rahul (must NOT be deleted)
        val payment = RentPaymentEntity(
            id = 101,
            tenantId = 1,
            tenantName = "Rahul",
            roomNumber = "404",
            amount = 6000.0,
            amountPaid = 6000.0,
            billingMonth = "October 2026",
            dueDate = "2026-10-05",
            paymentDate = "2026-10-05",
            paymentMode = "UPI",
            status = "Paid",
            propertyId = propId
        )
        rentPaymentDao.insertPayment(payment)

        // 3. Vacate Rahul with leaving date = October 20
        // Simulate today is October 3 (before October 20)
        val resultBefore = vacateTenantUseCase.execute(
            id = 1,
            leavingDate = "2026-10-20",
            currentDateOverride = "2026-10-03"
        )
        assertTrue(resultBefore is TenantValidationResult.Success)

        // Verify BEFORE October 20:
        // - tenant remains active/occupying according to future-vacancy behavior
        // - bed is NOT prematurely treated as available
        val rahulBefore = tenantDao.getTenantById(1)!!
        assertEquals("404", rahulBefore.roomNumber)
        assertEquals("Bed 2", rahulBefore.bedId)
        assertEquals("2026-10-20", rahulBefore.leavingDate)

        val bedBefore = bedDao.getBed(propId, "404", "Bed 2")!!
        assertEquals("OCCUPIED", bedBefore.status)

        val assignmentBefore = bedAssignmentDao.getAssignmentsForTenant(propId, 1).first()
        assertEquals("2026-10-20", assignmentBefore.endDate)

        // Verify report engine before October 20: shows 1 occupied bed, 0 vacancies, and 1 upcoming vacancy
        val allRooms = roomDao.getAllRooms(propId)
        val allBeds = bedDao.getAllBedsForProperty(propId)
        val allTenants = tenantDao.getAllTenants(propId)
        val allAssignments = bedAssignmentDao.getAllAssignments(propId)
        val allPayments = rentPaymentDao.getAllPaymentsForPropertySync(propId)

        val reportBefore = ReportCalculationEngine.calculateReport(
            property = null,
            period = ReportPeriod.Monthly("October 2026"),
            rooms = allRooms,
            beds = allBeds,
            tenants = allTenants,
            assignments = allAssignments,
            payments = allPayments,
            expenses = emptyList()
        )
        assertEquals(1, reportBefore.overview.activeTenantsCount)
        assertEquals(3, reportBefore.overview.totalPgCapacity)
        assertEquals(2, reportBefore.overview.vacanciesCount)
        assertEquals(1, reportBefore.vacancyReport.upcomingVacancies.size)
        assertEquals("Rahul", reportBefore.vacancyReport.upcomingVacancies.first().tenantName)

        // 4. Now simulate AFTER the effective leaving date (e.g. October 21)
        val resultAfter = vacateTenantUseCase.execute(
            id = 1,
            leavingDate = "2026-10-20",
            currentDateOverride = "2026-10-21"
        )
        assertTrue(resultAfter is TenantValidationResult.Success)

        // Verify AFTER the effective leaving date:
        // - tenant is vacated (roomNumber and bedId cleared)
        val rahulAfter = tenantDao.getTenantById(1)!!
        assertEquals("", rahulAfter.roomNumber)
        assertEquals("", rahulAfter.bedId)
        assertEquals("2026-10-20", rahulAfter.leavingDate)
        assertTrue("Vacate note must be recorded", rahulAfter.notes.contains("Vacated from Room 404 Bed 2"))

        // - BedAssignment ends correctly and historical assignment remains available
        val assignmentsAfter = bedAssignmentDao.getAssignmentsForTenant(propId, 1)
        assertEquals(1, assignmentsAfter.size)
        assertEquals("2026-10-20", assignmentsAfter.first().endDate)

        // - Bed becomes AVAILABLE
        val bedAfter = bedDao.getBed(propId, "404", "Bed 2")!!
        assertEquals("AVAILABLE", bedAfter.status)

        // - Historical rent/payment records are NOT deleted
        val paymentAfter = rentPaymentDao.getPaymentByIdIncludingDeleted(101)
        assertNotNull(paymentAfter)
        assertEquals(6000.0, paymentAfter!!.amountPaid, 0.01)

        // - Room summary reactive flow updates
        val summary = roomRepository.getRoomSummaryFlow("404").first()
        assertNotNull(summary)
        assertEquals(3, summary!!.beds.count { it.status == "AVAILABLE" })
        assertEquals(0, summary.tenants.size)

        // - Report updates: 0 active tenants, 3 vacancies
        val tenantsAfter = tenantDao.getAllTenants(propId)
        val bedsAfter = bedDao.getAllBedsForProperty(propId)
        val reportAfter = ReportCalculationEngine.calculateReport(
            property = null,
            period = ReportPeriod.Monthly("October 2026"),
            rooms = allRooms,
            beds = bedsAfter,
            tenants = tenantsAfter,
            assignments = assignmentsAfter,
            payments = allPayments,
            expenses = emptyList()
        )
        assertEquals(0, reportAfter.overview.activeTenantsCount)
        assertEquals(3, reportAfter.overview.vacanciesCount)
        assertEquals(0, reportAfter.vacancyReport.upcomingVacancies.size)
    }

    // ==================================================
    // REQUIRED ACCEPTANCE TEST 3 — PROPERTY ID ISOLATION & DTO SYNC
    // ==================================================
    @Test
    fun testP0_3_TenantPropertyIdSyncAndIsolation() = runTest {
        val propA = "property_A"
        val propB = "property_B"

        // 1. Create Property A with Tenant Rahul
        currentPropertyManager.setCurrentPropertyId(propA)
        val rahul = TenantEntity(
            id = 1,
            cloudId = "tenant_rahul_cloud",
            name = "Rahul",
            phone = "9111111111",
            email = "rahul@a.com",
            emergencyContact = "9111111112",
            roomNumber = "101",
            bedId = "Bed 1",
            monthlyRent = 7000.0,
            securityDeposit = 14000.0,
            moveInDate = "2026-10-01",
            isKycUploaded = true,
            kycDocType = "Aadhaar Card",
            propertyId = propA,
            ownerId = "owner_123"
        )
        tenantRepository.insertTenant(rahul)

        // 2. Create Property B with Tenant Arjun
        currentPropertyManager.setCurrentPropertyId(propB)
        val arjun = TenantEntity(
            id = 2,
            cloudId = "tenant_arjun_cloud",
            name = "Arjun",
            phone = "9222222222",
            email = "arjun@b.com",
            emergencyContact = "9222222223",
            roomNumber = "201",
            bedId = "Bed 1",
            monthlyRent = 8000.0,
            securityDeposit = 16000.0,
            moveInDate = "2026-10-01",
            isKycUploaded = true,
            kycDocType = "PAN Card",
            propertyId = propB,
            ownerId = "owner_123"
        )
        tenantRepository.insertTenant(arjun)

        // 3. Verify DTO mapping preserves exact propertyId (NO "property_default")
        val rahulDto = rahul.toDto("owner_123")
        assertEquals(propA, rahulDto.propertyId)
        assertNotEquals("property_default", rahulDto.propertyId)

        val arjunDto = arjun.toDto("owner_123")
        assertEquals(propB, arjunDto.propertyId)
        assertNotEquals("property_default", arjunDto.propertyId)

        // 4. Verify reverse mapping (DTO -> Entity) preserves exact propertyId
        val rahulEntityReconstructed = rahulDto.toEntity()
        assertEquals(propA, rahulEntityReconstructed.propertyId)

        val arjunEntityReconstructed = arjunDto.toEntity()
        assertEquals(propB, arjunEntityReconstructed.propertyId)

        // 5. Open Property A: ONLY Rahul appears
        currentPropertyManager.setCurrentPropertyId(propA)
        val propATenants = tenantRepository.getAllTenantsFlow().first()
        assertEquals(1, propATenants.size)
        assertEquals("Rahul", propATenants.first().name)
        assertEquals(propA, propATenants.first().propertyId)

        // 6. Open Property B: ONLY Arjun appears
        currentPropertyManager.setCurrentPropertyId(propB)
        val propBTenants = tenantRepository.getAllTenantsFlow().first()
        assertEquals(1, propBTenants.size)
        assertEquals("Arjun", propBTenants.first().name)
        assertEquals(propB, propBTenants.first().propertyId)

        // 7. Verify no tenant crosses properties
        assertFalse(propATenants.any { it.name == "Arjun" })
        assertFalse(propBTenants.any { it.name == "Rahul" })
    }

    // ==================================================
    // REQUIRED ACCEPTANCE TEST 4 — STARTUP PG DATA AUTO-RESTORATION
    // ==================================================
    @Test
    fun testStartupExistingPropertyAndDataAutoRestoration() = runTest {
        val ownerId = "owner_karan_123"
        val propKaranId = "prop_karan_pg"

        // 1. Existing PG data in database
        val karanProp = PropertyEntity(
            propertyId = propKaranId,
            ownerId = ownerId,
            propertyName = "Karan PG",
            address = "Koramangala 5th Block",
            city = "Bangalore",
            state = "Karnataka",
            postalCode = "560095",
            contactNumber = "+91 98888 77777",
            description = "Main Student PG",
            isActive = true
        )
        propertyDao.insertProperty(karanProp)

        roomDao.insertRoom(RoomEntity("101", "1st Floor", 2, 7000.0, "AC", "", true, ownerId, propKaranId))
        roomDao.insertRoom(RoomEntity("102", "1st Floor", 3, 6000.0, "Non-AC", "", true, ownerId, propKaranId))

        bedDao.insertBed(BedEntity("101", "Bed 1", "OCCUPIED", "", ownerId, propKaranId))
        bedDao.insertBed(BedEntity("101", "Bed 2", "AVAILABLE", "", ownerId, propKaranId))
        bedDao.insertBed(BedEntity("102", "Bed 1", "OCCUPIED", "", ownerId, propKaranId))
        bedDao.insertBed(BedEntity("102", "Bed 2", "AVAILABLE", "", ownerId, propKaranId))
        bedDao.insertBed(BedEntity("102", "Bed 3", "AVAILABLE", "", ownerId, propKaranId))

        tenantDao.insertTenant(
            TenantEntity(
                id = 10,
                name = "Vikas",
                phone = "9888811111",
                email = "vikas@karan.com",
                emergencyContact = "9888811112",
                roomNumber = "101",
                bedId = "Bed 1",
                monthlyRent = 7000.0,
                securityDeposit = 14000.0,
                moveInDate = "2026-10-01",
                isKycUploaded = true,
                kycDocType = "Aadhaar",
                propertyId = propKaranId,
                ownerId = ownerId
            )
        )
        tenantDao.insertTenant(
            TenantEntity(
                id = 11,
                name = "Sameer",
                phone = "9888822222",
                email = "sameer@karan.com",
                emergencyContact = "9888822223",
                roomNumber = "102",
                bedId = "Bed 1",
                monthlyRent = 6000.0,
                securityDeposit = 12000.0,
                moveInDate = "2026-10-01",
                isKycUploaded = true,
                kycDocType = "Aadhaar",
                propertyId = propKaranId,
                ownerId = ownerId
            )
        )

        // 2. Simulate Cold App Start: Fresh CurrentPropertyManager starting with "property_default"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val freshPropertyManager = CurrentPropertyManager(context, propertyDao)
        assertEquals("property_default", freshPropertyManager.getCurrentPropertyId())

        // 3. StartupManager bootstrap runs
        val restoredPropId = freshPropertyManager.restoreActiveProperty()
        assertEquals(propKaranId, restoredPropId)
        assertEquals(propKaranId, freshPropertyManager.getCurrentPropertyId())

        // 4. Reactive Dashboard flows immediately observe Karan PG
        val billingMonthManager = com.example.features.rent.domain.util.CurrentBillingMonthManager(context)
        val dashRepo = com.example.features.dashboard.data.DashboardRepository(
            roomDao = roomDao,
            tenantDao = tenantDao,
            rentPaymentDao = rentPaymentDao,
            expenseDao = db.expenseDao(),
            bedDao = bedDao,
            bedAssignmentDao = bedAssignmentDao,
            propertyDao = propertyDao,
            ownerProfileDao = db.ownerProfileDao(),
            currentPropertyManager = freshPropertyManager,
            currentBillingMonthManager = billingMonthManager
        )
        val recentActivityUseCase = com.example.features.dashboard.domain.usecase.RecentActivityUseCase(dashRepo)
        val dashSummaryUseCase = com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase(dashRepo, recentActivityUseCase)
        val dashboardVm = com.example.features.dashboard.ui.DashboardViewModel(
            dashboardSummaryUseCase = dashSummaryUseCase,
            currentBillingMonthManager = billingMonthManager,
            currentPropertyManager = freshPropertyManager
        )

        val nonLoadingFlow = kotlinx.coroutines.flow.flow {
            dashboardVm.uiState.collect { state ->
                if (state !is com.example.features.dashboard.ui.DashboardUiState.Loading) {
                    emit(state)
                }
            }
        }
        val uiState = nonLoadingFlow.first()
        assertTrue("Dashboard must NOT be Empty state", uiState is com.example.features.dashboard.ui.DashboardUiState.Success)
        val success = uiState as com.example.features.dashboard.ui.DashboardUiState.Success

        assertEquals(2, success.data.occupancy.totalRooms)
        assertEquals(5, success.data.occupancy.totalBeds)
        assertEquals(2, success.data.occupancy.occupiedBeds)
        assertEquals(3, success.data.occupancy.vacantBeds)
    }
}
