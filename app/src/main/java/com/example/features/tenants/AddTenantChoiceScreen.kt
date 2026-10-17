package com.example.features.tenants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTenantChoiceScreen(
    onBackClick: () -> Unit,
    onUseGoogleFormClick: () -> Unit,
    onAddManuallyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = rememberTranslation("Back"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = rememberTranslation("Add Tenant"),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Sri Lakshmi Residency",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                    GlobalLanguageToggle()
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("add_tenant_choice_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Clipboard Illustration Box
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color(0xFFE8F0FE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF0866FF), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = rememberTranslation("How would you like to add the tenant?"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rememberTranslation("You can add tenant manually or use details submitted through Google Form."),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option 1: Use Google Form data
            Card(
                onClick = onUseGoogleFormClick,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7).copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("option_google_form")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = rememberTranslation("Use Google Form data"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rememberTranslation("Select from tenants who filled the Google Form"),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF475569))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF16A34A)
                    )
                }
            }

            // Option 2: Add Manually
            Card(
                onClick = onAddManuallyClick,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("option_add_manually")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFE8F0FE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF0866FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = rememberTranslation("Add manually"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rememberTranslation("Enter tenant details yourself"),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF0866FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Info Note Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF0866FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = rememberTranslation("Both options will create the same tenant record. You can review and edit all details before saving."),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E40AF), fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}
