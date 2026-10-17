package com.example.features.settings.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(20.dp)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App identity
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.primaryContainer)
                    .border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apartment,
                    contentDescription = "PG Manager app icon",
                    modifier = Modifier.size(52.dp),
                    tint = colors.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "PG Manager",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Simple living. Smarter management.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = CircleShape,
                color = colors.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 7.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSecondaryContainer
                    )
                    Text(
                        text = "Version 1.0.0 (1)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App information
            SectionHeading(title = "APP INFORMATION")

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        colors.outlineVariant.copy(alpha = 0.65f),
                        cardShape
                    ),
                shape = cardShape,
                color = colors.surface,
                shadowElevation = 0.dp
            ) {
                Column {
                    InformationRow(
                        icon = Icons.Default.Code,
                        title = "Developer",
                        subtitle = "Google AI Studio"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.6f)
                    )

                    InformationRow(
                        icon = Icons.Default.Info,
                        title = "Copyright",
                        subtitle = "© 2026 PG Manager. All rights reserved."
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Resources and support
            SectionHeading(title = "LEGAL & SUPPORT")

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        colors.outlineVariant.copy(alpha = 0.65f),
                        cardShape
                    ),
                shape = cardShape,
                color = colors.surface,
                shadowElevation = 0.dp
            ) {
                Column {
                    NavigationRow(
                        icon = Icons.Default.Description,
                        title = "Open Source Licenses",
                        subtitle = "Libraries used in this app",
                        onClick = onNavigateToLicenses
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.6f)
                    )

                    NavigationRow(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy",
                        subtitle = "How your information is handled",
                        onClick = { /* Connect your privacy policy */ }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.6f)
                    )

                    NavigationRow(
                        icon = Icons.Default.Description,
                        title = "Terms & Conditions",
                        subtitle = "Terms of app usage",
                        onClick = { /* Connect your terms screen */ }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colors.outlineVariant.copy(alpha = 0.6f)
                    )

                    NavigationRow(
                        icon = Icons.Default.ContactSupport,
                        title = "Contact Support",
                        subtitle = "Get help with PG Manager",
                        onClick = { /* Connect your support action */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Footer
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Made with",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(5.dp))

                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.error
                )

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = "for better living",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "PG MANAGER  •  VERSION 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InformationRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = colors.primary
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = colors.primary
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open $title",
            modifier = Modifier.size(16.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}