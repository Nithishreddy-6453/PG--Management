import os

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_1 = '''                        composable(route = Screen.ReportsRevenue.route) {
                            RevenueReportScreen(
                                viewModel = hiltViewModel(navController.getBackStackEntry(Screen.Reports.route)),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

new_1 = '''                        composable(route = Screen.ReportsRevenue.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            RevenueReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

old_2 = '''                        composable(route = Screen.ReportsExpense.route) {
                            ExpenseReportScreen(
                                viewModel = hiltViewModel(navController.getBackStackEntry(Screen.Reports.route)),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

new_2 = '''                        composable(route = Screen.ReportsExpense.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            ExpenseReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

old_3 = '''                        composable(route = Screen.ReportsRent.route) {
                            RentAnalyticsScreen(
                                viewModel = hiltViewModel(navController.getBackStackEntry(Screen.Reports.route)),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

new_3 = '''                        composable(route = Screen.ReportsRent.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            RentAnalyticsScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

old_4 = '''                        composable(route = Screen.ReportsOccupancy.route) {
                            OccupancyReportScreen(
                                viewModel = hiltViewModel(navController.getBackStackEntry(Screen.Reports.route)),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

new_4 = '''                        composable(route = Screen.ReportsOccupancy.route) { entry ->
                            val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Reports.route) }
                            OccupancyReportScreen(
                                viewModel = hiltViewModel(parentEntry),
                                onBackClick = { navController.popBackStack() }
                            )
                        }'''

content = content.replace(old_1, new_1)
content = content.replace(old_2, new_2)
content = content.replace(old_3, new_3)
content = content.replace(old_4, new_4)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
