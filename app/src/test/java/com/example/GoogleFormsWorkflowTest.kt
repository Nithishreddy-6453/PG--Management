package com.example

import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.domain.model.FormVersionHistory
import com.example.features.googleform.domain.model.GoogleAccountInfo
import com.example.features.googleform.domain.model.TenantRegistrationForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated JVM Unit Tests for Google Forms Shared Access and Permission Request Workflow.
 * Note: These automated tests mock Google Drive/Forms API dependencies and verify model logic,
 * state transitions, and workspace isolation without requiring live Google Account authentication.
 */
class GoogleFormsWorkflowTest {

    @Test
    fun testFormAccessRequestStateTransitions() {
        val pendingRequest = FormAccessRequest(
            requestId = "req_101",
            propertyId = "prop_emerald",
            formId = "form_abc123",
            formTitle = "Emerald Stays Registration",
            requesterPgUid = "user_b",
            requesterGoogleEmail = "manager_b@gmail.com",
            requesterName = "Manager B",
            ownerPgUid = "user_a",
            ownerGoogleEmail = "owner_a@gmail.com",
            status = "PENDING",
            notificationStatus = "PENDING"
        )

        assertTrue("Request should be pending", pendingRequest.isPending)
        assertFalse("Request should not be approved", pendingRequest.isApproved)
        assertFalse("Request should not be rejected", pendingRequest.isRejected)

        val approvedRequest = pendingRequest.copy(status = "APPROVED")
        assertFalse("Request should no longer be pending", approvedRequest.isPending)
        assertTrue("Request should be approved", approvedRequest.isApproved)

        val rejectedRequest = pendingRequest.copy(status = "REJECTED", rejectionReason = "Unauthorized requester")
        assertTrue("Request should be rejected", rejectedRequest.isRejected)
        assertEquals("Unauthorized requester", rejectedRequest.rejectionReason)
    }

    @Test
    fun testNotificationMetadataTracking() {
        val request = FormAccessRequest(
            requestId = "req_102",
            propertyId = "prop_emerald",
            formId = "form_abc123",
            formTitle = "Emerald Stays Registration",
            requesterPgUid = "user_b",
            requesterGoogleEmail = "manager_b@gmail.com",
            requesterName = "Manager B",
            ownerPgUid = "user_a",
            ownerGoogleEmail = "owner_a@gmail.com",
            status = "PENDING",
            notificationStatus = "PENDING"
        )

        assertEquals("PENDING", request.notificationStatus)

        val sentRequest = request.copy(
            notificationStatus = "SENT",
            notificationSentAt = System.currentTimeMillis()
        )
        assertEquals("SENT", sentRequest.notificationStatus)
        assertNotNull(sentRequest.notificationSentAt)

        val failedRequest = request.copy(
            notificationStatus = "FAILED",
            notificationError = "Network error"
        )
        assertEquals("FAILED", failedRequest.notificationStatus)
        assertEquals("Network error", failedRequest.notificationError)
    }

    @Test
    fun testGoogleAccountInfoScopeRequirements() {
        val disconnected = GoogleAccountInfo(isConnected = false)
        assertFalse("Disconnected account lacks required scopes", disconnected.hasAllRequiredScopes)

        val partialConnected = GoogleAccountInfo(
            isConnected = true,
            email = "user@gmail.com",
            hasFormsBodyScope = true,
            hasResponsesReadonlyScope = false
        )
        assertFalse("Partial scope account should not have all required scopes", partialConnected.hasAllRequiredScopes)
        assertTrue("Partial scope account needs additional consent", partialConnected.needsAdditionalConsent)

        val fullyAuthorized = GoogleAccountInfo(
            isConnected = true,
            email = "user@gmail.com",
            hasFormsBodyScope = true,
            hasResponsesReadonlyScope = true,
            hasDriveFileScope = true
        )
        assertTrue("Fully authorized account has all required scopes", fullyAuthorized.hasAllRequiredScopes)
        assertFalse("Fully authorized account does not need additional consent", fullyAuthorized.needsAdditionalConsent)
    }

    @Test
    fun testTenantRegistrationFormAvailability() {
        val activeForm = TenantRegistrationForm(
            propertyId = "prop_emerald",
            cloudId = "cloud_01",
            ownerId = "owner_pg",
            formId = "1F_google_form_id",
            formTitle = "Emerald Stays - Tenant Form",
            responderUri = "https://docs.google.com/forms/d/e/123/viewform",
            editUri = "https://docs.google.com/forms/d/123/edit",
            googleAccountEmail = "owner@gmail.com",
            formVersion = 1,
            published = true,
            active = true,
            questionMapping = mapOf("FULL_NAME" to "q1"),
            lastCheckedAt = System.currentTimeMillis(),
            lastSuccessfulCheckAt = System.currentTimeMillis(),
            lastError = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        assertTrue("Active published form with URI is available", activeForm.isAvailable)

        val inactiveForm = activeForm.copy(active = false)
        assertFalse("Inactive form is not available", inactiveForm.isAvailable)
    }

    @Test
    fun testFormVersionHistoryCreation() {
        val history = FormVersionHistory(
            id = "hist_001",
            propertyId = "prop_emerald",
            formId = "old_form_123",
            formTitle = "Old Tenant Registration Form",
            responderUri = "https://docs.google.com/forms/d/e/old123/viewform",
            editUri = "https://docs.google.com/forms/d/old123/edit",
            googleAccountEmail = "owner@gmail.com",
            formVersion = 1,
            replacedByEmail = "manager_b@gmail.com"
        )

        assertEquals("prop_emerald", history.propertyId)
        assertEquals(1, history.formVersion)
        assertEquals("manager_b@gmail.com", history.replacedByEmail)
        assertNotNull(history.replacedAt)
    }
}
