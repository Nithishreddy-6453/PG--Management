import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Fix Splash screen
bad_splash = """                                }
                            )
                        }
                        )
                        }
                        composable"""

good_splash = """                                }
                            )
                        }
                        // B. Welcome Screen Onboarding Route
                        composable"""

content = content.replace(bad_splash, good_splash)

# Fix Profile screen
bad_profile = """                        composable(route = Screen.Profile.route) {
                            ProfileScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        )
                        }"""

good_profile = """                        composable(route = Screen.Profile.route) {
                            ProfileScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }"""

content = content.replace(bad_profile, good_profile)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
