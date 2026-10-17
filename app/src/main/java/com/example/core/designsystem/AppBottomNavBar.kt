package com.example.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
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
    HOME, ROOMS, PAYMENTS, TENANTS, EXPENSES;

    companion object {
        val RENT = PAYMENTS
    }
}

/**
 * Standardized Material 3 Global Bottom Navigation Bar matching the approved UI reference.
 *
 * 5-tab fixed arrangement:
 * 1. Home
 * 2. Rooms
 * 3. Rent (Center prominent blue circle with Rupee icon)
 * 4. Tenants
 * 5. Expenses
 *
 * Supports Light/Dark themes, Telugu/English bilingual labels,
 * 48dp touch targets, clear active pill indicators, and edge-to-edge safe navigation insets.
 */
@Composable
fun AppBottomNavBar(
    currentTab: MainTab,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLang by AppLanguageManager.languageFlow.collectAsState()
    val isTe = currentLang == "తెలుగు"
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDark) 4.dp else 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("app_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
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

            // 3. Rent (Center Prominent Blue Circular Action)
            CenterRentNavItem(
                label = if (isTe) "అద్దె" else "Rent",
                isSelected = currentTab == MainTab.PAYMENTS,
                testTag = "nav_tab_payments",
                onClick = {
                    if (currentTab != MainTab.PAYMENTS) {
                        onNavigate(Screen.RentLedger.route)
                    }
                }
            )

            // 4. Tenants
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

/**
 * Standard nav item with active light-blue pill container when selected.
 */
@Composable
private fun BottomNavItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryBlue = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val selectedContainer = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFFE8F0FF)
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (isSelected) selectedContainer else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected) primaryBlue else inactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                color = if (isSelected) primaryBlue else inactiveColor,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Center prominent Rent navigation button featuring a vivid blue circular background and white Rupee symbol.
 */
@Composable
private fun CenterRentNavItem(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryBlue = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val labelColor = if (isSelected) primaryBlue else if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)

    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(primaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CurrencyRupee,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 11.sp,
                color = labelColor,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
