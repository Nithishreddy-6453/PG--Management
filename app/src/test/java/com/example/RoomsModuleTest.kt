package com.example

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.*
import com.example.features.rooms.data.repository.RoomRepositoryImpl
import com.example.features.rooms.domain.model.*
import com.example.features.rooms.domain.repository.RoomRepository
import com.example.features.rooms.domain.usecase.*
import com.example.features.rooms.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class RoomsModuleTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var db: AppDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var tenantDao: TenantDao
    private lateinit var rentPaymentDao: RentPaymentDao
    private lateinit var repository: RoomRepository

    // Use cases
    private lateinit var getRoomsUseCase: GetRoomsUseCase
    private lateinit var getRoomDetailsUseCase: GetRoomDetailsUseCase
    private lateinit var validateRoomUseCase: ValidateRoomUseCase
    private lateinit var addRoomUseCase: AddRoomUseCase
    private lateinit var updateRoomUseCase: UpdateRoomUseCase
    private lateinit var deleteRoomUseCase: DeleteRoomUseCase
    private lateinit var searchRoomsUseCase: SearchRoomsUseCase
    private lateinit var filterRoomsUseCase: FilterRoomsUseCase
    private lateinit var occupancyCalculationUseCase: OccupancyCalculationUseCase

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

        repository = RoomRepositoryImpl(roomDao, tenantDao, rentPaymentDao)

        // Initialize Use Cases
        getRoomsUseCase = GetRoomsUseCase(repository)
        getRoomDetailsUseCase = GetRoomDetailsUseCase(repository)
        validateRoomUseCase = ValidateRoomUseCase(repository)
        addRoomUseCase = AddRoomUseCase(repository, validateRoomUseCase)
        updateRoomUseCase = UpdateRoomUseCase(repository, validateRoomUseCase)
        deleteRoomUseCase = DeleteRoomUseCase(repository)
        searchRoomsUseCase = SearchRoomsUseCase()
        filterRoomsUseCase = FilterRoomsUseCase()
        occupancyCalculationUseCase = OccupancyCalculationUseCase()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testAddRoomValidationAndInsertion() = runTest(testDispatcher) {
        // Adding a room with blank number should fail validation
        val blankResult = addRoomUseCase("", "1st Floor", 2, 6000.0, "Non-AC", "Notes")
        advanceUntilIdle()
        assertTrue(blankResult is RoomValidationResult.Error)
        assertEquals("Room Number is required.", (blankResult as RoomValidationResult.Error).message)

        // Adding a valid room should succeed
        val validResult = addRoomUseCase("101", "1st Floor", 2, 6000.0, "Non-AC", "Nice room")
        advanceUntilIdle()
        assertTrue(validResult is RoomValidationResult.Success)

        // Verify it exists in DB
        val room = repository.getRoom("101")
        assertNotNull(room)
        assertEquals("1st Floor", room?.floor)
        assertEquals(2, room?.capacity)
        assertEquals(6000.0, room?.ratePerBed ?: 0.0, 0.0)
        assertEquals("Non-AC", room?.roomType)
        assertEquals("Nice room", room?.notes)

        // Trying to add same room number again should result in duplicate error
        val duplicateResult = addRoomUseCase("101", "1st Floor", 3, 5000.0, "AC", "")
        advanceUntilIdle()
        assertTrue(duplicateResult is RoomValidationResult.Error)
        assertEquals("Room Number 101 already exists.", (duplicateResult as RoomValidationResult.Error).message)
    }

    @Test
    fun testUpdateRoomOccupancyConstraints() = runTest(testDispatcher) {
        // Insert Room 204
        repository.insertRoom(RoomEntity("204", "2nd Floor", 2, 7000.0, "AC", "Has balcony"))
        advanceUntilIdle()

        // Update floor or rent should succeed
        val updateResult = updateRoomUseCase("204", "2nd Floor", 2, 7500.0, "AC", "Updated notes")
        advanceUntilIdle()
        assertTrue(updateResult is RoomValidationResult.Success)

        val updatedRoom = repository.getRoom("204")
        assertNotNull(updatedRoom)
        assertEquals(7500.0, updatedRoom?.ratePerBed ?: 0.0, 0.0)
        assertEquals("Updated notes", updatedRoom?.notes)

        // Now, insert a tenant in Room 204
        tenantDao.insertTenant(
            TenantEntity(
                id = 0,
                name = "Rahul Sharma",
                phone = "9876543211",
                email = "rahul@gmail.com",
                emergencyContact = "9876543212",
                roomNumber = "204",
                bedId = "Bed A",
                monthlyRent = 7500.0,
                securityDeposit = 7500.0,
                moveInDate = "2026-07-01",
                isKycUploaded = true,
                kycDocType = "Aadhaar Card"
            )
        )
        advanceUntilIdle()

        // Attempting to scale down room capacity to 0 beds (less than active tenants: 1) should fail
        val scaleDownFail = updateRoomUseCase("204", "2nd Floor", 0, 7500.0, "AC", "")
        advanceUntilIdle()
        assertTrue(scaleDownFail is RoomValidationResult.Error)
        assertEquals("Bed capacity cannot be reduced below current active tenants count (1).", (scaleDownFail as RoomValidationResult.Error).message)

        // Scaling down capacity to 1 bed should succeed
        val scaleDownSuccess = updateRoomUseCase("204", "2nd Floor", 1, 7500.0, "AC", "")
        advanceUntilIdle()
        assertTrue(scaleDownSuccess is RoomValidationResult.Success)
    }

    @Test
    fun testDeleteRoomActiveTenantBlock() = runTest(testDispatcher) {
        // Insert Room 301
        repository.insertRoom(RoomEntity("301", "3rd Floor", 1, 9000.0))
        advanceUntilIdle()

        // Deleting empty room should succeed
        val deleteSuccess = deleteRoomUseCase("301")
        advanceUntilIdle()
        assertTrue(deleteSuccess is RoomValidationResult.Success)
        assertNull(repository.getRoom("301"))

        // Re-insert room and assign tenant
        repository.insertRoom(RoomEntity("301", "3rd Floor", 1, 9000.0))
        tenantDao.insertTenant(
            TenantEntity(
                id = 0,
                name = "Sneha Patil",
                phone = "9900112233",
                email = "sneha@gmail.com",
                emergencyContact = "9900112234",
                roomNumber = "301",
                bedId = "Bed A",
                monthlyRent = 9000.0,
                securityDeposit = 9000.0,
                moveInDate = "2026-07-10",
                isKycUploaded = false,
                kycDocType = "None"
            )
        )
        advanceUntilIdle()

        // Deleting should fail now because of active tenant
        val deleteFail = deleteRoomUseCase("301")
        advanceUntilIdle()
        assertTrue(deleteFail is RoomValidationResult.Error)
        assertEquals("Cannot delete Room 301 because it has 1 active tenants.", (deleteFail as RoomValidationResult.Error).message)
    }

    @Test
    fun testSearchAndFilterRooms() = runTest(testDispatcher) {
        val summary1 = RoomSummary(RoomEntity("101", "1st Floor", 2, 6000.0, "Non-AC", "Close to elevator"), emptyList())
        val summary2 = RoomSummary(RoomEntity("102", "1st Floor", 1, 8000.0, "AC", "Quiet face"), emptyList())
        val summary3 = RoomSummary(RoomEntity("201", "2nd Floor", 3, 5000.0, "Non-AC", "Balcony view"), emptyList())

        val rooms = listOf(summary1, summary2, summary3)

        // Search test
        val searchResult = searchRoomsUseCase(rooms, "Quiet")
        assertEquals(1, searchResult.size)
        assertEquals("102", searchResult[0].roomNumber)

        val searchResultFloor = searchRoomsUseCase(rooms, "2nd")
        assertEquals(1, searchResultFloor.size)
        assertEquals("201", searchResultFloor[0].roomNumber)

        // Filter test - Floor
        val floorFiltered = filterRoomsUseCase(rooms, "1st Floor", "All", "All")
        assertEquals(2, floorFiltered.size)

        // Filter test - Type
        val typeFiltered = filterRoomsUseCase(rooms, "All", "AC", "All")
        assertEquals(1, typeFiltered.size)
        assertEquals("102", typeFiltered[0].roomNumber)
    }

    @Test
    fun testRoomListViewModelState() = runTest(testDispatcher) {
        val vm = RoomListViewModel(
            getRoomsUseCase,
            searchRoomsUseCase,
            filterRoomsUseCase,
            deleteRoomUseCase,
            occupancyCalculationUseCase
        )
        
        // Active collection to start the cold StateFlow
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Wait for the state flow to emit Empty after Loading
        advanceUntilIdle()
        var state = vm.uiState.value
        assertTrue(state is RoomListUiState.Empty)

        // Insert room
        repository.insertRoom(RoomEntity("101", "1st Floor", 2, 6000.0))
        advanceUntilIdle()

        // Get updated state
        state = vm.uiState.value
        assertTrue(state is RoomListUiState.Success)
        val successState = state as RoomListUiState.Success
        assertEquals(1, successState.rooms.size)
        assertEquals("101", successState.rooms[0].roomNumber)
        
        job.cancel()
    }

    @Test
    fun testAddRoomViewModelValidationAndSave() = runTest(testDispatcher) {
        val vm = AddRoomViewModel(addRoomUseCase)
        advanceUntilIdle()

        // Initial empty state
        var state = vm.uiState.value
        assertEquals("", state.roomNumber)
        assertFalse(state.isSaving)

        // Trigger save with blank inputs should cause validation error
        vm.saveRoom()
        advanceUntilIdle()
        state = vm.uiState.value
        assertFalse(state.isSaving)
        assertNotNull(state.error)

        // Enter valid input
        vm.onRoomNumberChanged("501")
        vm.onFloorChanged("5th Floor")
        vm.onCapacityChanged("2")
        vm.onRatePerBedChanged("10000")
        vm.onRoomTypeChanged("AC")
        vm.onNotesChanged("Super Deluxe room")
        advanceUntilIdle()

        // Trigger save again
        vm.saveRoom()
        advanceUntilIdle()
        
        // Form should reset upon success and DB should contain the room
        state = vm.uiState.value
        if (state.error != null) {
            println("ERROR WAS: ${state.error}")
        }
        assertEquals("", state.roomNumber) // form reset
        assertNull(state.error)

        val room = repository.getRoom("501")
        assertNotNull(room)
        assertEquals("5th Floor", room?.floor)
        assertEquals(2, room?.capacity)
        assertEquals("AC", room?.roomType)
        assertEquals("Super Deluxe room", room?.notes)
    }

    @Test
    fun testEditRoomViewModelLoadsAndSaves() = runTest(testDispatcher) {
        // Pre-insert a room to edit
        repository.insertRoom(RoomEntity("401", "4th Floor", 2, 7000.0, "Non-AC", "Original notes"))
        advanceUntilIdle()

        val savedStateHandle = SavedStateHandle(mapOf("roomNumber" to "401"))
        val vm = EditRoomViewModel(repository, updateRoomUseCase, savedStateHandle)
        advanceUntilIdle()

        // Verify original values loaded
        var state = vm.uiState.value
        assertEquals("401", state.roomNumber)
        assertEquals("4th Floor", state.floor)
        assertEquals("Original notes", state.notes)

        // Modify values
        vm.onFloorChanged("4th Floor West")
        vm.onNotesChanged("Modified notes")
        advanceUntilIdle()

        // Save
        vm.saveRoom()
        advanceUntilIdle()

        if (vm.uiState.value.error != null) {
            println("EDIT ERROR WAS: ${vm.uiState.value.error}")
        }

        // Verify updated values in database
        val room = repository.getRoom("401")
        assertEquals("4th Floor West", room?.floor)
        assertEquals("Modified notes", room?.notes)
    }
}
