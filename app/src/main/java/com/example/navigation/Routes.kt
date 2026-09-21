package com.example.navigation

/**
 * Type-safe Screen Route references matching standard layout specifications.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object AuthEmail : Screen("auth_email")
    object PinSetup : Screen("pin_setup")
    object PinLogin : Screen("pin_login")
    object Dashboard : Screen("dashboard")
    object Rooms : Screen("rooms")
    object AddRoom : Screen("add_room")
    object EditRoom : Screen("edit_room/{roomId}") {
        fun createRoute(roomId: String) = "edit_room/$roomId"
    }
    object RoomDetails : Screen("room_details/{roomId}") {
        fun createRoute(roomId: String) = "room_details/$roomId"
    }
    object Tenants : Screen("tenants")
    object Rent : Screen("rent")
    object RentLedger : Screen("rent_ledger")
    object PaymentDetails : Screen("payment_details/{paymentId}") {
        fun createRoute(paymentId: Int) = "payment_details/$paymentId"
    }
    object RecordPayment : Screen("record_payment")
    object EditPayment : Screen("edit_payment/{paymentId}") {
        fun createRoute(paymentId: Int) = "edit_payment/$paymentId"
    }
    object Receipt : Screen("receipt/{paymentId}") {
        fun createRoute(paymentId: Int) = "receipt/$paymentId"
    }
    object Expenses : Screen("expenses")
    object ExpenseDetails : Screen("expense_details/{expenseId}") {
        fun createRoute(expenseId: Int) = "expense_details/$expenseId"
    }
    object AddExpense : Screen("add_expense")
    object EditExpense : Screen("edit_expense/{expenseId}") {
        fun createRoute(expenseId: Int) = "edit_expense/$expenseId"
    }
    object Profile : Screen("profile")
    object Reports : Screen("reports")
    object ReportsRevenue : Screen("reports_revenue")
    object ReportsExpense : Screen("reports_expense")
    object ReportsRent : Screen("reports_rent")
    object ReportsOccupancy : Screen("reports_occupancy")
    object Settings : Screen("settings")
    object AddTenant : Screen("add_tenant")
    object EditTenant : Screen("edit_tenant/{tenantId}") {
        fun createRoute(tenantId: Int) = "edit_tenant/$tenantId"
    }
    object TenantDetails : Screen("tenant_details/{tenantId}") {
        fun createRoute(tenantId: Int) = "tenant_details/$tenantId"
    }
    object SettingsHome : Screen("settings_home")
    object BusinessSettings : Screen("settings_business")
    object AppSettings : Screen("settings_app")
    object SecuritySettings : Screen("settings_security")
    object NotificationSettings : Screen("settings_notifications")
    object BackupSettings : Screen("settings_backup")
    object SyncSettings : Screen("settings_sync")
    object AboutSettings : Screen("settings_about")
    object ExcelExport : Screen("excel_export")
    object ExcelImport : Screen("excel_import")

    companion object {
        const val KEY_ROOM_ID = "roomId"
        const val KEY_TENANT_ID = "tenantId"
        const val KEY_EXPENSE_ID = "expenseId"
    }
}
