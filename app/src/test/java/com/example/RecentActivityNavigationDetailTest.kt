package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.database.*
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.RecentActivityUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecentActivityNavigationDetailTest {

    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var ownerProfileDao: OwnerProfileDao
    private lateinit var propertyDao: PropertyDao
    private lateinit var currentPropertyManager: CurrentPropertyManager
    private lateinit var currentBillingMonthManager: CurrentBillingMonthManager
    private lateinit var repository: DashboardRepository
    private lateinit var recentActivityUseCase: RecentActivityUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        roomDao = db.roomDao()
        tenantDao = db.tenantDao()
        rentPaymentDao = db.rentPaymentDao()
        expenseDao = db.expenseDao()
        ownerProfileDao = db.ownerProfileDao()
        propertyDao = db.propertyDao()
        currentPropertyManager = CurrentPropertyManager(context, propertyDao)
        currentBillingMonthManager = CurrentBillingMonthManager(context)
        currentBillingMonthManager.setCurrentBillingMonth("October 2026")

        repository = DashboardRepository(
            roomDao = roomDao,
            tenantDao = tenantDao,
            rentPaymentDao = rentPaymentDao,
            expenseDao = expenseDao,
            bedDao = db.bedDao(),
            bedAssignmentDao = db.bedAssignmentDao(),
            propertyDao = propertyDao,
            ownerProfileDao = ownerProfileDao,
            currentPropertyManager = currentPropertyManager,
            currentBillingMonthManager = currentBillingMonthManager
        )
        recentActivityUseCase = RecentActivityUseCase(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testRecentActivityPreservesRealRecordIdsAndPropertyIdsForKaranAndSR() = runTest {
        // Setup two distinct properties: Karan PG and SR PG
        val propKaranId = "prop_karan_pg"
        val propSrId = "prop_sr_pg"

        propertyDao.insertProperty(
            PropertyEntity(
                propertyId = propKaranId,
                propertyName = "Karan PG",
                address = "Hitech City",
                city = "Hyderabad",
                state = "Telangana",
                postalCode = "500081",
                contactNumber = "9988776655",
                ownerId = "owner_karan",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        propertyDao.insertProperty(
            PropertyEntity(
                propertyId = propSrId,
                propertyName = "SR PG",
                address = "Madhapur",
                city = "Hyderabad",
                state = "Telangana",
                postalCode = "500081",
                contactNumber = "9988776644",
                ownerId = "owner_sr",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Insert distinct expenses for Karan PG and SR PG
        val karanExpenseId = expenseDao.insertExpense(
            ExpenseEntity(
                id = 101,
                title = "Water Bill Karan",
                category = "Water",
                amount = 4500.0,
                date = "2026-10-10",
                notes = "Karan Water",
                propertyId = propKaranId
            )
        ).toInt()

        val srExpenseId = expenseDao.insertExpense(
            ExpenseEntity(
                id = 202,
                title = "Electricity SR",
                category = "Electricity",
                amount = 8200.0,
                date = "2026-10-11",
                notes = "SR Electricity",
                propertyId = propSrId
            )
        ).toInt()

        // Insert distinct rent payments for Karan PG and SR PG
        val karanPaymentId = rentPaymentDao.insertPayment(
            RentPaymentEntity(
                id = 301,
                tenantId = 1,
                tenantName = "Karan Tenant",
                roomNumber = "101",
                billingMonth = "October 2026",
                amount = 8000.0,
                amountPaid = 8000.0,
                dueDate = "2026-10-05",
                paymentDate = "2026-10-06",
                paymentMode = "UPI",
                status = "Paid",
                propertyId = propKaranId
            )
        ).toInt()

        val srPaymentId = rentPaymentDao.insertPayment(
            RentPaymentEntity(
                id = 402,
                tenantId = 2,
                tenantName = "SR Tenant",
                roomNumber = "202",
                billingMonth = "October 2026",
                amount = 9500.0,
                amountPaid = 9500.0,
                dueDate = "2026-10-05",
                paymentDate = "2026-10-07",
                paymentMode = "Cash",
                status = "Paid",
                propertyId = propSrId
            )
        ).toInt()

        // Switch to Karan PG and compute activities
        currentPropertyManager.setCurrentPropertyId(propKaranId)
        val karanActivities = recentActivityUseCase().first()

        val karanExpenseActivity = karanActivities.find { it.type == ActivityType.EXPENSE_ADDED }
        assertNotNull("Karan PG must have its expense activity", karanExpenseActivity)
        assertEquals(101, karanExpenseActivity!!.recordId)
        assertEquals(propKaranId, karanExpenseActivity.propertyId)

        val karanPaymentActivity = karanActivities.find { it.type == ActivityType.RENT_PAID }
        assertNotNull("Karan PG must have its payment activity", karanPaymentActivity)
        assertEquals(301, karanPaymentActivity!!.recordId)
        assertEquals(propKaranId, karanPaymentActivity.propertyId)

        // Verify that route generation uses real record ID
        val expectedExpenseRoute = Screen.ExpenseDetails.createRoute(karanExpenseActivity.recordId)
        assertEquals("expense_details/101", expectedExpenseRoute)

        val expectedPaymentRoute = Screen.Receipt.createRoute(karanPaymentActivity.recordId)
        assertEquals("receipt/301", expectedPaymentRoute)

        // Now switch to SR PG and verify distinct SR activities
        currentPropertyManager.setCurrentPropertyId(propSrId)
        val srActivities = recentActivityUseCase().first()

        val srExpenseActivity = srActivities.find { it.type == ActivityType.EXPENSE_ADDED }
        assertNotNull("SR PG must have its expense activity", srExpenseActivity)
        assertEquals(202, srExpenseActivity!!.recordId)
        assertEquals(propSrId, srExpenseActivity.propertyId)

        val srPaymentActivity = srActivities.find { it.type == ActivityType.RENT_PAID }
        assertNotNull("SR PG must have its payment activity", srPaymentActivity)
        assertEquals(402, srPaymentActivity!!.recordId)
        assertEquals(propSrId, srPaymentActivity.propertyId)

        // Verify SR routes cannot be confused with Karan routes
        assertNotEquals(karanExpenseActivity.recordId, srExpenseActivity.recordId)
        assertNotEquals(karanPaymentActivity.recordId, srPaymentActivity.recordId)
        assertEquals("expense_details/202", Screen.ExpenseDetails.createRoute(srExpenseActivity.recordId))
        assertEquals("receipt/402", Screen.Receipt.createRoute(srPaymentActivity.recordId))
    }
}
