import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Delete dangling brackets at the very end
content = re.sub(r'                        \)\n                        \}\s*$', '', content)

content += """
                        // Settings Module
                        composable(route = Screen.SettingsHome.route) {
                            com.example.features.settings.ui.SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToBusiness = { navController.navigate(Screen.BusinessSettings.route) },
                                onNavigateToApp = { navController.navigate(Screen.AppSettings.route) },
                                onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                                onNavigateToNotifications = { navController.navigate(Screen.NotificationSettings.route) },
                                onNavigateToBackup = { navController.navigate(Screen.BackupSettings.route) },
                                onNavigateToAbout = { navController.navigate(Screen.AboutSettings.route) }
                            )
                        }
                        
                        composable(route = Screen.BusinessSettings.route) {
                            com.example.features.settings.ui.BusinessSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.AppSettings.route) {
                            com.example.features.settings.ui.AppSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.SecuritySettings.route) {
                            com.example.features.settings.ui.SecuritySettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.NotificationSettings.route) {
                            com.example.features.settings.ui.NotificationSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.BackupSettings.route) {
                            com.example.features.settings.ui.BackupScreen(
                                viewModel = hiltViewModel(),
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.AboutSettings.route) {
                            com.example.features.settings.ui.AboutScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
"""

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
