import os

content = """package com.example.features.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.RecentActivity
import com.example.features.dashboard.domain.usecase.OccupancyStats
import com.example.navigation.Screen
import com.example.ui.viewmodel.PgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PgViewModel,
    dashboardViewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    onLockRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val uiState by dashboardViewModel.uiState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val pgName = profile?.pgName ?: "Emerald Stays"
    val ownerName = profile?.ownerName ?: "Nithish Prasad"
    val currentDate = remember {
        SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pgName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = currentDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onNavigate(Screen.Settings.route) },
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onLockRequested,
                        modifier = Modifier.testTag("dashboard_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("dashboard_top_bar")
            )
        },
        modifier = modifier.testTag("dashboard_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(targetState = uiState, label = "DashboardStateAnimation") { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        DashboardSkeletonLoading()
                    }
                    is DashboardUiState.Empty -> {
                        DashboardEmptyRoomsState(
                            onAddRoomClick = { onNavigate(Screen.Rooms.route) }
                        )
                    }
                    is DashboardUiState.Error -> {
                        DashboardErrorState(
                            errorMessage = state.message,
                            onRetryClick = { dashboardViewModel.handleEvent(DashboardUiEvent.Retry) }
                        )
                    }
                    is DashboardUiState.Success -> {
                        val summary = state.data
                        DashboardContent(
                            ownerName = ownerName,
                            summary = summary,
                            onNavigate = onNavigate
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    ownerName: String,
    summary: DashboardSummary,
    onNavigate: (String) -> Unit
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.large)
            .testTag("dashboard_scroll_content"),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 1. Dynamic Greeting Header
        item {
            Spacer(modifier = Modifier.height(spacing.small))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Namaste, $ownerName",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.testTag("dashboard_welcome_text")
                )
                Text(
                    text = "Property Ledger & Operational Summary",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Spacer(modifier = Modifier.height(spacing.medium))
        }

        // 2. Command Center (Quick Actions)
        item {
            Text(
                text = "Command Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(spacing.small))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                QuickActionItem(
                    title = "Add Room",
                    icon = Icons.Default.AddHome,
                    color = Color(0xFF1A73E8),
                    bgColor = Color(0xFFE8F0FE),
                    onClick = { onNavigate(Screen.Rooms.route) }
                )
                QuickActionItem(
                    title = "Add Tenant",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF0D652D),
                    bgColor = Color(0xFFE6F4EA),
                    onClick = { onNavigate(Screen.Tenants.route) }
                )
                QuickActionItem(
                    title = "Collect Rent",
                    icon = Icons.Default.AttachMoney,
                    color = Color(0xFFE37400),
                    bgColor = Color(0xFFFEF7E0),
                    onClick = { onNavigate(Screen.RentLedger.route) }
                )
                QuickActionItem(
                    title = "Add Expense",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF9334E6),
                    bgColor = Color(0xFFF3E8FD),
                    onClick = { onNavigate(Screen.Expenses.route) }
                )
            }
        }

        // 3. KPIs
        item {
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Key Performance Indicators",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(spacing.small))
            
            OccupancyHeroCard(stats = summary.occupancy)
            
            Spacer(modifier = Modifier.height(spacing.small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                DashboardKpiCard(
                    title = "Monthly Revenue",
                    value = "₹${String.format("%,.0f", summary.revenue.monthlyRevenue)}",
                    subtitle = "Collected rent cycles",
                    icon = Icons.Default.AttachMoney,
                    accentColor = Color(0xFF0D652D),
                    modifier = Modifier.weight(1f)
                )
                DashboardKpiCard(
                    title = "Pending Rent",
                    value = "₹${String.format("%,.0f", summary.revenue.pendingRent)}",
                    subtitle = "Outstanding dues",
                    icon = Icons.Default.Warning,
                    accentColor = Color(0xFFD93025),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(spacing.small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                DashboardKpiCard(
                    title = "Monthly Expenses",
                    value = "₹${String.format("%,.0f", summary.expenses.monthlyExpenses)}",
                    subtitle = "PG maintenance",
                    icon = Icons.Default.Analytics,
                    accentColor = Color(0xFF9334E6),
                    modifier = Modifier.weight(1f)
                )
                DashboardKpiCard(
                    title = "Net Profit",
                    value = "₹${String.format("%,.0f", summary.profit.netProfit)}",
                    subtitle = "Operations flow",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = Color(0xFF0D652D),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Recent Activity Feed Header
        item {
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Operational Feed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { }
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View All",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.small))
        }

        // 5. Recent Activity List
        items(summary.recentActivities) { activity ->
            ActivityFeedItem(activity = activity)
        }

        item {
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.size(110.dp, 120.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(bgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mini trend chart mock
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val path = Path()
                path.moveTo(0f, size.height)
                path.cubicTo(
                    size.width * 0.25f, size.height,
                    size.width * 0.25f, size.height * 0.5f,
                    size.width * 0.5f, size.height * 0.5f
                )
                path.cubicTo(
                    size.width * 0.75f, size.height * 0.5f,
                    size.width * 0.75f, 0f,
                    size.width, 0f
                )
                
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.0f))
                    )
                )
                
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun OccupancyHeroCard(
    stats: OccupancyStats,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Occupancy Status",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${stats.occupiedRooms} / ${stats.totalRooms} Rooms Occupied",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${stats.occupiedBeds} of ${stats.totalBeds} total beds filled (${stats.bedOccupancyPercentage.toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFE8F0FE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val fillRatio = if (stats.totalBeds > 0) stats.occupiedBeds.toFloat() / stats.totalBeds else 0f
            LinearProgressIndicator(
                progress = { fillRatio },
                color = Color(0xFF1A73E8),
                trackColor = Color(0xFFF1F3F4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun ActivityFeedItem(
    activity: RecentActivity,
    modifier: Modifier = Modifier
) {
    val (icon, tint, bgColor) = when (activity.type) {
        ActivityType.TENANT_ADDED -> Triple(Icons.Default.PersonAdd, Color(0xFF1A73E8), Color(0xFFE8F0FE))
        ActivityType.RENT_PAID -> Triple(Icons.Default.AttachMoney, Color(0xFF0D652D), Color(0xFFE6F4EA))
        ActivityType.EXPENSE_ADDED -> Triple(Icons.Default.Receipt, Color(0xFFD93025), Color(0xFFFCE8E6))
        ActivityType.ROOM_VACATED -> Triple(Icons.Default.MeetingRoom, Color(0xFF5F6368), Color(0xFFF1F3F4))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(bgColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            // Date formatting
            val parts = activity.date.split(" ")
            val date = parts.getOrNull(0) ?: activity.date
            val time = parts.getOrNull(1) ?: ""
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSkeletonLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun DashboardEmptyRoomsState(onAddRoomClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AddHome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to your PG Ledger Hub!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddRoomClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add your first room")
        }
    }
}

@Composable
fun DashboardErrorState(errorMessage: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Operational Sync Failure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry Sync")
        }
    }
}
"""

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

