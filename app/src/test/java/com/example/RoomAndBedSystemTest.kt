package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.*
import com.example.features.rooms.data.repository.RoomRepositoryImpl
import com.example.features.rooms.domain.model.*
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.rooms.domain.usecase.UpdateRoomUseCase
import com.example.features.rooms.domain.usecase.ValidateRoomUseCase
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
class RoomAndBedSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var bedDao: BedDao
    private lateinit var bedAssignmentDao: BedAssignmentDao
    private lateinit var repository: RoomRepository

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

        val currentPropertyManager = com.example.features.properties.data.CurrentPropertyManager(context, db.propertyDao())
        repository = RoomRepositoryImpl(roomDao, tenantDao, rentPaymentDao, bedDao, bedAssignmentDao, currentPropertyManager)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testRequiredScenarioRule32AndBedOccupancy() = runTest(testDispatcher) {
        val propId = "property_default"
        // 1. Create Room 404 with capacity 3
        val room = RoomEntity(roomNumber = "404", floor = "4th", capacity = 3, ratePerBed = 6000.0, propertyId = propId)
        repository.insertRoom(room)
        bedDao.insertBed(BedEntity(roomNumber = "404", bedId = "Bed 1", propertyId = propId))
        bedDao.insertBed(BedEntity(roomNumber = "404", bedId = "Bed 2", propertyId = propId))
        bedDao.insertBed(BedEntity(roomNumber = "404", bedId = "Bed 3", propertyId = propId))

        // 2. Insert tenants Arjun and Bharath
        val arjunId = tenantDao.insertTenant(TenantEntity(name = "Arjun", phone = "111", email = "a@a.com", emergencyContact = "123", roomNumber = "404", bedId = "Bed 1", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()
        val bharathId = tenantDao.insertTenant(TenantEntity(name = "Bharath", phone = "222", email = "b@b.com", emergencyContact = "123", roomNumber = "404", bedId = "Bed 2", monthlyRent = 6000.0, securityDeposit = 6000.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()

        bedAssignmentDao.insertAssignment(BedAssignmentEntity(tenantId = arjunId, roomNumber = "404", bedId = "Bed 1", startDate = "2026-10-01", agreedRent = 6000.0, propertyId = propId))
        bedAssignmentDao.insertAssignment(BedAssignmentEntity(tenantId = bharathId, roomNumber = "404", bedId = "Bed 2", startDate = "2026-10-01", agreedRent = 6000.0, propertyId = propId))

        bedDao.updateBed(BedEntity(roomNumber = "404", bedId = "Bed 1", status = "OCCUPIED", propertyId = propId))
        bedDao.updateBed(BedEntity(roomNumber = "404", bedId = "Bed 2", status = "OCCUPIED", propertyId = propId))

        // 3. Verify Room Status = Partially Occupied, Occupied = 2, Available = 1
        val summary1 = repository.getRoomSummaryFlow("404").first()
        assertNotNull(summary1)
        assertEquals("Partially Occupied", summary1?.occupancyStatus)
        assertEquals(2, summary1?.occupiedBeds)
        assertEquals(1, summary1?.availableBeds)

        // 4. Assign Rahul to Bed 3
        val rahulId = tenantDao.insertTenant(TenantEntity(name = "Rahul", phone = "333", email = "r@r.com", emergencyContact = "123", roomNumber = "404", bedId = "Bed 3", monthlyRent = 5500.0, securityDeposit = 5500.0, moveInDate = "2026-10-01", isKycUploaded = false, kycDocType = "None", propertyId = propId)).toInt()
        bedAssignmentDao.insertAssignment(BedAssignmentEntity(tenantId = rahulId, roomNumber = "404", bedId = "Bed 3", startDate = "2026-10-01", agreedRent = 5500.0, propertyId = propId))
        bedDao.updateBed(BedEntity(roomNumber = "404", bedId = "Bed 3", status = "OCCUPIED", propertyId = propId))

        val summary2 = repository.getRoomSummaryFlow("404").first()
        assertEquals("Full", summary2?.occupancyStatus)
        assertEquals(3, summary2?.occupiedBeds)
        assertEquals(0, summary2?.availableBeds)

        // 5. Set Bharath leaving date = October 20
        val bharath = tenantDao.getTenantById(bharathId)!!
        tenantDao.updateTenant(bharath.copy(leavingDate = "2026-10-20"))

        val summary3 = repository.getRoomSummaryFlow("404").first()
        assertTrue(summary3?.hasUpcomingVacancy == true)

        // 6. Transfer Arjun from 404 Bed 1 to Room 405 Bed 2 on October 15
        val room405 = RoomEntity(roomNumber = "405", floor = "4th", capacity = 2, ratePerBed = 7000.0, propertyId = propId)
        repository.insertRoom(room405)
        bedDao.insertBed(BedEntity(roomNumber = "405", bedId = "Bed 1", propertyId = propId))
        bedDao.insertBed(BedEntity(roomNumber = "405", bedId = "Bed 2", propertyId = propId))

        // Close Arjun's old assignment on Oct 14
        val arjunAssignment = bedAssignmentDao.getActiveAssignmentForTenant(propId, arjunId)!!
        bedAssignmentDao.updateAssignment(arjunAssignment.copy(endDate = "2026-10-14"))
        bedDao.updateBed(BedEntity(roomNumber = "404", bedId = "Bed 1", status = "AVAILABLE", propertyId = propId))

        // Open Arjun's new assignment on Oct 15 in Room 405 Bed 2
        bedAssignmentDao.insertAssignment(BedAssignmentEntity(tenantId = arjunId, roomNumber = "405", bedId = "Bed 2", startDate = "2026-10-15", agreedRent = 7000.0, propertyId = propId))
        bedDao.updateBed(BedEntity(roomNumber = "405", bedId = "Bed 2", status = "OCCUPIED", propertyId = propId))
        val arjun = tenantDao.getTenantById(arjunId)!!
        tenantDao.updateTenant(arjun.copy(roomNumber = "405", bedId = "Bed 2", monthlyRent = 7000.0))

        // Verify history preserved
        val arjunHistory = bedAssignmentDao.getAssignmentsForTenant(propId, arjunId)
        assertEquals(2, arjunHistory.size)
        val oldAssignment = arjunHistory.find { it.roomNumber == "404" }
        val newAssignment = arjunHistory.find { it.roomNumber == "405" }
        assertNotNull(oldAssignment)
        assertEquals("2026-10-14", oldAssignment?.endDate)
        assertNotNull(newAssignment)
        assertNull(newAssignment?.endDate)
        assertEquals("2026-10-15", newAssignment?.startDate)
    }

    @Test
    fun testRoomCapacitySaveBlockInvariant() = runTest(testDispatcher) {
        val propId = "property_default"
        val validateUseCase = ValidateRoomUseCase(repository)
        val updateRoomUseCase = UpdateRoomUseCase(repository, validateUseCase)

        // 1. Create Room 101 with capacity 3
        val initialRoom = RoomEntity(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 3,
            ratePerBed = 6000.0,
            roomType = "Non-AC",
            propertyId = propId
        )
        repository.insertRoom(initialRoom)

        // 2. Add two active tenants to Room 101
        val t1Id = tenantDao.insertTenant(
            TenantEntity(
                name = "Tenant One",
                phone = "1111111111",
                email = "t1@test.com",
                emergencyContact = "999",
                roomNumber = "101",
                bedId = "Bed 1",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-10-01",
                isKycUploaded = false,
                kycDocType = "None",
                propertyId = propId
            )
        ).toInt()

        val t2Id = tenantDao.insertTenant(
            TenantEntity(
                name = "Tenant Two",
                phone = "2222222222",
                email = "t2@test.com",
                emergencyContact = "999",
                roomNumber = "101",
                bedId = "Bed 2",
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
                tenantId = t1Id,
                roomNumber = "101",
                bedId = "Bed 1",
                startDate = "2026-10-01",
                agreedRent = 6000.0,
                propertyId = propId
            )
        )
        bedAssignmentDao.insertAssignment(
            BedAssignmentEntity(
                tenantId = t2Id,
                roomNumber = "101",
                bedId = "Bed 2",
                startDate = "2026-10-01",
                agreedRent = 6000.0,
                propertyId = propId
            )
        )

        // Verify active tenants count in Room 101 is 2
        val activeTenants = repository.getTenantsInRoom("101")
        assertEquals(2, activeTenants.size)

        // 3. Attempting to reduce capacity to 1 (less than active occupancy 2) MUST BE REJECTED
        val rejectResult = updateRoomUseCase(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 1,
            ratePerBed = 6000.0,
            roomType = "Non-AC",
            notes = "Attempted shrink"
        )
        assertTrue(rejectResult is RoomValidationResult.Error)
        assertEquals(
            "Bed capacity cannot be reduced below current active tenants count (2).",
            (rejectResult as RoomValidationResult.Error).message
        )

        // 4. Verify existing room in database remains COMPLETELY UNCHANGED after rejection
        val roomAfterReject = repository.getRoom("101")
        assertNotNull(roomAfterReject)
        assertEquals(3, roomAfterReject?.capacity)
        assertEquals("1st Floor", roomAfterReject?.floor)

        // 5. Reducing capacity to 2 (at or above current active occupancy of 2) MUST SUCCEED
        val successResult = updateRoomUseCase(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 6000.0,
            roomType = "Non-AC",
            notes = "Valid shrink"
        )
        assertTrue(successResult is RoomValidationResult.Success)

        val roomAfterValidShrink = repository.getRoom("101")
        assertEquals(2, roomAfterValidShrink?.capacity)
        assertEquals("Valid shrink", roomAfterValidShrink?.notes)

        // 6. Historical inactive assignments do not count as active occupancy
        // Vacate Tenant Two
        val vacateResult = repository.vacateTenant(t2Id, "2026-10-05")
        assertTrue(vacateResult is RoomValidationResult.Success)

        // Active tenants count in Room 101 is now 1 (Tenant Two's historical assignment exists in bed_assignments table)
        val activeAfterVacate = repository.getTenantsInRoom("101")
        assertEquals(1, activeAfterVacate.size)

        val allRoomAssignments = bedAssignmentDao.getAssignmentsForRoom(propId, "101")
        assertTrue(allRoomAssignments.size >= 2) // Historical assignments exist

        // Now reducing capacity to 1 (matching remaining active tenant count 1) MUST SUCCEED
        val reduceTo1Result = updateRoomUseCase(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 1,
            ratePerBed = 6000.0,
            roomType = "Non-AC",
            notes = "Shrunk after vacate"
        )
        assertTrue(reduceTo1Result is RoomValidationResult.Success)
        assertEquals(1, repository.getRoom("101")?.capacity)

        // 7. Legitimate boundary: Attempting capacity 0 or negative is rejected by validation
        val zeroCapacityResult = updateRoomUseCase(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 0,
            ratePerBed = 6000.0,
            roomType = "Non-AC",
            notes = "Zero capacity"
        )
        assertTrue(zeroCapacityResult is RoomValidationResult.Error)
        // Room remains unchanged at capacity 1
        assertEquals(1, repository.getRoom("101")?.capacity)
    }
}
