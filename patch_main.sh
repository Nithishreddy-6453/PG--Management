sed -i '/composable(route = Screen.Profile.route) {/i \
                        composable(route = Screen.SettingsHome.route) {\
                            com.example.features.settings.ui.SettingsScreen(\
                                viewModel = hiltViewModel(),\
                                onBackClick = { navController.popBackStack() },\
                                onNavigateToBusiness = { /* TODO */ },\
                                onNavigateToApp = { /* TODO */ },\
                                onNavigateToSecurity = { /* TODO */ },\
                                onNavigateToNotifications = { /* TODO */ },\
                                onNavigateToBackup = { /* TODO */ },\
                                onNavigateToAbout = { /* TODO */ }\
                            )\
                        }\
' app/src/main/java/com/example/MainActivity.kt
