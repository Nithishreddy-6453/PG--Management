package com.example.features.tenants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(number: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF0866FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)))
        }
    }
}

@Composable
fun DocUploadCard(
    title: String,
    subtitle: String,
    isAttached: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isAttached) Color(0xFF10B981) else Color(0xFFCBD5E1)),
        colors = CardDefaults.outlinedCardColors(containerColor = if (isAttached) Color(0xFFECFDF5) else Color(0xFFFAFAFA)),
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isAttached) Color(0xFF10B981) else Color(0xFF0866FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAttached) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF64748B)),
                maxLines = 1
            )
        }
    }
}
