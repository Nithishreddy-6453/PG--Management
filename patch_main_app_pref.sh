sed -i 's/onNavigateToApp = { \/\* TODO \*\/ },/onNavigateToApp = { navController.navigate(Screen.AppSettings.route) },/g' app/src/main/java/com/example/MainActivity.kt

sed -i '/composable(route = Screen.BusinessSettings.route) {/i \
                        composable(route = Screen.AppSettings.route) {\
                            com.example.features.settings.ui.AppPreferencesScreen(\
                                viewModel = hiltViewModel(),\
                                onBackClick = { navController.popBackStack() }\
                            )\
                        }\
' app/src/main/java/com/example/MainActivity.kt
