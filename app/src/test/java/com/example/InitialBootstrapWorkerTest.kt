package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.data.sync.InitialBootstrapWorker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InitialBootstrapWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testInitialBootstrapWorker_companionConstants() {
        assertEquals("PGManager_InitialBootstrapWorker", InitialBootstrapWorker.INITIAL_BOOTSTRAP_WORK_NAME)
        assertEquals("owner_id", InitialBootstrapWorker.KEY_OWNER_ID)
    }

    @Test
    fun testEnqueueInitialBootstrap_doesNotCrash() {
        InitialBootstrapWorker.enqueueInitialBootstrap(context, "test_owner_123")
        InitialBootstrapWorker.cancel(context)
    }
}
