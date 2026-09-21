sed -i 's/onNavigateToSecurity = { \/\* TODO \*\/ },/onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },/g' app/src/main/java/com/example/MainActivity.kt

sed -i '/composable(route = Screen.AppSettings.route) {/i \
                        composable(route = Screen.SecuritySettings.route) {\
                            com.example.features.settings.ui.SecuritySettingsScreen(\
                                viewModel = hiltViewModel(),\
                                onBackClick = { navController.popBackStack() }\
                            )\
                        }\
' app/src/main/java/com/example/MainActivity.kt
