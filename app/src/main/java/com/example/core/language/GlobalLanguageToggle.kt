package com.example.core.language

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standardized Global Language Toggle Pill [ EN | తెలుగు ]
 * Used consistently across all screen headers.
 */
@Composable
fun GlobalLanguageToggle(
    modifier: Modifier = Modifier,
    selectedLanguageOverride: String? = null,
    onLanguageSelectOverride: ((String) -> Unit)? = null
) {
    val globalLanguage by AppLanguageManager.languageFlow.collectAsState()
    val currentLang = selectedLanguageOverride ?: globalLanguage
    val onSelect = onLanguageSelectOverride ?: { AppLanguageManager.setLanguage(it) }

    val isEn = currentLang == "EN"
    val isTe = currentLang == "తెలుగు"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF1F5F9))
            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(20.dp))
            .padding(2.5.dp)
            .testTag("global_language_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // EN Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isEn) Color(0xFF2563EB) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect("EN") }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    fontSize = 11.5.sp,
                    fontWeight = if (isEn) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isEn) Color.White else Color(0xFF64748B)
                )
            }

            // Telugu Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTe) Color(0xFF2563EB) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect("తెలుగు") }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "తెలుగు",
                    fontSize = 11.5.sp,
                    fontWeight = if (isTe) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isTe) Color.White else Color(0xFF64748B)
                )
            }
        }
    }
}
