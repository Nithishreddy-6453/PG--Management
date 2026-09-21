package com.example.features.settings.domain.model

data class BusinessSettings(
    val pgName: String = "",
    val ownerName: String = "",
    val contactNumber: String = "",
    val emailAddress: String = "",
    val address: String = "",
    val defaultMonthlyRent: Double = 0.0,
    val defaultSecurityDeposit: Double = 0.0,
    val defaultRentDueDay: Int = 1,
    val currency: String = "₹"
)

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,
    val dateFormat: String = "dd/MM/yyyy",
    val numberFormat: String = "Indian",
    val language: String = "en"
)

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

data class SecuritySettings(
    val biometricEnabled: Boolean = false,
    val autoLockTimeout: Long = 0L, // 0 means immediately, or X minutes
    val requirePinOnResume: Boolean = true
)

data class NotificationSettings(
    val rentDueReminder: Boolean = true,
    val overdueRentReminder: Boolean = true,
    val monthlyInvoiceReminder: Boolean = true,
    val backupReminder: Boolean = false,
    val appUpdates: Boolean = true
)
