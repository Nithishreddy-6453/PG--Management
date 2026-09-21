package com.example.features.backup.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val backupVersion: Int,
    val createdAt: String,
    val applicationVersion: String,
    val databaseVersion: Int,
    val businessSettings: BusinessSettingsDto?,
    val appSettings: AppSettingsDto?,
    val securitySettings: SecuritySettingsDto?,
    val notificationSettings: NotificationSettingsDto?,
    val profile: OwnerProfileDto?,
    val rooms: List<RoomDto>,
    val tenants: List<TenantDto>,
    val payments: List<RentPaymentDto>,
    val expenses: List<ExpenseDto>
)

@JsonClass(generateAdapter = true)
data class BusinessSettingsDto(
    val pgName: String,
    val ownerName: String,
    val contactNumber: String,
    val emailAddress: String,
    val address: String,
    val defaultMonthlyRent: Double,
    val defaultSecurityDeposit: Double,
    val defaultRentDueDay: Int,
    val currency: String
)

@JsonClass(generateAdapter = true)
data class AppSettingsDto(
    val theme: String,
    val dynamicColor: Boolean,
    val dateFormat: String,
    val numberFormat: String,
    val language: String
)

@JsonClass(generateAdapter = true)
data class SecuritySettingsDto(
    val biometricEnabled: Boolean,
    val autoLockTimeout: Long,
    val requirePinOnResume: Boolean
)

@JsonClass(generateAdapter = true)
data class NotificationSettingsDto(
    val rentDueReminder: Boolean,
    val overdueRentReminder: Boolean,
    val monthlyInvoiceReminder: Boolean,
    val backupReminder: Boolean,
    val appUpdates: Boolean
)

@JsonClass(generateAdapter = true)
data class OwnerProfileDto(
    val pgName: String,
    val ownerName: String,
    val phone: String,
    val upiId: String,
    val pinCode: String
)

@JsonClass(generateAdapter = true)
data class RoomDto(
    val roomNumber: String,
    val floor: String,
    val capacity: Int,
    val ratePerBed: Double,
    val roomType: String,
    val notes: String
)

@JsonClass(generateAdapter = true)
data class TenantDto(
    val id: Int,
    val name: String,
    val phone: String,
    val email: String,
    val emergencyContact: String,
    val roomNumber: String,
    val bedId: String,
    val monthlyRent: Double,
    val securityDeposit: Double,
    val moveInDate: String,
    val isKycUploaded: Boolean,
    val kycDocType: String,
    val alternateContact: String,
    val dob: String,
    val gender: String,
    val address: String,
    val occupation: String,
    val companyOrCollege: String,
    val advancePaid: Double,
    val notes: String
)

@JsonClass(generateAdapter = true)
data class RentPaymentDto(
    val id: Int,
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val billingMonth: String,
    val amount: Double,
    val amountPaid: Double,
    val dueDate: String,
    val paymentDate: String?,
    val paymentMode: String?,
    val transactionReference: String?,
    val remarks: String?,
    val status: String
)

@JsonClass(generateAdapter = true)
data class ExpenseDto(
    val id: Int,
    val amount: Double,
    val category: String,
    val date: String,
    val notes: String,
    val title: String,
    val paymentMethod: String,
    val vendor: String?
)
