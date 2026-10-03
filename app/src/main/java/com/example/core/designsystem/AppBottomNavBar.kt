package com.example.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.AppLanguageManager
import com.example.navigation.Screen

enum class MainTab {
    HOME, ROOMS, TENANTS, PAYMENTS, EXPENSES
}

@Composable
fun AppBottomNavBar(
    currentTab: MainTab,
    onNavigate: (String) -> Unit
) {
    val currentLang by AppLanguageManager.languageFlow.collectAsState()
    val isTe = currentLang == "తెలుగు"

    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEDF2F7)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            BottomNavItem(
                label = if (isTe) "హోమ్" else "Home",
                icon = Icons.Default.Home,
                isSelected = currentTab == MainTab.HOME,
                onClick = {
                    if (currentTab != MainTab.HOME) {
                        onNavigate(Screen.Dashboard.route)
                    }
                }
            )

            // 2. Rooms
            BottomNavItem(
                label = if (isTe) "గదులు" else "Rooms",
                icon = Icons.Default.MeetingRoom,
                isSelected = currentTab == MainTab.ROOMS,
                onClick = {
                    if (currentTab != MainTab.ROOMS) {
                        onNavigate(Screen.Rooms.route)
                    }
                }
            )

            // 3. Tenants
            BottomNavItem(
                label = if (isTe) "అద్దెదారులు" else "Tenants",
                icon = Icons.Default.Group,
                isSelected = currentTab == MainTab.TENANTS,
                onClick = {
                    if (currentTab != MainTab.TENANTS) {
                        onNavigate(Screen.Tenants.route)
                    }
                }
            )

            // 4. Payments (Rent Ledger)
            BottomNavItem(
                label = if (isTe) "చెల్లింపులు" else "Payments",
                icon = Icons.Default.AttachMoney,
                isSelected = currentTab == MainTab.PAYMENTS,
                onClick = {
                    if (currentTab != MainTab.PAYMENTS) {
                        onNavigate(Screen.RentLedger.route)
                    }
                }
            )

            // 5. Expenses
            BottomNavItem(
                label = if (isTe) "ఖర్చులు" else "Expenses",
                icon = Icons.Default.ReceiptLong,
                isSelected = currentTab == MainTab.EXPENSES,
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
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFEBF3FE) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                )
            )
        }
    }
}
