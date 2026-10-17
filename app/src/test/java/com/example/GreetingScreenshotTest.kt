package com.example

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.PgTheme
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.ExpensesStats
import com.example.features.dashboard.domain.usecase.OccupancyStats
import com.example.features.dashboard.domain.usecase.ProfitStats
import com.example.features.dashboard.domain.usecase.RecentActivity
import com.example.features.dashboard.domain.usecase.RevenueStats
import com.example.features.dashboard.ui.DashboardContent
import com.example.features.reports.domain.model.AttentionItem
import com.example.features.reports.domain.model.AttentionType
import com.example.features.startup.WelcomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun welcome_screen_screenshot() {
    composeTestRule.setContent { 
      PgTheme { 
        WelcomeScreen(
          onContinueWithEmail = {},
          onGoogleSignInSuccess = {}
        ) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/welcome_screen.png")
  }

  @Test
  fun home_screen_screenshot() {
    val sampleSummary = DashboardSummary(
      occupancy = OccupancyStats(
        totalRooms = 10,
        occupiedRooms = 6,
        vacantRooms = 4,
        totalBeds = 19,
        occupiedBeds = 11,
        vacantBeds = 8,
        activeTenantsCount = 11,
        totalPgCapacity = 19,
        homeOccupancyPercentage = 57.9,
        vacanciesCount = 8
      ),
      revenue = RevenueStats(
        monthlyRevenue = 28952.0,
        pendingRent = 38468.0,
        totalExpected = 67420.0,
        previousDuesReceived = 6000.0,
        billingMonth = "October 2026"
      ),
      expenses = ExpensesStats(
        totalExpenses = 25500.0
      ),
      profit = ProfitStats(
        netProfit = 9452.0
      ),
      whatNeedsAttention = listOf(
        AttentionItem(
          type = AttentionType.RENT_REMAINING,
          title = "7 tenants have rent remaining",
          subtitle = "Arun (₹6,452) and 6 others",
          severity = "WARNING"
        ),
        AttentionItem(
          type = AttentionType.VACANCY,
          title = "8 vacancies",
          subtitle = "Room 111 (3 beds available)",
          severity = "INFO"
        )
      ),
      recentActivities = listOf(
        RecentActivity(
          id = "expense_1",
          date = "2026-01-03",
          type = ActivityType.EXPENSE_ADDED,
          param1 = "Staff Salary",
          amount = 10000.0
        ),
        RecentActivity(
          id = "expense_2",
          date = "2026-01-03",
          type = ActivityType.EXPENSE_ADDED,
          param1 = "Water",
          amount = 5000.0
        )
      )
    )

    composeTestRule.setContent {
      PgTheme {
        DashboardContent(
          ownerName = "karan",
          greeting = "Good morning,",
          selectedLanguage = "EN",
          onLanguageSelect = {},
          summary = sampleSummary,
          onNavigate = {},
          activePgName = "Emerald Stays",
          pgLocation = "Hyderabad",
          onSwitchProperty = {},
          activeBillingMonth = "October 2026",
          onSelectBillingMonth = {},
          innerPadding = PaddingValues(top = 24.dp, bottom = 80.dp)
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home_screen_repaired.png")
  }
}
