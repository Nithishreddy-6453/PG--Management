package com.example.features.settings.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.settings.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onNavigateToBusiness: () -> Unit,
    onNavigateToApp: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSync: () -> Unit = {},
    onNavigateToExcelExport: () -> Unit = {},
    onNavigateToExcelImport: () -> Unit = {},
    onNavigateToAbout: () -> Unit,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val businessSettings by viewModel.businessSettings.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = rememberTranslation("Back"),
                                tint = Color(0xFF1E293B)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = rememberTranslation("Settings"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            )
                            Text(
                                text = rememberTranslation("Manage your app preferences"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B)
                                )
                            )
                        }
                    }

                    GlobalLanguageToggle()
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize().testTag("settings_screen_container")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. PG Details Section
            item {
                SettingsSectionGroup(
                    sectionTitle = rememberTranslation("PG Details"),
                    sectionIcon = Icons.Default.Business,
                    sectionIconTint = Color(0xFF2563EB)
                ) {
                    SettingsRow(
                        icon = Icons.Default.Business,
                        iconBgColor = Color(0xFFEBF3FE),
                        iconTint = Color(0xFF2563EB),
                        title = rememberTranslation("Business Settings"),
                        subtitle = businessSettings.pgName.ifEmpty { rememberTranslation("Setup your PG details") },
                        onClick = onNavigateToBusiness
                    )
                }
            }

            // 2. Preferences Section
            item {
                SettingsSectionGroup(
                    sectionTitle = rememberTranslation("Preferences"),
                    sectionIcon = Icons.Default.Person,
                    sectionIconTint = Color(0xFF059669)
                ) {
                    SettingsRow(
                        icon = Icons.Default.ColorLens,
                        iconBgColor = Color(0xFFF5F3FF),
                        iconTint = Color(0xFF7C3AED),
                        title = rememberTranslation("App Settings"),
                        subtitle = rememberTranslation("Theme, Language, Currency"),
                        onClick = onNavigateToApp
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        iconBgColor = Color(0xFFFEF2F2),
                        iconTint = Color(0xFFDC2626),
                        title = rememberTranslation("Notifications"),
                        subtitle = rememberTranslation("Manage alerts and reminders"),
                        onClick = onNavigateToNotifications
                    )
                }
            }

            // 3. Security & Data Section
            item {
                SettingsSectionGroup(
                    sectionTitle = rememberTranslation("Security & Data"),
                    sectionIcon = Icons.Default.Security,
                    sectionIconTint = Color(0xFFD97706)
                ) {
                    SettingsRow(
                        icon = Icons.Default.Security,
                        iconBgColor = Color(0xFFFEF3C7),
                        iconTint = Color(0xFFD97706),
                        title = rememberTranslation("Security"),
                        subtitle = rememberTranslation("App lock and biometrics"),
                        onClick = onNavigateToSecurity
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    SettingsRow(
                        icon = Icons.Default.Backup,
                        iconBgColor = Color(0xFFECFDF5),
                        iconTint = Color(0xFF059669),
                        title = rememberTranslation("Backup & Restore"),
                        subtitle = rememberTranslation("Secure your data"),
                        onClick = onNavigateToBackup
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    SettingsRow(
                        icon = Icons.Default.Sync,
                        iconBgColor = Color(0xFFF5F3FF),
                        iconTint = Color(0xFF7C3AED),
                        title = rememberTranslation("Multi-Device Cloud Sync"),
                        subtitle = rememberTranslation("Real-time sync, device identity & conflicts"),
                        onClick = onNavigateToSync
                    )
                }
            }

            // 4. Tools & Data Section
            item {
                SettingsSectionGroup(
                    sectionTitle = rememberTranslation("Tools & Data"),
                    sectionIcon = Icons.Default.Build,
                    sectionIconTint = Color(0xFF2563EB)
                ) {
                    SettingsRow(
                        icon = Icons.Default.FileDownload,
                        iconBgColor = Color(0xFFEBF3FE),
                        iconTint = Color(0xFF2563EB),
                        title = rememberTranslation("Export to Excel"),
                        subtitle = rememberTranslation("Summary, tenants, payments, expenses & rooms"),
                        onClick = onNavigateToExcelExport
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        iconBgColor = Color(0xFFECFDF5),
                        iconTint = Color(0xFF059669),
                        title = rememberTranslation("Import from Excel"),
                        subtitle = rememberTranslation("Import existing tenants from spreadsheet"),
                        onClick = onNavigateToExcelImport
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconBgColor = Color(0xFFFEF2F2),
                        iconTint = Color(0xFFDC2626),
                        title = rememberTranslation("About"),
                        subtitle = rememberTranslation("App version and information"),
                        onClick = onNavigateToAbout
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionGroup(
    sectionTitle: String,
    sectionIcon: ImageVector,
    sectionIconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = sectionIcon,
                contentDescription = null,
                tint = sectionIconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = sectionIconTint
                )
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
    }
}
