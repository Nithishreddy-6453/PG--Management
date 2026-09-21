package com.example

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.designsystem.PgTheme
import com.example.features.auth.ui.PinLoginScreen
import com.example.features.auth.ui.PinSetupScreen
import com.example.features.dashboard.ui.DashboardScreen
import com.example.features.dashboard.ui.DashboardViewModel
import com.example.features.rooms.ui.RoomListScreen
import com.example.features.tenants.*
import com.example.features.tenants.ui.viewmodel.*
import com.example.features.rent.RentScreen
import com.example.features.expenses.*
import com.example.features.reports.ui.FinancialDashboardScreen
import com.example.features.reports.ui.RevenueReportScreen
import com.example.features.reports.ui.ExpenseReportScreen
import com.example.features.reports.ui.RentAnalyticsScreen
import com.example.features.reports.ui.OccupancyReportScreen
import com.example.features.reports.ui.viewmodel.ReportsViewModel
import com.example.features.profile.ProfileScreen
import com.example.features.startup.SplashScreen
import com.example.features.startup.WelcomeScreen
import com.example.features.startup.StartupManager
import com.example.navigation.Screen
import com.example.ui.viewmodel.PgViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.example.features.settings.domain.repository.SettingsRepository
import com.example.features.settings.domain.model.AppSettings
import javax.inject.Inject

/**
 * Main application entry activity coordinating core view frames and system navigation layers.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var startupManager: StartupManager
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var authRepository: com.example.features.auth.domain.repository.FirebaseAuthRepository

    private val viewModel: PgViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable modern edge-to-edge system bar overlays
        enableEdgeToEdge()

        setContent {
            val appSettings by settingsRepository.getAppSettings().collectAsState(initial = AppSettings())
            val securitySettings by settingsRepository.getSecuritySettings().collectAsState(initial = com.example.features.settings.domain.model.SecuritySettings())
            PgTheme(appTheme = appSettings.theme, dynamicColor = appSettings.dynamicColor) {
                val navController = rememberNavController()
                var appBackgroundTime by remember { mutableStateOf(0L) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            appBackgroundTime = System.currentTimeMillis()
                        } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                            if (appBackgroundTime > 0L) {
                                val timeInBackground = System.currentTimeMillis() - appBackgroundTime
                                if (securitySettings.requirePinOnResume || (securitySettings.autoLockTimeout > 0 && timeInBackground >= securitySettings.autoLockTimeout)) {
                                    viewModel.lockApp()
                                    navController.navigate(Screen.PinLogin.route)
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                val profile by viewModel.profile.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // A. Splash Screen Entry Route
                        composable(route = Screen.Splash.route) {
                            SplashScreen(
                                startupManager = startupManager,
                                onInitializationComplete = {
                                    // Dynamic routing with Firebase Session Check
                                    val destination = if (authRepository.isUserLoggedIn()) {
                                        if (profile != null && !profile?.pinCode.isNullOrBlank()) {
                                            Screen.PinLogin.route
                                        } else {
                                            viewModel.unlockAppDirectly()
                                            Screen.Dashboard.route
                                        }
                                    } else {
                                        Screen.Welcome.route
                                    }

                                    navController.navigate(destination) {
                                        // Pop splash from backstack permanently to prevent backward loops
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        composable(route = Screen.Welcome.route) {
                            val authViewModel: com.example.features.auth.ui.viewmodel.AuthViewModel = hiltViewModel()
                            val authState by authViewModel.authState.collectAsState()
                            
                            LaunchedEffect(authState) {
                                val current = authState
                                if (current is com.example.features.auth.ui.viewmodel.AuthState.Success) {
                                    val isExisting = current.signInResult !is com.example.data.sync.SignInResult.NewAccount
                                    authViewModel.resetState()
                                    if (isExisting) {
                                        if (profile != null && !profile?.pinCode.isNullOrBlank()) {
                                            navController.navigate(Screen.PinLogin.route) {
                                                popUpTo(Screen.Welcome.route) { inclusive = true }
                                            }
                                        } else {
                                            viewModel.unlockAppDirectly()
                                            navController.navigate(Screen.Dashboard.route) {
                                                popUpTo(Screen.Welcome.route) { inclusive = true }
                                            }
                                        }
                                    } else {
                                        navController.navigate(Screen.PinSetup.route) {
                                            popUpTo(Screen.Welcome.route) { inclusive = true }
                                        }
                                    }
                                }
                            }
                            
                            WelcomeScreen(
                                onContinueWithEmail = {
                                    navController.navigate(Screen.AuthEmail.route)
                                },
                                onGoogleSignInSuccess = { idToken ->
                                    authViewModel.signInWithGoogle(idToken)
                                }
                            )
                        }
                        
                        composable(route = Screen.AuthEmail.route) {
                            val authViewModel: com.example.features.auth.ui.viewmodel.AuthViewModel = hiltViewModel()
                            com.example.features.auth.ui.screens.AuthEmailScreen(
                                viewModel = authViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onAuthSuccess = { isExistingAccount ->
                                    if (isExistingAccount) {
                                        if (profile != null && !profile?.pinCode.isNullOrBlank()) {
                                            navController.navigate(Screen.PinLogin.route) {
                                                popUpTo(Screen.Welcome.route) { inclusive = true }
                                            }
                                        } else {
                                            viewModel.unlockAppDirectly()
                                            navController.navigate(Screen.Dashboard.route) {
                                                popUpTo(Screen.Welcome.route) { inclusive = true }
                                            }
                                        }
                                    } else {
                                        navController.navigate(Screen.PinSetup.route) {
                                            popUpTo(Screen.Welcome.route) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }


                        // C. PIN Setup / Onboarding Configuration Route
                        composable(route = Screen.PinSetup.route) {
                            PinSetupScreen(
                                viewModel = viewModel,
                                onSetupComplete = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Welcome.route) { inclusive = true }
                                        popUpTo(Screen.PinSetup.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // D. PIN Login Route
                        composable(route = Screen.PinLogin.route) {
                            PinLoginScreen(
                                viewModel = viewModel,
                                onUnlockSuccess = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.PinLogin.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // E. Main Dashboard Control Hub Route
                        composable(route = Screen.Dashboard.route) {
                            DashboardScreen(
                                viewModel = viewModel,
                                dashboardViewModel = dashboardViewModel,
                                onNavigate = { route -> navController.navigate(route) },
                                onLockRequested = {
                                    navController.navigate(Screen.PinLogin.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // F. Rooms Directory Route
                        composable(route = Screen.Rooms.route) {
                            RoomListScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onAddRoomClick = { navController.navigate(Screen.AddRoom.route) },
                                onRoomDetailsClick = { id -> navController.navigate(Screen.RoomDetails.createRoute(id)) },
                                onEditRoomClick = { id -> navController.navigate(Screen.EditRoom.createRoute(id)) }
                            )
                        }

                        composable(route = Screen.AddRoom.route) {
                            com.example.features.rooms.ui.AddRoomScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.RoomDetails.route) {
                            com.example.features.rooms.ui.RoomDetailsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onEditRoomClick = { id -> navController.navigate(Screen.EditRoom.createRoute(id)) }
                            )
                        }

                        composable(route = Screen.EditRoom.route) {
                            com.example.features.rooms.ui.EditRoomScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // G. Tenants Directory Route
                        composable(route = Screen.Tenants.route) {
                            TenantsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onTenantClick = { id ->
                                    navController.navigate(Screen.TenantDetails.createRoute(id))
                                },
                                onAddTenantClick = {
                                    navController.navigate(Screen.AddTenant.route)
                                }
                            )
                        }

                        // G.1 Add Tenant Route
                        composable(route = Screen.AddTenant.route) {
                            AddTenantScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // G.2 Edit Tenant Route
                        composable(route = Screen.EditTenant.route) {
                            EditTenantScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // G.3 Tenant Details Route
                        composable(route = Screen.TenantDetails.route) {
                            TenantDetailsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onEditClick = { id ->
                                    navController.navigate(Screen.EditTenant.createRoute(id))
                                }
                            )
                        }

                        // H. Rent Ledger Route
                        composable(route = Screen.Rent.route) {
                            com.example.features.rent.ui.screens.RentDashboardScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToLedger = { navController.navigate(Screen.RentLedger.route) },
                                onNavigateToRecordPayment = { navController.navigate(Screen.RecordPayment.route) }
                            )
                        }

                        composable(route = Screen.RentLedger.route) {
                            com.example.features.rent.ui.screens.RentLedgerScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPaymentDetails = { id -> 
                                    navController.navigate(Screen.EditPayment.createRoute(id)) 
                                }
                            )
                        }

                        composable(route = Screen.RecordPayment.route) {
                            com.example.features.rent.ui.screens.PaymentScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.EditPayment.route) {
                            com.example.features.rent.ui.screens.PaymentScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.Receipt.route) {
                            com.example.features.rent.ui.screens.ReceiptScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // I. Expenses Ledger Route
                        composable(route = Screen.Expenses.route) {
                            ExpensesScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onExpenseClick = { id -> navController.navigate(Screen.ExpenseDetails.createRoute(id)) },
                                onAddExpenseClick = { navController.navigate(Screen.AddExpense.route) }
                            )
                        }

                        composable(route = Screen.ExpenseDetails.route) {
                            ExpenseDetailsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onEditClick = { id -> navController.navigate(Screen.EditExpense.createRoute(id)) }
                            )
                        }

                        composable(route = Screen.AddExpense.route) {
                            AddExpenseScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.EditExpense.route) {
                            EditExpenseScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // J. Reports Analytics Route
                        composable(route = Screen.Reports.route) {
                            FinancialDashboardScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onNavigateToRevenue = { navController.navigate(Screen.ReportsRevenue.route) },
                                onNavigateToExpense = { navController.navigate(Screen.ReportsExpense.route) },
                                onNavigateToOccupancy = { navController.navigate(Screen.ReportsOccupancy.route) },
                                onNavigateToRent = { navController.navigate(Screen.ReportsRent.route) }
                            )
                        }

                        composable(route = Screen.ReportsRevenue.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            RevenueReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.ReportsExpense.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            ExpenseReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.ReportsRent.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            RentAnalyticsScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.ReportsOccupancy.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            OccupancyReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // K. Profile & Settings Routes
                        composable(route = Screen.SecuritySettings.route) {
                            com.example.features.settings.ui.SecuritySettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.AppSettings.route) {
                            com.example.features.settings.ui.AppPreferencesScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.NotificationSettings.route) {
                            com.example.features.settings.ui.NotificationSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(route = Screen.AboutSettings.route) {
                            com.example.features.settings.ui.AboutScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToLicenses = { navController.navigate("licenses") }
                            )
                        }
                        composable(route = "licenses") {
                            com.example.features.settings.ui.LicensesScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(route = Screen.BackupSettings.route) {
                            com.example.features.backup.ui.BackupSettingsScreen(
                                viewModel = hiltViewModel(),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(route = Screen.SyncSettings.route) {
                            com.example.features.settings.ui.SyncSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(route = Screen.BusinessSettings.route) {
                            com.example.features.settings.ui.BusinessSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.SettingsHome.route) {
                            com.example.features.settings.ui.SettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() },
                                onNavigateToBusiness = { navController.navigate(Screen.BusinessSettings.route) },
                                onNavigateToApp = { navController.navigate(Screen.AppSettings.route) },
                                onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                                onNavigateToNotifications = { navController.navigate(Screen.NotificationSettings.route) },
                                onNavigateToBackup = { navController.navigate(Screen.BackupSettings.route) },
                                onNavigateToSync = { navController.navigate(Screen.SyncSettings.route) },
                                onNavigateToAbout = { navController.navigate(Screen.AboutSettings.route) }
                            )
                        }

                        composable(route = Screen.Profile.route) {
                            ProfileScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate(Screen.Splash.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }



                    }
                }
            }
        }
    }
}
