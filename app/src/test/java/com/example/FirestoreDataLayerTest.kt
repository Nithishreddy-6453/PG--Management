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
            notes = "Spacious room"
        )

        val dto: RoomDto = roomEntity.toDto(ownerId)

        assertEquals("101", dto.id)
        assertEquals("101", dto.roomNumber)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("1st Floor", dto.floor)
        assertEquals(2, dto.capacity)
        assertEquals(5500.0, dto.ratePerBed, 0.01)
        assertEquals("AC", dto.roomType)
        assertFalse(dto.deleted)
        assertEquals("SYNCED", dto.syncStatus)

        val restoredEntity = dto.toEntity()
        assertEquals(roomEntity, restoredEntity)
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
            kycDocType = "Aadhaar Card"
        )

        val dto: TenantDto = tenantEntity.toDto(ownerId)

        assertEquals("tenant_42", dto.id)
        assertEquals(42, dto.localId)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("Rahul Sharma", dto.name)
        assertEquals("9876543210", dto.phone)
        assertTrue(dto.isKycUploaded)

        val restoredEntity = dto.toEntity()
        assertEquals(tenantEntity, restoredEntity)
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
            status = "Paid"
        )

        val dto: PaymentDto = paymentEntity.toDto(ownerId)

        assertEquals("payment_88", dto.id)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("July 2026", dto.billingMonth)
        assertEquals("Paid", dto.status)

        val restored = dto.toEntity()
        assertEquals(paymentEntity, restored)
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
            vendor = "Local Plumber"
        )

        val dto: ExpenseDto = expense.toDto(ownerId)

        assertEquals("expense_15", dto.id)
        assertEquals(ownerId, dto.ownerId)
        assertEquals(1200.0, dto.amount, 0.01)

        val restored = dto.toEntity()
        assertEquals(expense, restored)
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
}
