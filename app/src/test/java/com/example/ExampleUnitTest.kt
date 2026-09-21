package com.example

import com.example.core.common.PgError
import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.features.startup.StartupManager
import com.example.features.startup.StartupState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enterprise Unit Test Suite verifying the correctness of our compiled project foundation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    // A lightweight mock logger for fast local JVM unit tests
    private val mockLogger = object : PgLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String, tr: Throwable?) {}
        override fun e(tag: String, msg: String, tr: Throwable?) {}
    }

    @Test
    fun test_pgResult_success_wrapping() {
        val expectedData = "Secure Workspace Payload"
        val result = PgResult.success(expectedData)

        assertTrue(result.isSuccess)
        assertEquals(expectedData, result.getOrNull())
    }

    @Test
    fun test_pgResult_failure_wrapping() {
        val errorMessage = "Database integrity check failed"
        val error = PgError.DatabaseError(errorMessage)
        val result = PgResult.failure(error)

        assertTrue(result.isFailure)
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun test_startupCoordinator_flowTransitions() = runTest {
        val startupManager = StartupManager(mockLogger, this)
        
        // Assert initial state is Idle
        assertEquals(StartupState.Idle, startupManager.state.value)

        // Start bootstrap
        startupManager.startBootstrap()

        // Wait for coroutine executions to finish
        advanceUntilIdle()

        // Assert system successfully transitions to Ready after completed simulation delays
        assertEquals(StartupState.Ready, startupManager.state.value)
    }
}
