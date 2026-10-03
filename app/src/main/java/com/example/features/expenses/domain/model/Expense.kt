package com.example.features.expenses.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * An individual payment transaction recorded against an expense.
 * Supports multiple partial payments against a single expense record.
 */
data class ExpensePayment(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val paymentDate: String,
    val paymentMethod: String = "Cash",
    val reference: String = "",
    val notes: String = ""
)

/**
 * Domain model representing an operational or capital Expense.
 * Supports:
 * - One-time vs Recurring
 * - Expected amount, Paid amount, Remaining amount
 * - Payment status (Unpaid, Partially Paid, Paid)
 * - Multiple payment transactions history
 */
data class Expense(
    val id: Int = 0,
    val title: String,
    val category: String,
    val amount: Double,                    // Total / Expected expense amount
    val paidAmount: Double = amount,       // Amount paid so far
    val remainingAmount: Double = 0.0,     // Amount remaining to pay
    val status: String = "Paid",           // "Unpaid", "Partially Paid", "Paid"
    val date: String,                      // Expense date (YYYY-MM-DD)
    val paymentDate: String? = null,       // Date payment was made
    val paymentMethod: String = "Cash",    // Primary / default payment method
    val vendor: String? = null,
    val notes: String = "",
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = null, // "Monthly", "Yearly"
    val recurringStartDate: String? = null,
    val recurringEndDate: String? = null,
    val recurringExpenseId: String? = null,
    val payments: List<ExpensePayment> = emptyList()
) {
    val isFullyPaid: Boolean
        get() = status.equals("Paid", ignoreCase = true) || (remainingAmount <= 0.001 && paidAmount > 0)

    val isPartiallyPaid: Boolean
        get() = status.equals("Partially Paid", ignoreCase = true) || (paidAmount > 0.001 && remainingAmount > 0.001)

    val isUnpaid: Boolean
        get() = status.equals("Unpaid", ignoreCase = true) || (paidAmount <= 0.001)

    companion object {
        private const val META_TAG = "__EXPENSE_META__:"

        /**
         * Packs additional financial and recurring metadata into the entity's notes field
         * in a clean, robust, and backward-compatible format.
         */
        fun packNotesWithMetadata(
            userNotes: String,
            status: String,
            paidAmount: Double,
            remainingAmount: Double,
            paymentDate: String?,
            isRecurring: Boolean,
            recurringFrequency: String?,
            recurringStartDate: String?,
            recurringEndDate: String?,
            recurringExpenseId: String?,
            payments: List<ExpensePayment>
        ): String {
            try {
                val json = JSONObject()
                json.put("status", status)
                json.put("paidAmount", paidAmount)
                json.put("remainingAmount", remainingAmount)
                if (paymentDate != null) json.put("paymentDate", paymentDate)
                json.put("isRecurring", isRecurring)
                if (recurringFrequency != null) json.put("recurringFrequency", recurringFrequency)
                if (recurringStartDate != null) json.put("recurringStartDate", recurringStartDate)
                if (recurringEndDate != null) json.put("recurringEndDate", recurringEndDate)
                if (recurringExpenseId != null) json.put("recurringExpenseId", recurringExpenseId)

                val paymentsArray = JSONArray()
                for (p in payments) {
                    val pJson = JSONObject()
                    pJson.put("id", p.id)
                    pJson.put("amount", p.amount)
                    pJson.put("paymentDate", p.paymentDate)
                    pJson.put("paymentMethod", p.paymentMethod)
                    pJson.put("reference", p.reference)
                    pJson.put("notes", p.notes)
                    paymentsArray.put(pJson)
                }
                json.put("payments", paymentsArray)
                json.put("userNotes", userNotes)

                return META_TAG + json.toString()
            } catch (_: Exception) {
                return userNotes
            }
        }

        /**
         * Parses notes field to extract user notes and unpacked metadata.
         */
        fun unpackNotes(
            rawNotes: String,
            baseAmount: Double,
            baseDate: String,
            basePaymentMethod: String
        ): ParsedExpenseMetadata {
            if (!rawNotes.startsWith(META_TAG)) {
                // Legacy or un-tagged expense: treat as fully paid disbursement
                val legacyPayment = if (baseAmount > 0) {
                    listOf(
                        ExpensePayment(
                            id = "legacy_pay",
                            amount = baseAmount,
                            paymentDate = baseDate,
                            paymentMethod = basePaymentMethod,
                            reference = "",
                            notes = ""
                        )
                    )
                } else emptyList()

                return ParsedExpenseMetadata(
                    userNotes = rawNotes,
                    status = "Paid",
                    paidAmount = baseAmount,
                    remainingAmount = 0.0,
                    paymentDate = baseDate,
                    isRecurring = false,
                    recurringFrequency = null,
                    recurringStartDate = null,
                    recurringEndDate = null,
                    recurringExpenseId = null,
                    payments = legacyPayment
                )
            }

            return try {
                val jsonStr = rawNotes.removePrefix(META_TAG)
                val json = JSONObject(jsonStr)

                val userNotes = json.optString("userNotes", "")
                val status = json.optString("status", "Paid")
                val paidAmount = json.optDouble("paidAmount", baseAmount)
                val remainingAmount = json.optDouble("remainingAmount", (baseAmount - paidAmount).coerceAtLeast(0.0))
                val paymentDate = if (json.has("paymentDate")) json.optString("paymentDate") else null
                val isRecurring = json.optBoolean("isRecurring", false)
                val recurringFrequency = if (json.has("recurringFrequency")) json.optString("recurringFrequency") else null
                val recurringStartDate = if (json.has("recurringStartDate")) json.optString("recurringStartDate") else null
                val recurringEndDate = if (json.has("recurringEndDate")) json.optString("recurringEndDate") else null
                val recurringExpenseId = if (json.has("recurringExpenseId")) json.optString("recurringExpenseId") else null

                val paymentsList = mutableListOf<ExpensePayment>()
                val paymentsArray = json.optJSONArray("payments")
                if (paymentsArray != null) {
                    for (i in 0 until paymentsArray.length()) {
                        val pJson = paymentsArray.getJSONObject(i)
                        paymentsList.add(
                            ExpensePayment(
                                id = pJson.optString("id", UUID.randomUUID().toString()),
                                amount = pJson.optDouble("amount", 0.0),
                                paymentDate = pJson.optString("paymentDate", baseDate),
                                paymentMethod = pJson.optString("paymentMethod", basePaymentMethod),
                                reference = pJson.optString("reference", ""),
                                notes = pJson.optString("notes", "")
                            )
                        )
                    }
                } else if (paidAmount > 0) {
                    paymentsList.add(
                        ExpensePayment(
                            id = "pay_0",
                            amount = paidAmount,
                            paymentDate = paymentDate ?: baseDate,
                            paymentMethod = basePaymentMethod,
                            reference = "",
                            notes = ""
                        )
                    )
                }

                ParsedExpenseMetadata(
                    userNotes = userNotes,
                    status = status,
                    paidAmount = paidAmount,
                    remainingAmount = remainingAmount,
                    paymentDate = paymentDate,
                    isRecurring = isRecurring,
                    recurringFrequency = recurringFrequency,
                    recurringStartDate = recurringStartDate,
                    recurringEndDate = recurringEndDate,
                    recurringExpenseId = recurringExpenseId,
                    payments = paymentsList
                )
            } catch (_: Exception) {
                ParsedExpenseMetadata(
                    userNotes = rawNotes,
                    status = "Paid",
                    paidAmount = baseAmount,
                    remainingAmount = 0.0,
                    paymentDate = baseDate,
                    isRecurring = false,
                    recurringFrequency = null,
                    recurringStartDate = null,
                    recurringEndDate = null,
                    recurringExpenseId = null,
                    payments = emptyList()
                )
            }
        }
    }
}

data class ParsedExpenseMetadata(
    val userNotes: String,
    val status: String,
    val paidAmount: Double,
    val remainingAmount: Double,
    val paymentDate: String?,
    val isRecurring: Boolean,
    val recurringFrequency: String?,
    val recurringStartDate: String?,
    val recurringEndDate: String?,
    val recurringExpenseId: String?,
    val payments: List<ExpensePayment>
)
