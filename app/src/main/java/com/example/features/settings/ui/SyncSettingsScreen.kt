package com.example.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.ConflictRecordEntity
import com.example.data.sync.ConflictResolutionStrategy
import com.example.features.settings.ui.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: SyncViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val diagnostics by viewModel.diagnostics.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Device Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("sync_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !diagnostics.isSyncing,
                        modifier = Modifier.testTag("sync_now_top_button")
                    ) {
                        if (diagnostics.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("sync_screen_container")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // 1. Device & Account Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("device_identity_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                text = "Device & Account Identity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = "Account: ${viewModel.getUserEmail()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(spacing.extraSmall))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Device ID: ${viewModel.getDeviceId()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(viewModel.getDeviceId()))
                                },
                                modifier = Modifier.size(28.dp).testTag("copy_device_id_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Device ID", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // 2. Synchronization Status & Diagnostics
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("sync_status_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (diagnostics.isSyncing) Icons.Default.Sync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (diagnostics.isSyncing) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (diagnostics.isSyncing) "Syncing with Cloud..." else "Up to date",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (diagnostics.lastSyncedAt > 0L) {
                                        "Last synced: ${dateFormat.format(Date(diagnostics.lastSyncedAt))}"
                                    } else {
                                        "Never synced"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.syncNow() },
                                enabled = !diagnostics.isSyncing,
                                modifier = Modifier.testTag("manual_sync_button")
                            ) {
                                Text("Sync Now")
                            }
                        }

                        if (diagnostics.lastError != null) {
                            Spacer(modifier = Modifier.height(spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Error: ${diagnostics.lastError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(spacing.small)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(spacing.small))

                        // Stats counters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem(
                                label = "Pending",
                                count = diagnostics.pendingCount,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatItem(
                                label = "Failed",
                                count = diagnostics.failedCount,
                                color = if (diagnostics.failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                            StatItem(
                                label = "Conflicts",
                                count = diagnostics.conflictCount,
                                color = if (diagnostics.conflictCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // 3. Active Conflicts Section
            item {
                Text(
                    text = "Conflict Resolution (${conflicts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (conflicts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
            }

            if (conflicts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("no_conflicts_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(spacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.medium))
                            Text(
                                text = "No active conflicts. All multi-device changes are cleanly synchronized.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                items(conflicts, key = { it.id }) { conflict ->
                    ConflictCard(
                        conflict = conflict,
                        onKeepMine = { viewModel.resolveConflict(conflict.id, ConflictResolutionStrategy.KEEP_LOCAL) },
                        onAcceptRemote = { viewModel.resolveConflict(conflict.id, ConflictResolutionStrategy.KEEP_REMOTE) }
                    )
                }
            }

            // 4. Architecture Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                text = "How Multi-Device Sync Works",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = "• Room database is your fast local source of truth on each device.\n" +
                                "• Changes are synced to Firestore cloud storage.\n" +
                                "• Firestore snapshot listeners push updates to other devices in real-time.\n" +
                                "• If concurrent offline edits happen on multiple phones, conflict records are safely preserved without losing data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConflictCard(
    conflict: ConflictRecordEntity,
    onKeepMine: () -> Unit,
    onAcceptRemote: () -> Unit
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag("conflict_card_${conflict.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text = "Conflict: ${conflict.entityType} #${conflict.entityId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Concurrent edits detected between this device and another device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(spacing.small))

            // Versions comparison
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(spacing.small)) {
                    Text(
                        text = "This Device (v${conflict.localVersion}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = conflict.localDataJson.ifBlank { "Local version" },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        text = "Remote Device [${conflict.deviceB.take(8)}] (v${conflict.remoteVersion}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = conflict.remoteDataJson.ifBlank { "Remote version" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                OutlinedButton(
                    onClick = onKeepMine,
                    modifier = Modifier.weight(1f).testTag("keep_local_button_${conflict.id}")
                ) {
                    Text("Keep Mine")
                }
                Button(
                    onClick = onAcceptRemote,
                    modifier = Modifier.weight(1f).testTag("accept_remote_button_${conflict.id}")
                ) {
                    Text("Accept Remote")
                }
            }
        }
    }
}
