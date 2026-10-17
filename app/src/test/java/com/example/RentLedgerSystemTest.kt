package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.PgLoggerImpl
import com.example.core.device.DeviceIdentityManager
import com.example.data.database.AppDatabase
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.data.sync.RealtimeSyncManager
import com.example.data.sync.SyncCoordinator
import com.example.data.sync.SyncEngine
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.data.repository.RentRepositoryImpl
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RentLedgerSystemTest {

    // =========================================================================
    // SPECIFICATION 22: REQUIRED ACCEPTANCE TEST
    // =========================================================================
    @Test
    fun testRequiredAcceptanceScenario_Rahul() {
        // Tenant Rahul, Monthly rent ₹6,000, Join date: October 15, 2026
        val monthlyRent = 6000.0
        val joinDate = "2026-10-15"
        val billingMonth = "October 2026"

        // 1. Calculate October proration
        val octProration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = joinDate,
            leavingDateStr = null,
            billingMonthStr = billingMonth
        )

        assertEquals("October has 31 days", 31, octProration.daysInMonth)
        assertEquals("Applicable days from Oct 15 to Oct 31 inclusive is 17", 17, octProration.applicableDays)
        assertTrue("October is prorated", octProration.isProrated)

        val octExpectedRent = RentBillingEngine.calculateExpectedRent(
            monthlyRent = monthlyRent,
            applicableDays = octProration.applicableDays,
            daysInMonth = octProration.daysInMonth
        )

        // ₹6,000 * 17 / 31 = ₹3,290.32
        assertEquals(3290.32, octExpectedRent, 0.001)

        // Initial status
        val futureDueDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        var initialStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, 0.0, futureDueDate)
        assertEquals("Pending", initialStatus)

        // Rahul pays ₹3,000
        val paidFirst = 3000.0
        val remainingFirst = octExpectedRent - paidFirst
        assertEquals(290.32, remainingFirst, 0.001)

        val partialStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, paidFirst, futureDueDate)
        assertEquals("Partially Paid", partialStatus)

        // Rahul pays remaining ₹290.32
        val totalPaid = paidFirst + 290.32
        val remainingFinal = octExpectedRent - totalPaid
        assertEquals(0.0, remainingFinal, 0.001)

        val fullStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, totalPaid, futureDueDate)
        assertEquals("Paid", fullStatus)

        // 2. November must be full rent
        val novProration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = joinDate,
            leavingDateStr = null,
            billingMonthStr = "November 2026"
        )
        assertEquals("November has 30 days", 30, novProration.daysInMonth)
        assertEquals("Applicable days in November is 30", 30, novProration.applicableDays)
        assertFalse("November is NOT prorated", novProration.isProrated)

        val novExpectedRent = RentBillingEngine.calculateExpectedRent(
            monthlyRent = monthlyRent,
            applicableDays = novProration.applicableDays,
            daysInMonth = novProration.daysInMonth
        )
        assertEquals(6000.0, novExpectedRent, 0.001)
    }

    // =========================================================================
    // SPECIFICATION 21: EDGE CASE UNIT TESTS
    // =========================================================================

    @Test
    fun test01_tenantJoinsOnDay1_fullMonthRent() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-01",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(31, proration.applicableDays)
        assertFalse(proration.isProrated)

        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(6000.0, rent, 0.001)
    }

    @Test
    fun test02_tenantJoinsOnDay15_prorated() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-15",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(17, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3290.32, rent, 0.001)
    }

    @Test
    fun test03_tenantJoinsOnLastDay_1DayRent() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-31",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(1, proration.applicableDays)
        assertTrue(proration.isProrated)

        // 6000 * 1 / 31 = 193.548... -> 193.55
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(193.55, rent, 0.001)
    }

    @Test
    fun test04_february28Days_nonLeapYear() {
        val days = RentBillingEngine.getDaysInMonth(2026, 2)
        assertEquals(28, days)

        // Joins Feb 15 in 2026 -> 28 - 15 + 1 = 14 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-02-15",
            leavingDateStr = null,
            billingMonthStr = "February 2026"
        )
        assertEquals(28, proration.daysInMonth)
        assertEquals(14, proration.applicableDays)

        // 6000 * 14 / 28 = 3000.0
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3000.0, rent, 0.001)
    }

    @Test
    fun test05_february29Days_leapYear() {
        val days = RentBillingEngine.getDaysInMonth(2028, 2)
        assertEquals(29, days)

        // Joins Feb 15 in 2028 (leap year) -> 29 - 15 + 1 = 15 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2028-02-15",
            leavingDateStr = null,
            billingMonthStr = "February 2028"
        )
        assertEquals(29, proration.daysInMonth)
        assertEquals(15, proration.applicableDays)

        // 6000 * 15 / 29 = 3103.448... -> 3103.45
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3103.45, rent, 0.001)
    }

    @Test
    fun test06_30DayMonth_November() {
        val days = RentBillingEngine.getDaysInMonth(2026, 11)
        assertEquals(30, days)

        // Joins Nov 11 in 30-day month -> 30 - 11 + 1 = 20 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-11-11",
            leavingDateStr = null,
            billingMonthStr = "November 2026"
        )
        assertEquals(30, proration.daysInMonth)
        assertEquals(20, proration.applicableDays)

        // 6000 * 20 / 30 = 4000.0
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(4000.0, rent, 0.001)
    }

    @Test
    fun test07_tenantLeavesMidMonth() {
        // Leaving Oct 20 in 31-day month -> Oct 1 to Oct 20 = 20 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-06-01",
            leavingDateStr = "2026-10-20",
            billingMonthStr = "October 2026"
        )
        assertEquals(31, proration.daysInMonth)
        assertEquals(20, proration.applicableDays)
        assertTrue(proration.isProrated)

        // 6000 * 20 / 31 = 3870.967... -> 3870.97
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3870.97, rent, 0.001)
    }

    @Test
    fun test08_tenantJoinsAndLeavesSameMonth() {
        // Joins Oct 10, Leaves Oct 25 -> 25 - 10 + 1 = 16 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-10",
            leavingDateStr = "2026-10-25",
            billingMonthStr = "October 2026"
        )
        assertEquals(16, proration.applicableDays)

        // 6000 * 16 / 31 = 3096.77
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3096.77, rent, 0.001)
    }

    @Test
    fun test09_tenantVacatedBeforeMonth_zeroApplicableDays() {
        // Left in Sept 2026, generating Oct 2026
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-01-01",
            leavingDateStr = "2026-09-30",
            billingMonthStr = "October 2026"
        )
        assertEquals(0, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(0.0, rent, 0.001)
    }

    @Test
    fun test10_tenantJoinsInFutureMonth_zeroApplicableDays() {
        // Joins in Nov 2026, generating Oct 2026
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-11-01",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(0, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(0.0, rent, 0.001)
    }

    @Test
    fun test11_deterministicCloudIdGeneration() {
        val id1 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "October 2026")
        val id2 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "2026-10")
        val id3 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "Oct 2026")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
        assertEquals("payment_prop_1_10_2026_10", id1)
    }

    @Test
    fun test12_monthPreviewGeneration() {
        val tenants = listOf(
            TenantEntity(
                id = 1,
                name = "Tenant Full",
                phone = "9999999991",
                email = "",
                emergencyContact = "",
                roomNumber = "101",
                bedId = "Bed A",
                monthlyRent = 10000.0,
                securityDeposit = 10000.0,
                moveInDate = "2026-01-01",
                isKycUploaded = false,
                kycDocType = "None"
            ),
            TenantEntity(
                id = 2,
                name = "Rahul",
                phone = "9999999992",
                email = "",
                emergencyContact = "",
                roomNumber = "102",
                bedId = "Bed A",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-10-15",
                isKycUploaded = false,
                kycDocType = "None"
            ),
            TenantEntity(
                id = 3,
                name = "Leaving Tenant",
                phone = "9999999993",
                email = "",
                emergencyContact = "",
                roomNumber = "103",
                bedId = "Bed A",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-01-01",
                leavingDate = "2026-10-20",
                isKycUploaded = false,
                kycDocType = "None"
            )
        )

        val preview = RentBillingEngine.generateMonthPreview(
            propertyId = "prop_1",
            propertyName = "Emerald Stays",
            billingMonthStr = "October 2026",
            allTenants = tenants,
            existingMonthPayments = emptyList()
        )

        assertEquals("October 2026", preview.billingMonth)
        assertEquals(3, preview.totalApplicableTenants)
        assertEquals(1, preview.fullMonthTenants)
        assertEquals(2, preview.proratedTenants)
        assertEquals(1, preview.leavingTenants)

        // Expected: 10000 + 3290.32 + 3870.97 = 17161.29
        assertEquals(17161.29, preview.expectedTotalRent, 0.01)
        assertFalse(preview.isAlreadyGenerated)
    }

    @Test
    fun test13_monthlyRentStatsCalculation() {
        val payments = listOf(
            RentPaymentEntity(
                id = 1,
                tenantId = 1,
                tenantName = "Tenant 1",
                roomNumber = "101",
                billingMonth = "October 2026",
                amount = 10000.0,
                amountPaid = 10000.0,
                dueDate = "2026-10-05",
                paymentDate = "2026-10-04",
                paymentMode = "UPI",
                status = "Paid"
            ),
            RentPaymentEntity(
                id = 2,
                tenantId = 2,
                tenantName = "Rahul",
                roomNumber = "102",
                billingMonth = "October 2026",
                amount = 3290.32,
                amountPaid = 3000.0,
                dueDate = "2026-10-15",
                paymentDate = "2026-10-15",
                paymentMode = "UPI",
                status = "Partially Paid"
            ),
            RentPaymentEntity(
                id = 3,
                tenantId = 3,
                tenantName = "Tenant 3",
                roomNumber = "103",
                billingMonth = "October 2026",
                amount = 5000.0,
                amountPaid = 0.0,
                dueDate = "2026-10-05",
                paymentDate = null,
                paymentMode = null,
                status = "Pending"
            )
        )

        val stats = RentBillingEngine.computeMonthlyRentStats("October 2026", payments)

        assertEquals(18290.32, stats.totalExpectedRent, 0.01)
        assertEquals(13000.0, stats.totalPaid, 0.01)
        assertEquals(5290.32, stats.totalOutstanding, 0.01)
        assertEquals(3, stats.totalTenantsCount)
        assertEquals(1, stats.paidTenantsCount)
        assertEquals(1, stats.partiallyPaidTenantsCount)
        assertEquals(1, stats.pendingTenantsCount)
        // 13000 / 18290.32 = 71.076% -> 71.1%
        assertEquals(71.1, stats.collectionPercentage, 0.1)
    }

    @Test
    fun testPreviousAndNextBillingMonthCalculations() {
        assertEquals("September 2026", RentBillingEngine.getPreviousBillingMonth("October 2026"))
        assertEquals("November 2026", RentBillingEngine.getNextBillingMonth("October 2026"))
        assertEquals("December 2025", RentBillingEngine.getPreviousBillingMonth("January 2026"))
        assertEquals("January 2027", RentBillingEngine.getNextBillingMonth("December 2026"))
    }

    // =========================================================================
    // F1 — ATOMIC MONTH RENT GENERATION TESTS
    // =========================================================================

    @Test
    fun testAtomicMonthRentGeneration_Success() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        try {
            val propertyDao = db.propertyDao()
            val tenantDao = db.tenantDao()
            val rentPaymentDao = db.rentPaymentDao()
            val syncQueueDao = db.syncQueueDao()

            val propId = "prop_test_1"
            propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

            val currentPropManager = CurrentPropertyManager(context, propertyDao, null)
            currentPropManager.setCurrentPropertyId(propId)
            val currentBillingMonthManager = CurrentBillingMonthManager(context)
            currentBillingMonthManager.setCurrentBillingMonth("October 2026")

            val logger = PgLoggerImpl()
            val deviceIdentityManager = DeviceIdentityManager(context)
            val conflictResolver = com.example.data.sync.ConflictResolver(logger)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val firestorePropertyRepo = com.example.data.firestore.repository.FirestorePropertyRepository(firestore, auth)
            val firestoreRoomRepo = com.example.data.firestore.repository.FirestoreRoomRepository(firestore, auth)
            val firestoreTenantRepo = com.example.data.firestore.repository.FirestoreTenantRepository(firestore, auth)
            val firestorePaymentRepo = com.example.data.firestore.repository.FirestorePaymentRepository(firestore, auth)
            val firestoreExpenseRepo = com.example.data.firestore.repository.FirestoreExpenseRepository(firestore, auth)
            val firestoreSettingsRepo = com.example.data.firestore.repository.FirestoreSettingsRepository(firestore, auth)
            val firestoreUserRepo = com.example.data.firestore.repository.FirestoreUserRepository(firestore, auth)
            val firestoreTenantMediaRepo = com.example.data.firestore.repository.FirestoreTenantMediaRepository(firestore, auth)

            val syncEngine = SyncEngine(
                database = db,
                roomDao = db.roomDao(),
                tenantDao = tenantDao,
                rentPaymentDao = rentPaymentDao,
                expenseDao = db.expenseDao(),
                ownerProfileDao = db.ownerProfileDao(),
                propertyDao = propertyDao,
                syncQueueDao = syncQueueDao,
                conflictRecordDao = db.conflictRecordDao(),
                deviceIdentityManager = deviceIdentityManager,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreSettingsRepository = firestoreSettingsRepo,
                firestoreUserRepository = firestoreUserRepo,
                tenantMediaDao = db.tenantMediaDao(),
                driveFolderDao = db.driveFolderDao(),
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                currentPropertyManager = currentPropManager,
                auth = auth,
                conflictResolver = conflictResolver,
                logger = logger,
                context = context
            )

            val realtimeSyncManager = RealtimeSyncManager(
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                syncEngine = syncEngine,
                auth = auth,
                logger = logger,
                scope = this
            )

            val syncCoordinator = SyncCoordinator(
                context = context,
                syncQueueDao = syncQueueDao,
                syncEngine = syncEngine,
                realtimeSyncManager = realtimeSyncManager,
                deviceIdentityManager = deviceIdentityManager,
                logger = logger,
                currentPropertyManager = currentPropManager
            )

            val rentRepository = RentRepositoryImpl(
                rentPaymentDao = rentPaymentDao,
                tenantDao = tenantDao,
                propertyDao = propertyDao,
                currentPropertyManager = currentPropManager,
                currentBillingMonthManager = currentBillingMonthManager,
                appDatabase = db,
                syncCoordinator = syncCoordinator,
                excelDriveBackupManager = null
            )

            // Setup 5 eligible tenants (3 full month, 2 prorated)
            tenantDao.insertTenant(TenantEntity(id = 1, name = "Tenant 1", phone = "9111111111", email = "", emergencyContact = "", roomNumber = "101", bedId = "Bed A", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-01-01", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            tenantDao.insertTenant(TenantEntity(id = 2, name = "Tenant 2", phone = "9222222222", email = "", emergencyContact = "", roomNumber = "102", bedId = "Bed A", monthlyRent = 8000.0, securityDeposit = 8000.0, moveInDate = "2026-01-01", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            tenantDao.insertTenant(TenantEntity(id = 3, name = "Tenant 3", phone = "9333333333", email = "", emergencyContact = "", roomNumber = "103", bedId = "Bed A", monthlyRent = 10000.0, securityDeposit = 10000.0, moveInDate = "2026-01-01", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            tenantDao.insertTenant(TenantEntity(id = 4, name = "Tenant 4", phone = "9444444444", email = "", emergencyContact = "", roomNumber = "104", bedId = "Bed A", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-11-15", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            tenantDao.insertTenant(TenantEntity(id = 5, name = "Tenant 5", phone = "9555555555", email = "", emergencyContact = "", roomNumber = "105", bedId = "Bed A", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-01-01", leavingDate = "2026-11-10", isKycUploaded = false, kycDocType = "None", propertyId = propId))

            val preview = rentRepository.generateMonthRent("November 2026", "2026-11-05", backupPreviousMonth = false)

            assertEquals("November 2026", preview.billingMonth)
            assertEquals(5, preview.totalApplicableTenants)

            val generatedInvoices = rentPaymentDao.getPaymentsForPropertyAndMonth(propId, "November 2026")
            assertEquals(5, generatedInvoices.size)

            val syncOps = syncQueueDao.getAllOperationsFlow().first()
            assertEquals(5, syncOps.count { it.entityType == "PAYMENT" && it.operationType == "CREATE" })

            assertEquals("November 2026", currentBillingMonthManager.getCurrentBillingMonth())
        } finally {
            db.close()
        }
    }

    @Test
    fun testAtomicMonthRentGeneration_RollbackOnFailure() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        try {
            val propertyDao = db.propertyDao()
            val tenantDao = db.tenantDao()
            val rentPaymentDao = db.rentPaymentDao()
            val syncQueueDao = db.syncQueueDao()

            val propId = "prop_test_2"
            propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

            val currentPropManager = CurrentPropertyManager(context, propertyDao, null)
            currentPropManager.setCurrentPropertyId(propId)
            val currentBillingMonthManager = CurrentBillingMonthManager(context)
            currentBillingMonthManager.setCurrentBillingMonth("October 2026")

            val logger = PgLoggerImpl()
            val deviceIdentityManager = DeviceIdentityManager(context)
            val conflictResolver = com.example.data.sync.ConflictResolver(logger)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val firestorePropertyRepo = com.example.data.firestore.repository.FirestorePropertyRepository(firestore, auth)
            val firestoreRoomRepo = com.example.data.firestore.repository.FirestoreRoomRepository(firestore, auth)
            val firestoreTenantRepo = com.example.data.firestore.repository.FirestoreTenantRepository(firestore, auth)
            val firestorePaymentRepo = com.example.data.firestore.repository.FirestorePaymentRepository(firestore, auth)
            val firestoreExpenseRepo = com.example.data.firestore.repository.FirestoreExpenseRepository(firestore, auth)
            val firestoreSettingsRepo = com.example.data.firestore.repository.FirestoreSettingsRepository(firestore, auth)
            val firestoreUserRepo = com.example.data.firestore.repository.FirestoreUserRepository(firestore, auth)
            val firestoreTenantMediaRepo = com.example.data.firestore.repository.FirestoreTenantMediaRepository(firestore, auth)

            val syncEngine = SyncEngine(
                database = db,
                roomDao = db.roomDao(),
                tenantDao = tenantDao,
                rentPaymentDao = rentPaymentDao,
                expenseDao = db.expenseDao(),
                ownerProfileDao = db.ownerProfileDao(),
                propertyDao = propertyDao,
                syncQueueDao = syncQueueDao,
                conflictRecordDao = db.conflictRecordDao(),
                deviceIdentityManager = deviceIdentityManager,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreSettingsRepository = firestoreSettingsRepo,
                firestoreUserRepository = firestoreUserRepo,
                tenantMediaDao = db.tenantMediaDao(),
                driveFolderDao = db.driveFolderDao(),
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                currentPropertyManager = currentPropManager,
                auth = auth,
                conflictResolver = conflictResolver,
                logger = logger,
                context = context
            )

            val realtimeSyncManager = RealtimeSyncManager(
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                syncEngine = syncEngine,
                auth = auth,
                logger = logger,
                scope = this
            )

            val syncCoordinator = SyncCoordinator(
                context = context,
                syncQueueDao = syncQueueDao,
                syncEngine = syncEngine,
                realtimeSyncManager = realtimeSyncManager,
                deviceIdentityManager = deviceIdentityManager,
                logger = logger,
                currentPropertyManager = currentPropManager
            )

            val rentRepository = RentRepositoryImpl(
                rentPaymentDao = rentPaymentDao,
                tenantDao = tenantDao,
                propertyDao = propertyDao,
                currentPropertyManager = currentPropManager,
                currentBillingMonthManager = currentBillingMonthManager,
                appDatabase = db,
                syncCoordinator = syncCoordinator,
                excelDriveBackupManager = null
            )

            for (i in 1..5) {
                tenantDao.insertTenant(TenantEntity(id = i, name = "Tenant $i", phone = "900000000$i", email = "", emergencyContact = "", roomNumber = "10$i", bedId = "Bed A", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-01-01", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            }

            val initialQueueCount = syncQueueDao.getAllOperationsFlow().first().size

            // Inject SQLite trigger failure on payments table insert after 2 rows have succeeded
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_on_payments_insert AFTER INSERT ON payments WHEN (SELECT COUNT(*) FROM payments WHERE billingMonth = 'November 2026') >= 2 BEGIN SELECT RAISE(ABORT, 'Simulated DB Failure'); END;"
            )

            var exceptionThrown = false
            try {
                rentRepository.generateMonthRent("November 2026", "2026-11-05", backupPreviousMonth = false)
            } catch (e: Exception) {
                exceptionThrown = true
            } finally {
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_on_payments_insert")
            }

            assertTrue("Expected exception due to database trigger failure", exceptionThrown)

            val novPaymentsAfter = rentPaymentDao.getPaymentsForPropertyAndMonth(propId, "November 2026")
            assertEquals(0, novPaymentsAfter.size)

            val finalQueueCount = syncQueueDao.getAllOperationsFlow().first().size
            assertEquals(initialQueueCount, finalQueueCount)

            assertEquals("October 2026", currentBillingMonthManager.getCurrentBillingMonth())
        } finally {
            db.close()
        }
    }

    @Test
    fun testMonthRentGeneration_Idempotent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        try {
            val propertyDao = db.propertyDao()
            val tenantDao = db.tenantDao()
            val rentPaymentDao = db.rentPaymentDao()
            val syncQueueDao = db.syncQueueDao()

            val propId = "prop_test_3"
            propertyDao.insertProperty(PropertyEntity(propertyId = propId, propertyName = "Emerald Stays"))

            val currentPropManager = CurrentPropertyManager(context, propertyDao, null)
            currentPropManager.setCurrentPropertyId(propId)
            val currentBillingMonthManager = CurrentBillingMonthManager(context)
            currentBillingMonthManager.setCurrentBillingMonth("October 2026")

            val logger = PgLoggerImpl()
            val deviceIdentityManager = DeviceIdentityManager(context)
            val conflictResolver = com.example.data.sync.ConflictResolver(logger)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val firestorePropertyRepo = com.example.data.firestore.repository.FirestorePropertyRepository(firestore, auth)
            val firestoreRoomRepo = com.example.data.firestore.repository.FirestoreRoomRepository(firestore, auth)
            val firestoreTenantRepo = com.example.data.firestore.repository.FirestoreTenantRepository(firestore, auth)
            val firestorePaymentRepo = com.example.data.firestore.repository.FirestorePaymentRepository(firestore, auth)
            val firestoreExpenseRepo = com.example.data.firestore.repository.FirestoreExpenseRepository(firestore, auth)
            val firestoreSettingsRepo = com.example.data.firestore.repository.FirestoreSettingsRepository(firestore, auth)
            val firestoreUserRepo = com.example.data.firestore.repository.FirestoreUserRepository(firestore, auth)
            val firestoreTenantMediaRepo = com.example.data.firestore.repository.FirestoreTenantMediaRepository(firestore, auth)

            val syncEngine = SyncEngine(
                database = db,
                roomDao = db.roomDao(),
                tenantDao = tenantDao,
                rentPaymentDao = rentPaymentDao,
                expenseDao = db.expenseDao(),
                ownerProfileDao = db.ownerProfileDao(),
                propertyDao = propertyDao,
                syncQueueDao = syncQueueDao,
                conflictRecordDao = db.conflictRecordDao(),
                deviceIdentityManager = deviceIdentityManager,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreSettingsRepository = firestoreSettingsRepo,
                firestoreUserRepository = firestoreUserRepo,
                tenantMediaDao = db.tenantMediaDao(),
                driveFolderDao = db.driveFolderDao(),
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                currentPropertyManager = currentPropManager,
                auth = auth,
                conflictResolver = conflictResolver,
                logger = logger,
                context = context
            )

            val realtimeSyncManager = RealtimeSyncManager(
                firestorePropertyRepository = firestorePropertyRepo,
                firestoreRoomRepository = firestoreRoomRepo,
                firestoreTenantRepository = firestoreTenantRepo,
                firestorePaymentRepository = firestorePaymentRepo,
                firestoreExpenseRepository = firestoreExpenseRepo,
                firestoreTenantMediaRepository = firestoreTenantMediaRepo,
                syncEngine = syncEngine,
                auth = auth,
                logger = logger,
                scope = this
            )

            val syncCoordinator = SyncCoordinator(
                context = context,
                syncQueueDao = syncQueueDao,
                syncEngine = syncEngine,
                realtimeSyncManager = realtimeSyncManager,
                deviceIdentityManager = deviceIdentityManager,
                logger = logger,
                currentPropertyManager = currentPropManager
            )

            val rentRepository = RentRepositoryImpl(
                rentPaymentDao = rentPaymentDao,
                tenantDao = tenantDao,
                propertyDao = propertyDao,
                currentPropertyManager = currentPropManager,
                currentBillingMonthManager = currentBillingMonthManager,
                appDatabase = db,
                syncCoordinator = syncCoordinator,
                excelDriveBackupManager = null
            )

            for (i in 1..5) {
                tenantDao.insertTenant(TenantEntity(id = i, name = "Tenant $i", phone = "900000000$i", email = "", emergencyContact = "", roomNumber = "10$i", bedId = "Bed A", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-01-01", isKycUploaded = false, kycDocType = "None", propertyId = propId))
            }

            val preview1 = rentRepository.generateMonthRent("November 2026", "2026-11-05", backupPreviousMonth = false)
            val payments1 = rentPaymentDao.getPaymentsForPropertyAndMonth(propId, "November 2026")
            val syncOps1 = syncQueueDao.getAllOperationsFlow().first().size

            assertEquals(5, payments1.size)

            val preview2 = rentRepository.generateMonthRent("November 2026", "2026-11-05", backupPreviousMonth = false)
            val payments2 = rentPaymentDao.getPaymentsForPropertyAndMonth(propId, "November 2026")
            val syncOps2 = syncQueueDao.getAllOperationsFlow().first().size

            assertEquals(5, payments2.size)
            assertEquals(syncOps1, syncOps2)
            assertEquals(payments1.map { it.id }.sorted(), payments2.map { it.id }.sorted())
            assertEquals(preview1.expectedTotalRent, preview2.expectedTotalRent, 0.01)
        } finally {
            db.close()
        }
    }

    @Test
    fun testCollectRentPrefillAndFullPaymentValidation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        try {
            val tenantDao = db.tenantDao()
            val rentPaymentDao = db.rentPaymentDao()

            val tenantId = tenantDao.insertTenant(
                TenantEntity(
                    id = 1,
                    name = "Vikram Singh",
                    phone = "9876543210",
                    email = "",
                    emergencyContact = "",
                    roomNumber = "201",
                    bedId = "Bed A",
                    monthlyRent = 10000.0,
                    securityDeposit = 10000.0,
                    moveInDate = "2026-01-01",
                    isKycUploaded = false,
                    kycDocType = "None",
                    propertyId = "prop_1"
                )
            ).toInt()

            // 1. Initial obligation record: ₹10,000 expected, ₹0 paid -> Outstanding ₹10,000
            val initialRecord = RentPaymentEntity(
                tenantId = tenantId,
                tenantName = "Vikram Singh",
                roomNumber = "201",
                billingMonth = "October 2026",
                amount = 10000.0,
                amountPaid = 0.0,
                dueDate = "2026-10-05",
                status = "Pending",
                propertyId = "prop_1"
            )
            val paymentId = rentPaymentDao.insertPayment(initialRecord).toInt()

            // Scenario 1: ₹10,000 expected, ₹0 paid -> prefill ₹10,000, remaining ₹10,000
            val record1 = rentPaymentDao.getPaymentByIdIncludingDeleted(paymentId)!!
            val remaining1 = (record1.amount - record1.amountPaid).coerceAtLeast(0.0)
            assertEquals(10000.0, remaining1, 0.001)

            // Scenario 3 & 8: First partial payment of ₹3,000
            val paid1 = 3000.0
            val statusPartial = RentBillingEngine.calculatePaymentStatus(record1.amount, paid1, record1.dueDate)
            val updatedPartial = record1.copy(amountPaid = paid1, status = statusPartial)
            rentPaymentDao.updatePayment(updatedPartial)

            // Scenario 2: Owes ₹10,000, paid ₹3,000 -> remaining ₹7,000
            val record2 = rentPaymentDao.getPaymentByIdIncludingDeleted(paymentId)!!
            val remaining2 = (record2.amount - record2.amountPaid).coerceAtLeast(0.0)
            assertEquals(7000.0, remaining2, 0.001)
            assertEquals("Partially Paid", record2.status)

            // Scenario 4 & 5: Pay remaining ₹7,000 -> total paid ₹10,000, remaining ₹0
            val paid2 = 7000.0
            val totalPaid = record2.amountPaid + paid2
            val statusFull = RentBillingEngine.calculatePaymentStatus(record2.amount, totalPaid, record2.dueDate)
            val updatedFull = record2.copy(amountPaid = totalPaid, status = statusFull)
            rentPaymentDao.updatePayment(updatedFull)

            // Scenario 6: Fully paid -> outstanding ₹0, status "Paid"
            val recordFinal = rentPaymentDao.getPaymentByIdIncludingDeleted(paymentId)!!
            val remainingFinal = (recordFinal.amount - recordFinal.amountPaid).coerceAtLeast(0.0)
            assertEquals(0.0, remainingFinal, 0.001)
            assertEquals("Paid", recordFinal.status)

            // Scenario 7: Validate status calculation handles floating point precision
            val statusExact = RentBillingEngine.calculatePaymentStatus(10000.0, 10000.0, "2026-10-05")
            assertEquals("Paid", statusExact)

            // Scenario 9: Duplicate payment check
            val existingCheck = rentPaymentDao.getPaymentForTenantPropertyAndMonth(tenantId, "prop_1", "October 2026")
            assertEquals("Paid", existingCheck?.status)
            assertEquals(10000.0, existingCheck?.amountPaid ?: 0.0, 0.001)
        } finally {
            db.close()
        }
    }
}
