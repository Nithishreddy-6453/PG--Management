sed -i 's/onNavigateToBusiness = { \/\* TODO \*\/ },/onNavigateToBusiness = { navController.navigate(Screen.BusinessSettings.route) },/g' app/src/main/java/com/example/MainActivity.kt
sed -i '/composable(route = Screen.SettingsHome.route) {/i \
                        composable(route = Screen.BusinessSettings.route) {\
                            com.example.features.settings.ui.BusinessSettingsScreen(\
                                viewModel = hiltViewModel(),\
                                onBackClick = { navController.popBackStack() }\
                            )\
                        }\
' app/src/main/java/com/example/MainActivity.kt
