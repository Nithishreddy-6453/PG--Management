package com.example.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.AppLanguageManager
import com.example.navigation.Screen

enum class MainTab {
    HOME, ROOMS, TENANTS, PAYMENTS, EXPENSES
}

/**
 * Standardized Material 3 Global Bottom Navigation Bar.
 *
 * Supports Light/Dark themes, Telugu/English bilingual labels,
 * 48dp touch targets, clear active pill indicators, and edge-to-edge safe insets.
 */
@Composable
fun AppBottomNavBar(
    currentTab: MainTab,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLang by AppLanguageManager.languageFlow.collectAsState()
    val isTe = currentLang == "తెలుగు"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("app_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            BottomNavItem(
                label = if (isTe) "హోమ్" else "Home",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                isSelected = currentTab == MainTab.HOME,
                testTag = "nav_tab_home",
                onClick = {
                    if (currentTab != MainTab.HOME) {
                        onNavigate(Screen.Dashboard.route)
                    }
                }
            )

            // 2. Rooms
            BottomNavItem(
                label = if (isTe) "గదులు" else "Rooms",
                selectedIcon = Icons.Filled.MeetingRoom,
                unselectedIcon = Icons.Outlined.MeetingRoom,
                isSelected = currentTab == MainTab.ROOMS,
                testTag = "nav_tab_rooms",
                onClick = {
                    if (currentTab != MainTab.ROOMS) {
                        onNavigate(Screen.Rooms.route)
                    }
                }
            )

            // 3. Tenants
            BottomNavItem(
                label = if (isTe) "అద్దెదారులు" else "Tenants",
                selectedIcon = Icons.Filled.Group,
                unselectedIcon = Icons.Outlined.Group,
                isSelected = currentTab == MainTab.TENANTS,
                testTag = "nav_tab_tenants",
                onClick = {
                    if (currentTab != MainTab.TENANTS) {
                        onNavigate(Screen.Tenants.route)
                    }
                }
            )

            // 4. Payments (Rent Ledger)
            BottomNavItem(
                label = if (isTe) "చెల్లింపులు" else "Payments",
                selectedIcon = Icons.Filled.Payments,
                unselectedIcon = Icons.Outlined.Payments,
                isSelected = currentTab == MainTab.PAYMENTS,
                testTag = "nav_tab_payments",
                onClick = {
                    if (currentTab != MainTab.PAYMENTS) {
                        onNavigate(Screen.RentLedger.route)
                    }
                }
            )

            // 5. Expenses
            BottomNavItem(
                label = if (isTe) "ఖర్చులు" else "Expenses",
                selectedIcon = Icons.Filled.ReceiptLong,
                unselectedIcon = Icons.Outlined.ReceiptLong,
                isSelected = currentTab == MainTab.EXPENSES,
                testTag = "nav_tab_expenses",
                onClick = {
                    if (currentTab != MainTab.EXPENSES) {
                        onNavigate(Screen.Expenses.route)
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) activeContainerColor else Color.Transparent)
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected) primaryColor else inactiveColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (isSelected) primaryColor else inactiveColor,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
