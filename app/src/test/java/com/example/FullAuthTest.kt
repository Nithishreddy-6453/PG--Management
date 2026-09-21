package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.OwnerProfileEntity
import com.example.data.repository.PgRepository
import com.example.ui.viewmodel.PgViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FullAuthTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PgRepository
    private lateinit var viewModel: PgViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun createDb() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = PgRepository(db.roomDao(), db.tenantDao(), db.rentPaymentDao(), db.expenseDao(), db.ownerProfileDao())
        viewModel = PgViewModel(context, repo)
    }

    @After
    fun closeDb() {
        db.close()
        Dispatchers.resetMain()
    }

@Test
    fun testUnlockPlaintextMigration() = runBlocking {
        // Wait for IO callback prepopulation to finish
        Thread.sleep(100)
        // Insert plaintext profile
        db.ownerProfileDao().insertProfile(OwnerProfileEntity(1, "PG", "Owner", "Phone", "UPI", "5678"))
        
        viewModel.unlock("5678")
        var attempts = 0
        while (!viewModel.isUnlocked.value && attempts < 20) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }
        
        assertTrue("ViewModel should be unlocked", viewModel.isUnlocked.value)
        assertNull("Pin error should be null", viewModel.pinError.value)
        
        val updatedProfile = db.ownerProfileDao().getProfile()
        assertNotNull(updatedProfile)
        assertTrue("PIN should be migrated to BCrypt", updatedProfile!!.pinCode.startsWith("$2"))
    }
    
    @Test
    fun testUnlockBcrypt() = runBlocking {
        Thread.sleep(100)
        val hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, "4321".toCharArray())
        db.ownerProfileDao().insertProfile(OwnerProfileEntity(1, "PG", "Owner", "Phone", "UPI", hash))
        
        viewModel.unlock("4321")
        var attempts = 0
        while (!viewModel.isUnlocked.value && attempts < 20) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }
        
        assertTrue("ViewModel should be unlocked", viewModel.isUnlocked.value)
        assertNull("Pin error should be null", viewModel.pinError.value)
    }
}
