package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.RoomDao
import com.example.data.database.TenantDao
import com.example.data.database.RentPaymentDao
import com.example.data.database.ExpenseDao
import com.example.data.database.OwnerProfileDao
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.ExpenseEntity
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.dashboard.domain.usecase.OccupancyUseCase
import com.example.features.dashboard.domain.usecase.RevenueUseCase
import com.example.features.dashboard.domain.usecase.ExpensesUseCase
import com.example.features.dashboard.domain.usecase.ProfitUseCase
import com.example.features.dashboard.domain.usecase.RecentActivityUseCase
import com.example.features.dashboard.domain.usecase.DashboardSummaryUseCase
import com.example.features.dashboard.ui.DashboardUiState
import com.example.features.dashboard.ui.DashboardViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardModuleTest {

    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var ownerProfileDao: OwnerProfileDao
    private lateinit var currentPropertyManager: com.example.features.properties.data.CurrentPropertyManager
    private lateinit var repository: DashboardRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun createDb() {
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
        currentPropertyManager = com.example.features.properties.data.CurrentPropertyManager(context, db.propertyDao())
        
        repository = DashboardRepository(roomDao, tenantDao, rentPaymentDao, expenseDao, ownerProfileDao, currentPropertyManager)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun closeDb() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testDashboardDataAggregationFlow() = runTest {
        // 1. Insert room entries
        val room1 = RoomEntity(roomNumber = "101", floor = "1st Floor", capacity = 2, ratePerBed = 6000.0)
        val room2 = RoomEntity(roomNumber = "102", floor = "1st Floor", capacity = 3, ratePerBed = 5000.0)
        roomDao.insertRoom(room1)
        roomDao.insertRoom(room2)

        // Verify rooms list flow
        val roomsList = repository.getRoomsFlow().first()
        assertEquals(2, roomsList.size)

        // 2. Insert tenants
        val tenant1 = TenantEntity(
            id = 1,
            name = "John Doe",
            phone = "9876543210",
            email = "john@example.com",
            emergencyContact = "1234567890",
            roomNumber = "101",
            bedId = "Bed A",
            monthlyRent = 6000.0,
            securityDeposit = 12000.0,
            moveInDate = "2026-07-01",
            isKycUploaded = true,
            kycDocType = "Aadhaar"
        )
        tenantDao.insertTenant(tenant1)

        val tenantsList = repository.getTenantsFlow().first()
        assertEquals(1, tenantsList.size)

        // 3. Insert rent payment records
        val payment1 = RentPaymentEntity(
            id = 1,
            tenantId = 1,
            tenantName = "John Doe",
            roomNumber = "101",
            billingMonth = "July 2026",
            amount = 6000.0,
            amountPaid = 6000.0,
            dueDate = "2026-07-05",
            paymentDate = "2026-07-03",
            paymentMode = "UPI",
            status = "Paid"
        )
        val payment2 = RentPaymentEntity(
            id = 2,
            tenantId = 1,
            tenantName = "John Doe",
            roomNumber = "101",
            billingMonth = "August 2026",
            amount = 6000.0,
            dueDate = "2026-08-05",
            paymentDate = null,
            paymentMode = null,
            status = "Pending"
        )
        rentPaymentDao.insertPayment(payment1)
        rentPaymentDao.insertPayment(payment2)

        val paymentsList = repository.getPaymentsFlow().first()
        assertEquals(2, paymentsList.size)

        // 4. Insert operational expense items
        val expense1 = ExpenseEntity(
            id = 1,
            amount = 2500.0,
            category = "Water Bill",
            notes = "July operating water charges",
            date = "2026-07-10"
        )
        expenseDao.insertExpense(expense1)

        val expensesList = repository.getExpensesFlow().first()
        assertEquals(1, expensesList.size)

        // 5. Test Occupancy Use Case logic
        val occupancyUseCase = OccupancyUseCase(repository)
        val occupancyStats = occupancyUseCase().first()
        assertEquals(2, occupancyStats.totalRooms)
        assertEquals(1, occupancyStats.occupiedRooms)
        assertEquals(1, occupancyStats.vacantRooms)
        assertEquals(50.0, occupancyStats.occupancyPercentage, 0.01)
        assertEquals(5, occupancyStats.totalBeds)
        assertEquals(1, occupancyStats.occupiedBeds)
        assertEquals(4, occupancyStats.vacantBeds)
        assertEquals(20.0, occupancyStats.bedOccupancyPercentage, 0.01)

        // 6. Test Revenue Use Case logic
        val revenueUseCase = RevenueUseCase(repository)
        val revenueStats = revenueUseCase().first()
        assertEquals(6000.0, revenueStats.monthlyRevenue, 0.01)
        assertEquals(6000.0, revenueStats.pendingRent, 0.01)

        // 7. Test Expenses Use Case logic
        val expensesUseCase = ExpensesUseCase(repository)
        val expensesStats = expensesUseCase().first()
        assertEquals(2500.0, expensesStats.totalExpenses, 0.01)

        // 8. Test Profit Use Case logic
        val profitUseCase = ProfitUseCase(repository)
        val profitStats = profitUseCase().first()
        assertEquals(3500.0, profitStats.netProfit, 0.01) // 6000 collected - 2500 expenses

        // 9. Test Recent Activity Use Case logic
        val recentActivityUseCase = RecentActivityUseCase(repository)
        val activities = recentActivityUseCase().first()
        assertEquals(3, activities.size) // 1 tenant + 1 paid payment + 1 expense
        assertEquals("expense_1", activities[0].id) // July 10 (newest date)
        assertEquals("payment_1", activities[1].id) // July 03
        assertEquals("tenant_1", activities[2].id) // July 01 (oldest date)

        // 10. Test Dashboard Summary Use Case logic
        val dashboardSummaryUseCase = DashboardSummaryUseCase(
            occupancyUseCase, revenueUseCase, expensesUseCase, profitUseCase, recentActivityUseCase
        )
        val summary = dashboardSummaryUseCase().first()
        assertEquals(3500.0, summary.profit.netProfit, 0.01)
        assertEquals(3, summary.recentActivities.size)
    }

    @Test
    fun testDashboardViewModelInitialLoadingState() = runTest {
        val occupancyUseCase = OccupancyUseCase(repository)
        val revenueUseCase = RevenueUseCase(repository)
        val expensesUseCase = ExpensesUseCase(repository)
        val profitUseCase = ProfitUseCase(repository)
        val recentActivityUseCase = RecentActivityUseCase(repository)
        val dashboardSummaryUseCase = DashboardSummaryUseCase(
            occupancyUseCase, revenueUseCase, expensesUseCase, profitUseCase, recentActivityUseCase
        )

        val viewModel = DashboardViewModel(dashboardSummaryUseCase)
        
        // Wait (suspends safely) until Room's background thread query emits and state transitions to Empty
        val state = viewModel.uiState.first { it is DashboardUiState.Empty }
        assertTrue(state is DashboardUiState.Empty)
    }
}
