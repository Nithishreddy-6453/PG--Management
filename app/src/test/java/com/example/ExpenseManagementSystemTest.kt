package com.example

import com.example.data.database.ExpenseEntity
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.model.ExpensePayment
import com.example.features.expenses.domain.usecase.FilterExpensesUseCase
import com.example.features.expenses.domain.usecase.SearchExpensesUseCase
import com.example.features.expenses.domain.usecase.SortType
import com.example.features.expenses.domain.usecase.ValidateExpenseUseCase
import com.example.features.expenses.domain.usecase.ValidationResult
import com.example.features.rent.domain.util.RentBillingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpenseManagementSystemTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // =========================================================================
    // 1. ONE-TIME EXPENSE CREATION & BILLING MONTH DERIVATION
    // =========================================================================
    @Test
    fun testOneTimeExpense_DerivesBillingMonthCorrectly() {
        val expenseDate = "2026-10-03"
        val parsedDate = dateFormat.parse(expenseDate) ?: Date()
        val derivedBillingMonth = RentBillingEngine.formatCanonicalBillingMonth(parsedDate)

        assertEquals("October 2026", derivedBillingMonth)

        val expense = ExpenseEntity(
            id = 1,
            cloudId = "exp_purifier_001",
            propertyId = "prop_101",
            title = "Water purifier",
            category = "Appliances",
            amount = 8000.0,
            date = expenseDate,
            paymentMethod = "UPI",
            vendor = "Kent RO Service",
            notes = "Purifier for 2nd floor kitchen",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("Water purifier", expense.title)
        assertEquals(8000.0, expense.amount, 0.001)
        assertEquals("2026-10-03", expense.date)
        assertEquals("prop_101", expense.propertyId)
        assertEquals("UPI", expense.paymentMethod)
    }

    // =========================================================================
    // 2. EXPENSE DATE VS BILLING MONTH DERIVATION
    // =========================================================================
    @Test
    fun testExpenseDate_BillingMonthDerivation() {
        val sepExpenseDate = "2026-09-30"
        val octExpenseDate = "2026-10-05"

        val sepParsed = dateFormat.parse(sepExpenseDate) ?: Date()
        val octParsed = dateFormat.parse(octExpenseDate) ?: Date()

        val sepMonth = RentBillingEngine.formatCanonicalBillingMonth(sepParsed)
        val octMonth = RentBillingEngine.formatCanonicalBillingMonth(octParsed)

        assertEquals("September 2026", sepMonth)
        assertEquals("October 2026", octMonth)

        val prevOfOct = RentBillingEngine.getPreviousBillingMonth("October 2026")
        val nextOfOct = RentBillingEngine.getNextBillingMonth("October 2026")

        assertEquals("September 2026", prevOfOct)
        assertEquals("November 2026", nextOfOct)
    }

    // =========================================================================
    // 3. SEARCH & FILTERING EXPENSES
    // =========================================================================
    @Test
    fun testSearchAndFilterExpenses() {
        val expenses = listOf(
            Expense(id = 1, title = "Electricity Bill", category = "Electricity", amount = 4500.0, date = "2026-10-05", paymentMethod = "UPI", vendor = "BESCOM", notes = "Meter 101"),
            Expense(id = 2, title = "Airtel Wi-Fi", category = "Internet", amount = 1200.0, date = "2026-10-01", paymentMethod = "Online", vendor = "Airtel", notes = "Fiber 300mbps"),
            Expense(id = 3, title = "Plumbing Repair", category = "Maintenance", amount = 850.0, date = "2026-10-12", paymentMethod = "Cash", vendor = "Ramesh", notes = "Bathroom tap"),
            Expense(id = 4, title = "Security Guard Salary", category = "Staff", amount = 12000.0, date = "2026-09-30", paymentMethod = "Bank Transfer", vendor = "Suresh", notes = "Sep salary")
        )

        val searchUseCase = SearchExpensesUseCase()
        val filterUseCase = FilterExpensesUseCase()

        // 1. Search by title
        val searchResult = searchUseCase(expenses, "Wi-Fi")
        assertEquals(1, searchResult.size)
        assertEquals("Airtel Wi-Fi", searchResult[0].title)

        // 2. Filter by Category
        val electricityList = filterUseCase(expenses, category = "Electricity", month = null, paymentMethod = null)
        assertEquals(1, electricityList.size)
        assertEquals(4500.0, electricityList[0].amount, 0.001)

        // 3. Filter by Month
        val octList = filterUseCase(expenses, category = null, month = "2026-10", paymentMethod = null)
        assertEquals(3, octList.size)

        // 4. Sort by amount descending
        val sortedByAmount = filterUseCase(expenses, category = null, month = null, paymentMethod = null, sortBy = SortType.AMOUNT_DESC)
        assertEquals(12000.0, sortedByAmount.first().amount, 0.001)
        assertEquals(850.0, sortedByAmount.last().amount, 0.001)
    }

    // =========================================================================
    // 4. VALIDATION
    // =========================================================================
    @Test
    fun testExpenseValidation() {
        val validator = ValidateExpenseUseCase()

        // Empty title
        val res1 = validator(title = "", category = "Maintenance", amount = "500", date = "2026-10-01")
        assertTrue(res1 is ValidationResult.Error)

        // Invalid amount
        val res2 = validator(title = "Repairs", category = "Maintenance", amount = "-100", date = "2026-10-01")
        assertTrue(res2 is ValidationResult.Error)

        // Valid expense
        val res3 = validator(title = "Cleaning Supplies", category = "Supplies", amount = "650", date = "2026-10-01")
        assertTrue(res3 is ValidationResult.Success)
    }

    // =========================================================================
    // 5. PROPERTY ISOLATION
    // =========================================================================
    @Test
    fun testPropertyIsolation() {
        val propAExpenses = listOf(
            ExpenseEntity(id = 1, propertyId = "prop_A", title = "Cleaning Prop A", category = "Cleaning", amount = 2500.0, date = "2026-10-01", notes = "")
        )

        val propBExpenses = listOf(
            ExpenseEntity(id = 2, propertyId = "prop_B", title = "Security Prop B", category = "Security", amount = 8000.0, date = "2026-10-01", notes = "")
        )

        val allExpenses = propAExpenses + propBExpenses

        val filteredA = allExpenses.filter { it.propertyId == "prop_A" }
        val filteredB = allExpenses.filter { it.propertyId == "prop_B" }

        assertEquals(1, filteredA.size)
        assertEquals("Cleaning Prop A", filteredA[0].title)

        assertEquals(1, filteredB.size)
        assertEquals("Security Prop B", filteredB[0].title)
    }

    // =========================================================================
    // 6. NET OPERATING PROFIT CALCULATION (Revenue - Expenses)
    // =========================================================================
    @Test
    fun testMonthlyFinancialSummary_NetOperatingProfit() {
        val collectedRentRevenue = 45000.0

        val expenses = listOf(
            ExpenseEntity(id = 1, propertyId = "prop_1", title = "Electricity", category = "Electricity", amount = 5000.0, date = "2026-10-05", notes = ""),
            ExpenseEntity(id = 2, propertyId = "prop_1", title = "Internet", category = "Internet", amount = 1200.0, date = "2026-10-01", notes = ""),
            ExpenseEntity(id = 3, propertyId = "prop_1", title = "Plumbing Repair", category = "Maintenance", amount = 3000.0, date = "2026-10-12", notes = "")
        )

        val totalExpenseAmount = expenses.sumOf { it.amount }
        assertEquals(9200.0, totalExpenseAmount, 0.001)

        val netOperatingProfit = collectedRentRevenue - totalExpenseAmount
        assertEquals(35800.0, netOperatingProfit, 0.001) // ₹45,000 - ₹9,200 = ₹35,800
    }

    // =========================================================================
    // 7. MULTIPLE PARTIAL PAYMENTS ON A SINGLE EXPENSE RECORD
    // =========================================================================
    @Test
    fun testMultiplePartialPayments_StatusTransitions() {
        val totalExpected = 6000.0
        val p1 = ExpensePayment(id = "p1", amount = 2000.0, paymentDate = "2026-10-05", paymentMethod = "UPI")
        val p2 = ExpensePayment(id = "p2", amount = 2000.0, paymentDate = "2026-10-12", paymentMethod = "Cash")
        val p3 = ExpensePayment(id = "p3", amount = 2000.0, paymentDate = "2026-10-20", paymentMethod = "UPI")

        // 1. Initial Unpaid state
        val initialExpense = Expense(
            id = 10,
            title = "Building Painting",
            category = "Maintenance",
            amount = totalExpected,
            paidAmount = 0.0,
            remainingAmount = totalExpected,
            status = "Unpaid",
            date = "2026-10-01",
            payments = emptyList()
        )
        assertTrue(initialExpense.isUnpaid)
        assertFalse(initialExpense.isFullyPaid)
        assertEquals(6000.0, initialExpense.remainingAmount, 0.001)

        // 2. First Payment ₹2,000 -> Partially Paid
        val afterP1 = initialExpense.copy(
            paidAmount = 2000.0,
            remainingAmount = 4000.0,
            status = "Partially Paid",
            payments = listOf(p1)
        )
        assertTrue(afterP1.isPartiallyPaid)
        assertFalse(afterP1.isFullyPaid)
        assertEquals(4000.0, afterP1.remainingAmount, 0.001)

        // 3. Second Payment ₹2,000 -> Still Partially Paid
        val afterP2 = afterP1.copy(
            paidAmount = 4000.0,
            remainingAmount = 2000.0,
            status = "Partially Paid",
            payments = listOf(p1, p2)
        )
        assertTrue(afterP2.isPartiallyPaid)
        assertEquals(2000.0, afterP2.remainingAmount, 0.001)

        // 4. Third Payment ₹2,000 -> Fully Paid
        val afterP3 = afterP2.copy(
            paidAmount = 6000.0,
            remainingAmount = 0.0,
            status = "Paid",
            payments = listOf(p1, p2, p3)
        )
        assertTrue(afterP3.isFullyPaid)
        assertFalse(afterP3.isPartiallyPaid)
        assertEquals(0.0, afterP3.remainingAmount, 0.001)
        assertEquals(3, afterP3.payments.size)
    }

    // =========================================================================
    // 8. METADATA SERIALIZATION PACK/UNPACK SAFETY
    // =========================================================================
    @Test
    fun testMetadataPackUnpack_PreservesFinancialHistory() {
        val originalPayments = listOf(
            ExpensePayment(id = "pay_1", amount = 1500.0, paymentDate = "2026-10-02", paymentMethod = "UPI", reference = "REF123", notes = "Part 1"),
            ExpensePayment(id = "pay_2", amount = 1500.0, paymentDate = "2026-10-10", paymentMethod = "Cash", reference = "REC456", notes = "Part 2")
        )

        val packedNotes = Expense.packNotesWithMetadata(
            userNotes = "Monthly high speed internet",
            status = "Partially Paid",
            paidAmount = 3000.0,
            remainingAmount = 1000.0,
            paymentDate = "2026-10-10",
            isRecurring = true,
            recurringFrequency = "Monthly",
            recurringStartDate = "2026-10-01",
            recurringEndDate = null,
            recurringExpenseId = "rec_fiber_001",
            payments = originalPayments
        )

        // Unpack
        val unpacked = Expense.unpackNotes(
            rawNotes = packedNotes,
            baseAmount = 4000.0,
            baseDate = "2026-10-01",
            basePaymentMethod = "UPI"
        )

        assertEquals("Monthly high speed internet", unpacked.userNotes)
        assertEquals("Partially Paid", unpacked.status)
        assertEquals(3000.0, unpacked.paidAmount, 0.001)
        assertEquals(1000.0, unpacked.remainingAmount, 0.001)
        assertTrue(unpacked.isRecurring)
        assertEquals("Monthly", unpacked.recurringFrequency)
        assertEquals("rec_fiber_001", unpacked.recurringExpenseId)
        assertEquals(2, unpacked.payments.size)
        assertEquals("REF123", unpacked.payments[0].reference)
        assertEquals("REC456", unpacked.payments[1].reference)
    }
}
