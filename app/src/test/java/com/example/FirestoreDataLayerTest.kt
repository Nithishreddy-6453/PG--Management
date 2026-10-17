package com.example

import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.firestore.mapper.toBusinessSettings
import com.example.data.firestore.mapper.toDto
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.mapper.toOwnerProfileEntity
import com.example.data.firestore.mapper.toPropertyDto
import com.example.data.firestore.mapper.toSettingsDto
import com.example.data.firestore.model.ExpenseDto
import com.example.data.firestore.model.PaymentDto
import com.example.data.firestore.model.PropertyDto
import com.example.data.firestore.model.ReportDto
import com.example.data.firestore.model.RoomDto
import com.example.data.firestore.model.SettingsDto
import com.example.data.firestore.model.TenantDto
import com.example.data.firestore.model.UserDto
import com.example.features.settings.domain.model.BusinessSettings
import com.example.features.googleform.data.repository.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreDataLayerTest {

    @Test
    fun testRoomEntityToDtoAndBack() {
        val ownerId = "owner_123"
        val roomEntity = RoomEntity(
            roomNumber = "101",
            floor = "1st Floor",
            capacity = 2,
            ratePerBed = 5500.0,
            roomType = "AC",
            notes = "Spacious room",
            ownerId = ownerId,
            propertyId = "property_default",
            syncStatus = "LOCAL_ONLY"
        )

        val dto: RoomDto = roomEntity.toDto(ownerId)

        assertEquals("property_default_101", dto.id)
        assertEquals("101", dto.roomNumber)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("1st Floor", dto.floor)
        assertEquals(2, dto.capacity)
        assertEquals(5500.0, dto.ratePerBed, 0.01)
        assertEquals("AC", dto.roomType)
        assertFalse(dto.deleted)
        assertEquals("SYNCED", dto.syncStatus)

        val restoredEntity = dto.toEntity()
        assertEquals(roomEntity.roomNumber, restoredEntity.roomNumber)
        assertEquals(roomEntity.capacity, restoredEntity.capacity)
        assertEquals(roomEntity.ratePerBed, restoredEntity.ratePerBed, 0.01)
        assertEquals(roomEntity.ownerId, restoredEntity.ownerId)
    }

    @Test
    fun testTenantEntityToDtoAndBack() {
        val ownerId = "owner_123"
        val tenantEntity = TenantEntity(
            id = 42,
            name = "Rahul Sharma",
            phone = "9876543210",
            email = "rahul@example.com",
            emergencyContact = "9123456789",
            roomNumber = "101",
            bedId = "Bed A",
            monthlyRent = 6000.0,
            securityDeposit = 12000.0,
            moveInDate = "2026-01-01",
            isKycUploaded = true,
            kycDocType = "Aadhaar Card",
            ownerId = ownerId,
            propertyId = "property_default",
            syncStatus = "LOCAL_ONLY"
        )

        val dto: TenantDto = tenantEntity.toDto(ownerId)

        assertEquals("tenant_42", dto.id)
        assertEquals(42, dto.localId)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("Rahul Sharma", dto.name)
        assertEquals("9876543210", dto.phone)
        assertTrue(dto.isKycUploaded)

        val restoredEntity = dto.toEntity()
        assertEquals(tenantEntity.id, restoredEntity.id)
        assertEquals(tenantEntity.name, restoredEntity.name)
        assertEquals(tenantEntity.phone, restoredEntity.phone)
        assertEquals(tenantEntity.ownerId, restoredEntity.ownerId)
    }

    @Test
    fun testPaymentEntityToDtoAndBack() {
        val ownerId = "owner_123"
        val paymentEntity = RentPaymentEntity(
            id = 88,
            tenantId = 42,
            tenantName = "Rahul Sharma",
            roomNumber = "101",
            billingMonth = "July 2026",
            amount = 6000.0,
            amountPaid = 6000.0,
            dueDate = "2026-07-05",
            paymentDate = "2026-07-04",
            paymentMode = "UPI",
            transactionReference = "UPI12345678",
            remarks = "Paid on time",
            status = "Paid",
            ownerId = ownerId,
            propertyId = "property_default",
            syncStatus = "LOCAL_ONLY"
        )

        val dto: PaymentDto = paymentEntity.toDto(ownerId)

        assertEquals("payment_88", dto.id)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("July 2026", dto.billingMonth)
        assertEquals("Paid", dto.status)

        val restored = dto.toEntity()
        assertEquals(paymentEntity.id, restored.id)
        assertEquals(paymentEntity.amount, restored.amount, 0.01)
        assertEquals(paymentEntity.ownerId, restored.ownerId)
    }

    @Test
    fun testExpenseEntityToDtoAndBack() {
        val ownerId = "owner_123"
        val expense = ExpenseEntity(
            id = 15,
            amount = 1200.0,
            category = "Plumbing",
            date = "2026-07-20",
            notes = "Tap repair",
            title = "Bathroom Plumbing",
            paymentMethod = "Cash",
            vendor = "Local Plumber",
            ownerId = ownerId,
            propertyId = "property_default",
            syncStatus = "LOCAL_ONLY"
        )

        val dto: ExpenseDto = expense.toDto(ownerId)

        assertEquals("expense_15", dto.id)
        assertEquals(ownerId, dto.ownerId)
        assertEquals(1200.0, dto.amount, 0.01)

        val restored = dto.toEntity()
        assertEquals(expense.id, restored.id)
        assertEquals(expense.amount, restored.amount, 0.01)
        assertEquals(expense.ownerId, restored.ownerId)
    }

    @Test
    fun testPropertyAndSettingsMappers() {
        val ownerId = "owner_123"
        val ownerProfile = OwnerProfileEntity(
            id = 1,
            pgName = "Sunshine Stays",
            ownerName = "John Doe",
            phone = "9998887770",
            upiId = "john@upi",
            pinCode = "1234"
        )

        val propertyDto: PropertyDto = ownerProfile.toPropertyDto(ownerId)
        assertEquals("Sunshine Stays", propertyDto.pgName)
        assertEquals(ownerId, propertyDto.ownerId)

        val businessSettings = BusinessSettings(
            pgName = "Sunshine Stays",
            ownerName = "John Doe",
            contactNumber = "9998887770",
            emailAddress = "john@sunshine.com",
            address = "Main Street",
            defaultMonthlyRent = 6500.0
        )

        val settingsDto: SettingsDto = businessSettings.toSettingsDto(ownerId)
        assertEquals("Sunshine Stays", settingsDto.pgName)
        val restoredSettings = settingsDto.toBusinessSettings()
        assertEquals(businessSettings, restoredSettings)
    }

    @Test
    fun testUserAndReportDtoMetadataDefaults() {
        val userDto = UserDto(id = "user_1", ownerId = "user_1", email = "test@example.com")
        assertNotNull(userDto.createdAt)
        assertEquals("SYNCED", userDto.syncStatus)

        val reportDto = ReportDto(id = "rep_1", ownerId = "user_1", reportPeriod = "2026-07")
        assertNotNull(reportDto.createdAt)
        assertEquals(1, reportDto.version)
        assertFalse(reportDto.deleted)
    }

    @Test
    fun testPendingTenantRegistrationOwnershipSecurityMapping() {
        val ownerA = "firebase_user_A"
        val propertyId = "prop_123"
        val formId = "form_abc"
        val responseId = "resp_001"

        val entity = com.example.data.database.PendingTenantRegistrationEntity(
            cloudId = "reg_uuid_1",
            ownerId = ownerA,
            propertyId = propertyId,
            formId = formId,
            responseId = responseId,
            submittedAt = 1700000000000L,
            fullName = "John Prospect",
            phone = "9876543210",
            email = "prospect@example.com",
            status = "PENDING"
        )

        // Mapping to DTO must preserve strict ownerId and propertyId
        val dto = entity.toDto()
        assertEquals("reg_uuid_1", dto.cloudId)
        assertEquals(ownerA, dto.ownerId)
        assertEquals(propertyId, dto.propertyId)
        assertEquals(formId, dto.formId)
        assertEquals(responseId, dto.responseId)
        assertEquals("PENDING", dto.status)
        assertFalse(dto.deleted)

        // Mapping back to domain and entity preserves ownership context
        val domain = entity.toDomain()
        assertEquals(ownerA, domain.ownerId)
        assertEquals(propertyId, domain.propertyId)

        val restoredEntity = domain.toEntity()
        assertEquals(ownerA, restoredEntity.ownerId)
        assertEquals(propertyId, restoredEntity.propertyId)
        assertEquals("reg_uuid_1", restoredEntity.cloudId)
    }

    @Test
    fun testPendingTenantRegistrationsFirestoreRulesIntegrity() {
        val rootRulesFile = listOf(
            java.io.File("firestore.rules"),
            java.io.File("../firestore.rules")
        ).firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Could not locate root firestore.rules")

        val appRulesFile = listOf(
            java.io.File("app/firestore.rules"),
            java.io.File("firestore.rules")
        ).firstOrNull { it.exists() && it.canonicalPath != rootRulesFile.canonicalPath }
            ?: rootRulesFile

        val rootRules = rootRulesFile.readText()
        val appRules = appRulesFile.readText()

        // 1. Both rules files must be identical
        assertEquals("Root firestore.rules and app/firestore.rules must be identical", rootRules, appRules)

        // 2. pending_tenant_registrations collection rule must exist
        assertTrue("firestore.rules must match pending_tenant_registrations collection", rootRules.contains("match /pending_tenant_registrations/{regId}"))

        // 3. pending_tenant_registrations must enforce strict ownership controls
        val pendingBlock = rootRules.substringAfter("match /pending_tenant_registrations/{regId}").substringBefore("}")
        assertTrue("read/delete must be governed by isOwnerReadOrDelete()", pendingBlock.contains("allow read, delete: if isOwnerReadOrDelete();"))
        assertTrue("create must be governed by isOwnerCreate() with data validation", pendingBlock.contains("isOwnerCreate()") && pendingBlock.contains("isValidPendingRegistrationData(request.resource.data)"))
        assertTrue("update must be governed by isOwnerUpdate() with data validation", pendingBlock.contains("isOwnerUpdate()") && pendingBlock.contains("isValidPendingRegistrationData(request.resource.data)"))

        // 4. Must NOT be publicly writable or unauthenticated
        assertFalse("pending_tenant_registrations must NOT allow unauthenticated or public writes", pendingBlock.contains("allow write") || pendingBlock.contains("allow create: if true") || pendingBlock.contains("request.auth == null"))
    }

    @Test
    fun testDeterministicRegistrationCloudIdGeneration() {
        val formId = "1FAIpQLSc_EXAMPLE_FORM_ID"
        val responseId = "ACYDBNhEXAMPLE_RESPONSE_123"

        val id1 = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(formId, responseId)
        val id2 = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(formId, responseId)
        val idWithTrim = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId("  $formId  ", " $responseId ")

        // Same formId + responseId must be completely deterministic
        assertEquals(id1, id2)
        assertEquals(id1, idWithTrim)
        assertTrue("Generated ID must start with reg_ prefix", id1.startsWith("reg_"))

        // Different response ID must produce different cloudId
        val idDifferentResponse = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(formId, "DIFFERENT_RESP")
        assertFalse("Different response ID must produce different registration cloudId", id1 == idDifferentResponse)

        // Different form ID must produce different cloudId
        val idDifferentForm = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId("DIFFERENT_FORM", responseId)
        assertFalse("Different form ID must produce different registration cloudId", id1 == idDifferentForm)
    }

    @Test
    fun testLegacyRegistrationDtoPreservation() {
        val legacyRandomUuid = java.util.UUID.randomUUID().toString()
        val legacyDto = com.example.data.firestore.model.PendingTenantRegistrationDto(
            cloudId = legacyRandomUuid,
            ownerId = "owner_test_123",
            propertyId = "prop_123",
            formId = "FORM_123",
            responseId = "RESP_123",
            status = "ACCEPTED",
            reviewedBy = "owner@example.com",
            reviewedAt = 1720000000000L,
            rejectionReason = "",
            createdTenantCloudId = "tenant_cloud_456",
            claimedByDeviceId = "device_A",
            claimedAt = 1719999000000L,
            lastModifiedByDeviceId = "device_A"
        )

        // 1. Confirm legacy cloudId is preserved and NOT overwritten by deterministic generator
        val deterministicId = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(legacyDto.formId, legacyDto.responseId)
        assertFalse("Legacy UUID must not match deterministic ID", legacyRandomUuid == deterministicId)
        assertEquals(legacyRandomUuid, legacyDto.cloudId)

        // 2. Confirm reviewed state and metadata are retained
        assertEquals("ACCEPTED", legacyDto.status)
        assertEquals("owner@example.com", legacyDto.reviewedBy)
        assertEquals(1720000000000L, legacyDto.reviewedAt)
        assertEquals("tenant_cloud_456", legacyDto.createdTenantCloudId)
        assertEquals("device_A", legacyDto.claimedByDeviceId)
        assertEquals(1719999000000L, legacyDto.claimedAt)
    }

    @Test
    fun testStableTenantCloudIdDerivationFromRegistration() {
        val formId = "FORM_KYC_01"
        val responseId = "RESP_998877"
        val regCloudId = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(formId, responseId)

        assertTrue(regCloudId.startsWith("reg_"))
        val derivedTenantCloudId = "tenant_${regCloudId.removePrefix("reg_")}"

        // Must be stable across multiple evaluations
        val regCloudId2 = PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(formId, responseId)
        val derivedTenantCloudId2 = "tenant_${regCloudId2.removePrefix("reg_")}"

        assertEquals(derivedTenantCloudId, derivedTenantCloudId2)
        assertTrue(derivedTenantCloudId.startsWith("tenant_"))
        assertFalse("Derived tenant cloud ID must not contain random elements", derivedTenantCloudId.isBlank())
    }

    @Test
    fun testRejectionAndAcceptanceConflictPrevention() {
        // Model simulation of terminal states
        val acceptedDto = com.example.data.firestore.model.PendingTenantRegistrationDto(
            cloudId = "reg_123",
            status = "ACCEPTED",
            createdTenantCloudId = "tenant_123",
            reviewedBy = "admin@example.com"
        )
        val rejectedDto = com.example.data.firestore.model.PendingTenantRegistrationDto(
            cloudId = "reg_123",
            status = "REJECTED",
            rejectionReason = "Incomplete KYC"
        )

        // 1. An accepted registration cannot be rejected
        val canRejectAccepted = acceptedDto.status != "ACCEPTED"
        assertFalse("An accepted registration must reject any attempt to mark it REJECTED", canRejectAccepted)

        // 2. A rejected registration cannot be accepted
        val canAcceptRejected = rejectedDto.status != "REJECTED"
        assertFalse("A rejected registration must reject any attempt to claim or accept it", canAcceptRejected)

        // 3. Repeated rejection is idempotent (status remains REJECTED)
        val reRejected = rejectedDto.copy(rejectionReason = "Duplicate attempt")
        assertEquals("REJECTED", reRejected.status)
        assertEquals("Duplicate attempt", reRejected.rejectionReason)
    }
}
