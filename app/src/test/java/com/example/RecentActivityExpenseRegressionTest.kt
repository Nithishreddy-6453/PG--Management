package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.database.AppDatabase
import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.database.PropertyDao
import com.example.data.database.PropertyEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase
import com.example.features.dashboard.domain.usecase.RecentActivityUseCase
import com.example.features.dashboard.ui.DashboardUiState
import com.example.features.dashboard.ui.DashboardViewModel
import com.example.features.expenses.data.repository.ExpenseRepositoryImpl
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.usecase.AddExpenseUseCase
import com.example.features.expenses.domain.usecase.UpdateExpenseUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecentActivityExpenseRegressionTest {

    private lateinit var db: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var propertyDao: PropertyDao
    private lateinit var currentPropertyManager: CurrentPropertyManager
    private lateinit var currentBillingMonthManager: CurrentBillingMonthManager
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var recentActivityUseCase: RecentActivityUseCase
    private lateinit var dashboardSummaryUseCase: DashboardSummaryUseCase
    private lateinit var expenseRepository: ExpenseRepositoryImpl
    private lateinit var addExpenseUseCase: AddExpenseUseCase
    private lateinit var updateExpenseUseCase: UpdateExpenseUseCase

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        expenseDao = db.expenseDao()
        propertyDao = db.propertyDao()
        currentPropertyManager = CurrentPropertyManager(context, propertyDao)
        currentBillingMonthManager = CurrentBillingMonthManager(context)
        currentBillingMonthManager.setCurrentBillingMonth("October 2026")

        val prop = PropertyEntity(
            propertyId = "property_default",
            propertyName = "Emerald Stays",
            isActive = true
        )
        propertyDao.insertProperty(prop)
        currentPropertyManager.setCurrentPropertyId("property_default")

        // Insert at least 1 room and 1 tenant so Dashboard is not in Empty state
        db.roomDao().insertRoom(
            RoomEntity(
                roomNumber = "101",
                capacity = 1,
                propertyId = "property_default"
            )
        )
        db.tenantDao().insertTenant(
            TenantEntity(
                id = 1,
                name = "Existing Tenant",
                phone = "9876543210",
                email = "tenant@test.com",
                emergencyContact = "9876543211",
                roomNumber = "101",
                bedId = "Bed 1",
                monthlyRent = 8000.0,
                securityDeposit = 8000.0,
                moveInDate = "2026-06-01",
                isKycUploaded = true,
                kycDocType = "Aadhaar Card",
                propertyId = "property_default"
            )
        )

        dashboardRepository = DashboardRepository(
            roomDao = db.roomDao(),
            tenantDao = db.tenantDao(),
            rentPaymentDao = db.rentPaymentDao(),
            expenseDao = expenseDao,
            bedDao = db.bedDao(),
            bedAssignmentDao = db.bedAssignmentDao(),
            propertyDao = propertyDao,
            ownerProfileDao = db.ownerProfileDao(),
            currentPropertyManager = currentPropertyManager,
            currentBillingMonthManager = currentBillingMonthManager
        )

        recentActivityUseCase = RecentActivityUseCase(dashboardRepository)
        dashboardSummaryUseCase = DashboardSummaryUseCase(dashboardRepository, recentActivityUseCase)

        expenseRepository = ExpenseRepositoryImpl(
            expenseDao = expenseDao,
            currentPropertyManager = currentPropertyManager
        )
        addExpenseUseCase = AddExpenseUseCase(expenseRepository)
        updateExpenseUseCase = UpdateExpenseUseCase(expenseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * Regression test reproducing the exact observed issue:
     * Home screen's Recent Activity repeatedly displays 5 future recurring records:
     * - Water — ₹5,000 — 2027-01-03
     * - Staff Salary — ₹10,000 — 2027-01-03
     * - Water — ₹5,000 — 2026-12-03
     * - Staff Salary — ₹10,000 — 2026-12-03
     * - Water — ₹5,000 — 2026-11-03
     *
     * When saving a new expense today (2026-10-12):
     * 1. The DAO Flow emits the updated records.
     * 2. RecentActivityUseCase includes the new expense in the correct position.
     * 3. The dashboard state updates to include it in the five displayed entries.
     * 4. When the expense is updated/edited, it also updates reactively in the top 5.
     */
    @Test
    fun testNewExpenseAppearsInHomeScreenRecentActivity_EvenWithFutureExpensesPresent() = runTest {
        // 1. Insert the 5 existing future/recurring expenses observed in the bug report
        val existingExpenses = listOf(
            ExpenseEntity(
                id = 101,
                title = "Water",
                category = "Water",
                amount = 5000.0,
                date = "2027-01-03",
                notes = "Future recurring bill",
                propertyId = "property_default"
            ),
            ExpenseEntity(
                id = 102,
                title = "Staff Salary",
                category = "Staff Salary",
                amount = 10000.0,
                date = "2027-01-03",
                notes = "Future recurring salary",
                propertyId = "property_default"
            ),
            ExpenseEntity(
                id = 103,
                title = "Water",
                category = "Water",
                amount = 5000.0,
                date = "2026-12-03",
                notes = "Future recurring bill",
                propertyId = "property_default"
            ),
            ExpenseEntity(
                id = 104,
                title = "Staff Salary",
                category = "Staff Salary",
                amount = 10000.0,
                date = "2026-12-03",
                notes = "Future recurring salary",
                propertyId = "property_default"
            ),
            ExpenseEntity(
                id = 105,
                title = "Water",
                category = "Water",
                amount = 5000.0,
                date = "2026-11-03",
                notes = "Future recurring bill",
                propertyId = "property_default"
            )
        )
        for (exp in existingExpenses) {
            expenseDao.insertExpense(exp)
        }

        // 2. Observe dashboard Recent Activity before inserting the new expense
        val initialSummaryFlow = dashboardSummaryUseCase()
        val initialSummary = initialSummaryFlow.first()
        assertNotNull(initialSummary)

        // Future placeholder records must not crowd out or pollute Recent Activity
        val initialActivities = initialSummary.recentActivities
        val initialExpenseActivities = initialActivities.filter { it.type == ActivityType.EXPENSE_ADDED }
        assertEquals(0, initialExpenseActivities.size)

        // 3. Insert a new expense with a known ID, amount, category, and valid recent transaction date (today / Oct 12, 2026)
        val newExpenseDate = "2026-10-12"
        val newExpense = Expense(
            title = "Plumbing Repair",
            category = "Maintenance",
            amount = 1850.0,
            date = newExpenseDate,
            paymentMethod = "UPI",
            notes = "Replaced broken tap in Room 101"
        )
        addExpenseUseCase(newExpense)

        // 4. Verify that the DAO Flow emits the updated records
        val updatedDaoExpenses = expenseDao.getAllExpensesForPropertyFlow("property_default").first()
        val persistedNewExpense = updatedDaoExpenses.find { it.category == "Maintenance" && it.amount == 1850.0 }
        assertNotNull("The newly inserted expense must be persisted in Room", persistedNewExpense)
        assertEquals("2026-10-12", persistedNewExpense?.date)

        // 5. Verify that RecentActivityUseCase includes the new expense in the correct position
        val updatedActivities = recentActivityUseCase().first()
        val expenseActivity = updatedActivities.find { it.id == "expense_${persistedNewExpense?.id}" }
        assertNotNull("RecentActivityUseCase must include the new expense", expenseActivity)
        assertEquals(ActivityType.EXPENSE_ADDED, expenseActivity?.type)
        assertEquals("Maintenance", expenseActivity?.param1)
        assertEquals(1850.0, expenseActivity?.amount ?: 0.0, 0.001)

        // 6. Verify that Dashboard ViewModel state updates to include it in the five displayed entries
        val dashboardViewModel = DashboardViewModel(
            dashboardSummaryUseCase = dashboardSummaryUseCase,
            currentBillingMonthManager = currentBillingMonthManager,
            currentPropertyManager = currentPropertyManager
        )

        val uiState = dashboardViewModel.uiState.first { it is DashboardUiState.Success }
        assertTrue(uiState is DashboardUiState.Success)
        val successState = uiState as DashboardUiState.Success
        val topFiveActivities = successState.data.recentActivities.take(5)

        val inTopFive = topFiveActivities.any { it.id == "expense_${persistedNewExpense?.id}" }
        assertTrue("The newly created expense must be present in the 5 displayed entries on the Home screen", inTopFive)

        // 7. Verify Update/Edit Case: Edit the expense to a newer date and verify reactive update
        val updatedExpenseDomain = newExpense.copy(
            id = persistedNewExpense!!.id,
            amount = 2200.0,
            date = "2026-10-12",
            notes = "Replaced tap and shower head"
        )
        updateExpenseUseCase(updatedExpenseDomain)

        // Verify update in DAO Flow
        val postUpdateDaoExpenses = expenseDao.getAllExpensesForPropertyFlow("property_default").first()
        val postUpdateEntity = postUpdateDaoExpenses.find { it.id == persistedNewExpense.id }
        assertEquals(2200.0, postUpdateEntity?.amount ?: 0.0, 0.001)

        // Verify update in Dashboard state top 5
        val updatedUiState = dashboardViewModel.uiState.first { state ->
            state is DashboardUiState.Success &&
                state.data.recentActivities.take(5).any { it.id == "expense_${persistedNewExpense.id}" && it.amount == 2200.0 }
        }
        val updatedSuccessState = updatedUiState as DashboardUiState.Success
        val topFivePostUpdate = updatedSuccessState.data.recentActivities.take(5)
        val updatedActivityInTopFive = topFivePostUpdate.find { it.id == "expense_${persistedNewExpense.id}" }
        assertNotNull("Edited expense must remain in the 5 displayed entries", updatedActivityInTopFive)
        assertEquals(2200.0, updatedActivityInTopFive?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testNewExpenseAppearsAtTopEvenWhenRentPaymentSharesDate() = runTest {
        // Insert a rent payment on 2026-10-12 with ID 1
        db.rentPaymentDao().insertPayment(
            com.example.data.database.RentPaymentEntity(
                id = 1,
                tenantId = 1,
                tenantName = "John Doe",
                roomNumber = "101",
                billingMonth = "October 2026",
                amount = 8000.0,
                amountPaid = 8000.0,
                dueDate = "2026-10-05",
                paymentDate = "2026-10-12",
                paymentMode = "Cash",
                status = "Paid",
                propertyId = "property_default"
            )
        )

        // Insert an expense on the exact same date (2026-10-12) with ID 10
        expenseDao.insertExpense(
            ExpenseEntity(
                id = 10,
                title = "Supplies",
                category = "Supplies",
                amount = 500.0,
                date = "2026-10-12",
                notes = "Cleaning supplies",
                propertyId = "property_default"
            )
        )

        val activities = recentActivityUseCase().first()
        assertTrue("Activities should not be empty", activities.isNotEmpty())

        // Since expense ID 10 > payment ID 1 on the same date, expense 10 should be sorted before payment 1
        // (previously string comparison "payment_1" < "expense_10" due to 'p' vs 'e', but now numeric recordId descending sorts expense 10 first)
        val firstActivity = activities.first()
        assertEquals(ActivityType.EXPENSE_ADDED, firstActivity.type)
        assertEquals(10, firstActivity.recordId)
    }
}
