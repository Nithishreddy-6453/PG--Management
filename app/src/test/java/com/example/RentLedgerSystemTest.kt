package com.example

import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.rent.domain.util.RentBillingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RentLedgerSystemTest {

    // =========================================================================
    // SPECIFICATION 22: REQUIRED ACCEPTANCE TEST
    // =========================================================================
    @Test
    fun testRequiredAcceptanceScenario_Rahul() {
        // Tenant Rahul, Monthly rent ₹6,000, Join date: October 15, 2026
        val monthlyRent = 6000.0
        val joinDate = "2026-10-15"
        val billingMonth = "October 2026"

        // 1. Calculate October proration
        val octProration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = joinDate,
            leavingDateStr = null,
            billingMonthStr = billingMonth
        )

        assertEquals("October has 31 days", 31, octProration.daysInMonth)
        assertEquals("Applicable days from Oct 15 to Oct 31 inclusive is 17", 17, octProration.applicableDays)
        assertTrue("October is prorated", octProration.isProrated)

        val octExpectedRent = RentBillingEngine.calculateExpectedRent(
            monthlyRent = monthlyRent,
            applicableDays = octProration.applicableDays,
            daysInMonth = octProration.daysInMonth
        )

        // ₹6,000 * 17 / 31 = ₹3,290.32
        assertEquals(3290.32, octExpectedRent, 0.001)

        // Initial status
        var initialStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, 0.0, "2026-10-15")
        assertEquals("Pending", initialStatus)

        // Rahul pays ₹3,000
        val paidFirst = 3000.0
        val remainingFirst = octExpectedRent - paidFirst
        assertEquals(290.32, remainingFirst, 0.001)

        val partialStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, paidFirst, "2026-10-15")
        assertEquals("Partially Paid", partialStatus)

        // Rahul pays remaining ₹290.32
        val totalPaid = paidFirst + 290.32
        val remainingFinal = octExpectedRent - totalPaid
        assertEquals(0.0, remainingFinal, 0.001)

        val fullStatus = RentBillingEngine.calculatePaymentStatus(octExpectedRent, totalPaid, "2026-10-15")
        assertEquals("Paid", fullStatus)

        // 2. November must be full rent
        val novProration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = joinDate,
            leavingDateStr = null,
            billingMonthStr = "November 2026"
        )
        assertEquals("November has 30 days", 30, novProration.daysInMonth)
        assertEquals("Applicable days in November is 30", 30, novProration.applicableDays)
        assertFalse("November is NOT prorated", novProration.isProrated)

        val novExpectedRent = RentBillingEngine.calculateExpectedRent(
            monthlyRent = monthlyRent,
            applicableDays = novProration.applicableDays,
            daysInMonth = novProration.daysInMonth
        )
        assertEquals(6000.0, novExpectedRent, 0.001)
    }

    // =========================================================================
    // SPECIFICATION 21: EDGE CASE UNIT TESTS
    // =========================================================================

    @Test
    fun test01_tenantJoinsOnDay1_fullMonthRent() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-01",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(31, proration.applicableDays)
        assertFalse(proration.isProrated)

        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(6000.0, rent, 0.001)
    }

    @Test
    fun test02_tenantJoinsOnDay15_prorated() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-15",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(17, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3290.32, rent, 0.001)
    }

    @Test
    fun test03_tenantJoinsOnLastDay_1DayRent() {
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-31",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(1, proration.applicableDays)
        assertTrue(proration.isProrated)

        // 6000 * 1 / 31 = 193.548... -> 193.55
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(193.55, rent, 0.001)
    }

    @Test
    fun test04_february28Days_nonLeapYear() {
        val days = RentBillingEngine.getDaysInMonth(2026, 2)
        assertEquals(28, days)

        // Joins Feb 15 in 2026 -> 28 - 15 + 1 = 14 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-02-15",
            leavingDateStr = null,
            billingMonthStr = "February 2026"
        )
        assertEquals(28, proration.daysInMonth)
        assertEquals(14, proration.applicableDays)

        // 6000 * 14 / 28 = 3000.0
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3000.0, rent, 0.001)
    }

    @Test
    fun test05_february29Days_leapYear() {
        val days = RentBillingEngine.getDaysInMonth(2028, 2)
        assertEquals(29, days)

        // Joins Feb 15 in 2028 (leap year) -> 29 - 15 + 1 = 15 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2028-02-15",
            leavingDateStr = null,
            billingMonthStr = "February 2028"
        )
        assertEquals(29, proration.daysInMonth)
        assertEquals(15, proration.applicableDays)

        // 6000 * 15 / 29 = 3103.448... -> 3103.45
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3103.45, rent, 0.001)
    }

    @Test
    fun test06_30DayMonth_November() {
        val days = RentBillingEngine.getDaysInMonth(2026, 11)
        assertEquals(30, days)

        // Joins Nov 11 in 30-day month -> 30 - 11 + 1 = 20 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-11-11",
            leavingDateStr = null,
            billingMonthStr = "November 2026"
        )
        assertEquals(30, proration.daysInMonth)
        assertEquals(20, proration.applicableDays)

        // 6000 * 20 / 30 = 4000.0
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(4000.0, rent, 0.001)
    }

    @Test
    fun test07_tenantLeavesMidMonth() {
        // Leaving Oct 20 in 31-day month -> Oct 1 to Oct 20 = 20 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-06-01",
            leavingDateStr = "2026-10-20",
            billingMonthStr = "October 2026"
        )
        assertEquals(31, proration.daysInMonth)
        assertEquals(20, proration.applicableDays)
        assertTrue(proration.isProrated)

        // 6000 * 20 / 31 = 3870.967... -> 3870.97
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3870.97, rent, 0.001)
    }

    @Test
    fun test08_tenantJoinsAndLeavesSameMonth() {
        // Joins Oct 10, Leaves Oct 25 -> 25 - 10 + 1 = 16 days
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-10-10",
            leavingDateStr = "2026-10-25",
            billingMonthStr = "October 2026"
        )
        assertEquals(16, proration.applicableDays)

        // 6000 * 16 / 31 = 3096.77
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(3096.77, rent, 0.001)
    }

    @Test
    fun test09_tenantVacatedBeforeMonth_zeroApplicableDays() {
        // Left in Sept 2026, generating Oct 2026
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-01-01",
            leavingDateStr = "2026-09-30",
            billingMonthStr = "October 2026"
        )
        assertEquals(0, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(0.0, rent, 0.001)
    }

    @Test
    fun test10_tenantJoinsInFutureMonth_zeroApplicableDays() {
        // Joins in Nov 2026, generating Oct 2026
        val proration = RentBillingEngine.calculateProrationDetails(
            moveInDateStr = "2026-11-01",
            leavingDateStr = null,
            billingMonthStr = "October 2026"
        )
        assertEquals(0, proration.applicableDays)
        val rent = RentBillingEngine.calculateExpectedRent(6000.0, proration.applicableDays, proration.daysInMonth)
        assertEquals(0.0, rent, 0.001)
    }

    @Test
    fun test11_deterministicCloudIdGeneration() {
        val id1 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "October 2026")
        val id2 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "2026-10")
        val id3 = RentBillingEngine.generateDeterministicCloudId("prop_1", 10, "Oct 2026")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
        assertEquals("payment_prop_1_10_2026_10", id1)
    }

    @Test
    fun test12_monthPreviewGeneration() {
        val tenants = listOf(
            TenantEntity(
                id = 1,
                name = "Tenant Full",
                phone = "9999999991",
                email = "",
                emergencyContact = "",
                roomNumber = "101",
                bedId = "Bed A",
                monthlyRent = 10000.0,
                securityDeposit = 10000.0,
                moveInDate = "2026-01-01",
                isKycUploaded = false,
                kycDocType = "None"
            ),
            TenantEntity(
                id = 2,
                name = "Rahul",
                phone = "9999999992",
                email = "",
                emergencyContact = "",
                roomNumber = "102",
                bedId = "Bed A",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-10-15",
                isKycUploaded = false,
                kycDocType = "None"
            ),
            TenantEntity(
                id = 3,
                name = "Leaving Tenant",
                phone = "9999999993",
                email = "",
                emergencyContact = "",
                roomNumber = "103",
                bedId = "Bed A",
                monthlyRent = 6000.0,
                securityDeposit = 6000.0,
                moveInDate = "2026-01-01",
                leavingDate = "2026-10-20",
                isKycUploaded = false,
                kycDocType = "None"
            )
        )

        val preview = RentBillingEngine.generateMonthPreview(
            propertyId = "prop_1",
            propertyName = "Emerald Stays",
            billingMonthStr = "October 2026",
            allTenants = tenants,
            existingMonthPayments = emptyList()
        )

        assertEquals("October 2026", preview.billingMonth)
        assertEquals(3, preview.totalApplicableTenants)
        assertEquals(1, preview.fullMonthTenants)
        assertEquals(2, preview.proratedTenants)
        assertEquals(1, preview.leavingTenants)

        // Expected: 10000 + 3290.32 + 3870.97 = 17161.29
        assertEquals(17161.29, preview.expectedTotalRent, 0.01)
        assertFalse(preview.isAlreadyGenerated)
    }

    @Test
    fun test13_monthlyRentStatsCalculation() {
        val payments = listOf(
            RentPaymentEntity(
                id = 1,
                tenantId = 1,
                tenantName = "Tenant 1",
                roomNumber = "101",
                billingMonth = "October 2026",
                amount = 10000.0,
                amountPaid = 10000.0,
                dueDate = "2026-10-05",
                paymentDate = "2026-10-04",
                paymentMode = "UPI",
                status = "Paid"
            ),
            RentPaymentEntity(
                id = 2,
                tenantId = 2,
                tenantName = "Rahul",
                roomNumber = "102",
                billingMonth = "October 2026",
                amount = 3290.32,
                amountPaid = 3000.0,
                dueDate = "2026-10-15",
                paymentDate = "2026-10-15",
                paymentMode = "UPI",
                status = "Partially Paid"
            ),
            RentPaymentEntity(
                id = 3,
                tenantId = 3,
                tenantName = "Tenant 3",
                roomNumber = "103",
                billingMonth = "October 2026",
                amount = 5000.0,
                amountPaid = 0.0,
                dueDate = "2026-10-05",
                paymentDate = null,
                paymentMode = null,
                status = "Pending"
            )
        )

        val stats = RentBillingEngine.computeMonthlyRentStats("October 2026", payments)

        assertEquals(18290.32, stats.totalExpectedRent, 0.01)
        assertEquals(13000.0, stats.totalPaid, 0.01)
        assertEquals(5290.32, stats.totalOutstanding, 0.01)
        assertEquals(3, stats.totalTenantsCount)
        assertEquals(1, stats.paidTenantsCount)
        assertEquals(1, stats.partiallyPaidTenantsCount)
        assertEquals(1, stats.pendingTenantsCount)
        // 13000 / 18290.32 = 71.076% -> 71.1%
        assertEquals(71.1, stats.collectionPercentage, 0.1)
    }

    @Test
    fun testPreviousAndNextBillingMonthCalculations() {
        assertEquals("September 2026", RentBillingEngine.getPreviousBillingMonth("October 2026"))
        assertEquals("November 2026", RentBillingEngine.getNextBillingMonth("October 2026"))
        assertEquals("December 2025", RentBillingEngine.getPreviousBillingMonth("January 2026"))
        assertEquals("January 2027", RentBillingEngine.getNextBillingMonth("December 2026"))
    }
}
