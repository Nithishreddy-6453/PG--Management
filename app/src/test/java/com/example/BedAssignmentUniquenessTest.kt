package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class BedAssignmentUniquenessTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var bedAssignmentDao: BedAssignmentDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        bedAssignmentDao = db.bedAssignmentDao()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testCannotHaveTwoActiveAssignmentsForSameBed() = runTest(testDispatcher) {
        val propId = "property_default"
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // 1. Insert first active assignment for Bed 1 in Room 101
        val assignment1 = BedAssignmentEntity(
            assignmentId = "assign_1",
            tenantId = 101,
            roomNumber = "101",
            bedId = "Bed 1",
            startDate = "2026-01-01",
            endDate = null,
            agreedRent = 5000.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(assignment1)

        val active1 = bedAssignmentDao.getAssignmentsForRoom("property_default", "101")
            .filter { it.bedId == "Bed 1" && (it.endDate == null || it.endDate!! > todayStr) && !it.deleted }
        assertEquals("Should have exactly 1 active assignment initially", 1, active1.size)
        assertEquals("assign_1", active1.first().assignmentId)

        // 2. Insert second active assignment for same Bed 1 in Room 101
        val assignment2 = BedAssignmentEntity(
            assignmentId = "assign_2",
            tenantId = 102,
            roomNumber = "101",
            bedId = "Bed 1",
            startDate = "2026-02-01",
            endDate = null,
            agreedRent = 5500.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(assignment2)

        val allRoomAssignments = bedAssignmentDao.getAssignmentsForRoom("property_default", "101")
            .filter { it.bedId == "Bed 1" && !it.deleted }
        
        val activeAfter = allRoomAssignments.filter { it.endDate == null || it.endDate!! > todayStr }
        assertEquals("Active count for Bed 1 must remain <= 1", 1, activeAfter.size)
        assertEquals("Newest assignment must be the active one", "assign_2", activeAfter.first().assignmentId)

        val oldAssignment = allRoomAssignments.find { it.assignmentId == "assign_1" }
        assertNotNull("Previous assignment record must be preserved", oldAssignment)
        assertNotNull("Previous assignment must be deactivated with an endDate", oldAssignment?.endDate)
        assertEquals("2026-02-01", oldAssignment?.endDate)
    }

    @Test
    fun testInactiveHistoricalAssignmentsRemainAllowed() = runTest(testDispatcher) {
        val propId = "property_default"
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Insert two historical inactive assignments
        val hist1 = BedAssignmentEntity(
            assignmentId = "hist_1",
            tenantId = 201,
            roomNumber = "102",
            bedId = "Bed A",
            startDate = "2025-01-01",
            endDate = "2025-06-01",
            agreedRent = 4000.0,
            propertyId = propId
        )
        val hist2 = BedAssignmentEntity(
            assignmentId = "hist_2",
            tenantId = 202,
            roomNumber = "102",
            bedId = "Bed A",
            startDate = "2025-06-02",
            endDate = "2025-12-01",
            agreedRent = 4200.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(hist1)
        bedAssignmentDao.insertAssignment(hist2)

        // Insert new active assignment
        val active = BedAssignmentEntity(
            assignmentId = "active_1",
            tenantId = 203,
            roomNumber = "102",
            bedId = "Bed A",
            startDate = todayStr,
            endDate = null,
            agreedRent = 4500.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(active)

        val all = bedAssignmentDao.getAssignmentsForRoom("property_default", "102")
            .filter { it.bedId == "Bed A" && !it.deleted }
        assertEquals("All 3 records (2 historical + 1 active) must exist in history", 3, all.size)

        val activeList = all.filter { it.endDate == null || it.endDate!! > todayStr }
        assertEquals("Only 1 record must be active", 1, activeList.size)
        assertEquals("active_1", activeList.first().assignmentId)
    }

    @Test
    fun testMovingTenantOutAndAssigningDifferentBed() = runTest(testDispatcher) {
        val propId = "property_default"
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Tenant 1 on Bed 1
        val t1Bed1 = BedAssignmentEntity(
            assignmentId = "t1_b1",
            tenantId = 301,
            roomNumber = "103",
            bedId = "Bed 1",
            startDate = "2026-01-01",
            endDate = null,
            agreedRent = 6000.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(t1Bed1)

        // Tenant 2 on Bed 2
        val t2Bed2 = BedAssignmentEntity(
            assignmentId = "t2_b2",
            tenantId = 302,
            roomNumber = "103",
            bedId = "Bed 2",
            startDate = "2026-01-01",
            endDate = null,
            agreedRent = 6000.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(t2Bed2)

        // Deactivate Tenant 1
        bedAssignmentDao.updateAssignment(t1Bed1.copy(endDate = todayStr))

        // New tenant 3 on Bed 1
        val t3Bed1 = BedAssignmentEntity(
            assignmentId = "t3_b1",
            tenantId = 303,
            roomNumber = "103",
            bedId = "Bed 1",
            startDate = todayStr,
            endDate = null,
            agreedRent = 6000.0,
            propertyId = propId
        )
        bedAssignmentDao.insertAssignment(t3Bed1)

        val activeBed1 = bedAssignmentDao.getAssignmentsForRoom("property_default", "103")
            .filter { it.bedId == "Bed 1" && (it.endDate == null || it.endDate!! > todayStr) && !it.deleted }
        assertEquals(1, activeBed1.size)
        assertEquals("t3_b1", activeBed1.first().assignmentId)

        val activeBed2 = bedAssignmentDao.getAssignmentsForRoom("property_default", "103")
            .filter { it.bedId == "Bed 2" && (it.endDate == null || it.endDate!! > todayStr) && !it.deleted }
        assertEquals(1, activeBed2.size)
        assertEquals("t2_b2", activeBed2.first().assignmentId)
    }
}
