package com.example.features.properties.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.PropertyEntity

/**
 * Switch Property Bottom Sheet matching the high-fidelity UI specifications:
 *
 * 1. Transparent Background:
 *    - The background screen remains completely visible behind the sheet.
 *    - Dimmed by a translucent dark overlay (scrimColor = Color.Black.copy(alpha = 0.55f)).
 *    - The sheet itself has an opaque solid surface (White in Light mode, Dark Slate in Dark mode).
 *
 * 2. Visual Hierarchy:
 *    - Rounded top corners (~24dp)
 *    - Centered top drag handle
 *    - Header: Close icon on left, Title "Your Properties", Subtitle "Switch between your PGs", "+ Add PG" action on right
 *    - Property Search: Total count ("X properties"), rounded search input with search icon, instant search filter
 *    - Property List: Scrollable list of rounded property cards with:
 *        * Property icon in light-blue container
 *        * Property name & location
 *        * "Current property" pill badge
 *        * Filled blue check circle for active property
 *        * Overflow menu for property actions
 *        * Blue border on active property
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertySelectorBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: List<PropertyEntity>,
    currentProperty: PropertyEntity?,
    onSelectProperty: (PropertyEntity) -> Unit,
    onAddNewProperty: () -> Unit,
    onDismiss: () -> Unit,
    onEditProperty: ((PropertyEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // CRITICAL: Explicitly configure containerColor and scrimColor so the underlying screen
        // is visible and dimmed through a translucent dark overlay, without any solid white replacement.
        containerColor = if (isDark) Color(0xFF1E293B) else Color.White,
        contentColor = if (isDark) Color.White else Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
        dragHandle = null, // Custom drag handle rendered in content
        modifier = modifier.testTag("property_selector_sheet")
    ) {
        PropertySelectorSheetContent(
            properties = properties,
            currentProperty = currentProperty,
            onSelectProperty = onSelectProperty,
            onAddNewProperty = onAddNewProperty,
            onDismiss = onDismiss,
            onEditProperty = onEditProperty,
            isDark = isDark
        )
    }
}

@Composable
fun PropertySelectorSheetContent(
    properties: List<PropertyEntity>,
    currentProperty: PropertyEntity?,
    onSelectProperty: (PropertyEntity) -> Unit,
    onAddNewProperty: () -> Unit,
    onDismiss: () -> Unit,
    onEditProperty: ((PropertyEntity) -> Unit)? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredProperties = remember(properties, searchQuery) {
        if (searchQuery.isBlank()) {
            properties
        } else {
            val q = searchQuery.trim().lowercase()
            properties.filter { prop ->
                prop.propertyName.lowercase().contains(q) ||
                prop.address.lowercase().contains(q) ||
                prop.city.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // 1. Centered drag handle
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 8.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1))
                .align(Alignment.CenterHorizontally)
                .testTag("sheet_drag_handle")
        )

        // 2. Header: Close Button | Title + Subtitle | + Add PG
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("close_property_sheet_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Your Properties",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("property_sheet_title")
                )
                Text(
                    text = "Switch between your PGs",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp
                    ),
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onAddNewProperty()
                },
                modifier = Modifier.testTag("add_property_action_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add PG",
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Properties Count Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalCount = properties.size
            val countText = if (totalCount == 1) "1 property" else "$totalCount properties"
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                ),
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                modifier = Modifier.testTag("properties_count_text")
            )
        }

        // 4. Rounded Search Field
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search by property name or location...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp
                            ),
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFF2563EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("property_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Scrollable Properties List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .testTag("properties_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredProperties, key = { it.propertyId }) { property ->
                val isSelected = property.propertyId == currentProperty?.propertyId
                PropertyCard(
                    property = property,
                    isSelected = isSelected,
                    onClick = {
                        onSelectProperty(property)
                        onDismiss()
                    },
                    onEditProperty = onEditProperty,
                    isDark = isDark
                )
            }

            if (filteredProperties.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No properties found for \"$searchQuery\"" else "No properties found",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Check the spelling or clear the search" else "Add your first PG to start managing tenants",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        onAddNewProperty()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add New PG", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Property Card matching the reference design:
 * - Property icon in light-blue container
 * - Property name & location
 * - "Current property" indicator for the active property
 * - Filled blue check circle for selected property
 * - Overflow menu for property actions
 * - Blue border and visual emphasis on selected property
 */
@Composable
fun PropertyCard(
    property: PropertyEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditProperty: ((PropertyEntity) -> Unit)? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("property_card_${property.propertyId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isDark -> Color(0xFF1E293B)
                isSelected && !isDark -> Color(0xFFF0F7FF)
                isDark -> Color(0xFF0F172A)
                else -> Color.White
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, Color(0xFF2563EB))
        } else {
            BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Property icon in a light-blue icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isSelected && isDark -> Color(0xFF2563EB).copy(alpha = 0.25f)
                            isSelected && !isDark -> Color(0xFFDBEAFE)
                            isDark -> Color(0xFF1E293B)
                            else -> Color(0xFFEFF6FF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apartment,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Property Name, Location, and Current Property Badge
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = property.propertyName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        ),
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("current_property_indicator")
                        ) {
                            Text(
                                text = "Current property",
                                color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val location = listOf(property.address, property.city)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { "Hyderabad" }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.5.dp),
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Filled Blue Check Circle for active/selected property
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("property_check_${property.propertyId}")
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 4. Overflow Menu for property actions
            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("property_overflow_${property.propertyId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Property options",
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("Switch to this PG", fontSize = 13.5.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color(0xFF2563EB)
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onClick()
                        }
                    )
                    if (onEditProperty != null) {
                        DropdownMenuItem(
                            text = { Text("Edit Property", fontSize = 13.5.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onEditProperty(property)
                            }
                        )
                    }
                }
            }
        }
    }
}
