package com.example.features.rooms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation

/**
 * Single Canonical Room Form Composable used across the application for both
 * creating and editing rooms.
 */
@Composable
fun RoomFormContent(
    isEditMode: Boolean,
    roomNumber: String,
    onRoomNumberChange: (String) -> Unit,
    isRoomNumberEditable: Boolean,
    floor: String,
    onFloorChange: (String) -> Unit,
    capacity: String,
    onCapacityChange: (String) -> Unit,
    ratePerBed: String,
    onRatePerBedChange: (String) -> Unit,
    roomType: String,
    onRoomTypeChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    activeTenantsCount: Int = 0,
    isLoading: Boolean = false,
    isSaving: Boolean = false,
    error: String? = null,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Translations
    val titleText = if (isEditMode) {
        if (roomNumber.isNotBlank()) "Edit Room $roomNumber" else rememberTranslation("Edit Room")
    } else {
        rememberTranslation("Register Room")
    }

    val roomDetailsTitle = rememberTranslation("Room Details")
    val roomDetailsSubtitle = rememberTranslation("Basic information about the room")
    val roomNumberLabel = rememberTranslation("Room Number")
    val floorLabel = rememberTranslation("Floor")
    val capacityRentTitle = rememberTranslation("Capacity & Rent")
    val capacityRentSubtitle = rememberTranslation("Set how many beds and the monthly rent")
    val numBedsLabel = rememberTranslation("Number of Beds")
    val rentLabel = rememberTranslation("Monthly Rent Per Bed")
    val roomTypeTitle = rememberTranslation("Room Type")
    val roomTypeSubtitle = rememberTranslation("Select the type of room")
    val nonAcTitle = rememberTranslation("Non-AC")
    val nonAcSubtitle = rememberTranslation("No air conditioning")
    val acTitle = rememberTranslation("AC")
    val acSubtitle = rememberTranslation("With air conditioning")
    val additionalInfoTitle = rememberTranslation("Additional Information")
    val additionalInfoSubtitle = rememberTranslation("Add any notes (optional)")
    val cancelText = rememberTranslation("Cancel")
    val primaryButtonText = if (isEditMode) {
        rememberTranslation("Save Changes")
    } else {
        rememberTranslation("Register Room")
    }

    val floorOptions = listOf(
        "Ground Floor",
        "1st Floor",
        "2nd Floor",
        "3rd Floor",
        "4th Floor",
        "5th Floor",
        "6th Floor",
        "7th Floor",
        "8th Floor"
    )

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFFF8FAFC),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .testTag(if (isEditMode) "edit_room_top_bar" else "add_room_top_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color(0xFF0F172A)
                        )
                    }

                    Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            // FIXED BOTTOM ACTION BAR
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_room_button")
                    ) {
                        Text(
                            text = cancelText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Button(
                        onClick = onSaveClick,
                        enabled = !isSaving && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("save_room_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = primaryButtonText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (!isEditMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
            .fillMaxSize()
            .testTag(if (isEditMode) "edit_room_screen_container" else "add_room_screen_container")
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2563EB)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error Display Banner if any
                error?.let { err ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_banner")
                    ) {
                        Text(
                            text = err,
                            color = Color(0xFFDC2626),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // ==================================================
                // SECTION 1 — ROOM DETAILS
                // ==================================================
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Badge 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFDBEAFE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB)
                                )
                            }

                            Column {
                                Text(
                                    text = roomDetailsTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = roomDetailsSubtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Responsive Two-Column / Stacked Layout
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isNarrow = maxWidth < 340.dp
                            if (isNarrow) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalRoomNumberField(
                                        roomNumber = roomNumber,
                                        onRoomNumberChange = onRoomNumberChange,
                                        roomNumberLabel = roomNumberLabel,
                                        isEditable = isRoomNumberEditable,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    CanonicalFloorField(
                                        floor = floor,
                                        onFloorChange = onFloorChange,
                                        floorLabel = floorLabel,
                                        floorOptions = floorOptions,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalRoomNumberField(
                                        roomNumber = roomNumber,
                                        onRoomNumberChange = onRoomNumberChange,
                                        roomNumberLabel = roomNumberLabel,
                                        isEditable = isRoomNumberEditable,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CanonicalFloorField(
                                        floor = floor,
                                        onFloorChange = onFloorChange,
                                        floorLabel = floorLabel,
                                        floorOptions = floorOptions,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================================================
                // SECTION 2 — CAPACITY & RENT
                // ==================================================
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Badge 2
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFDCFCE7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "2",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }

                            Column {
                                Text(
                                    text = capacityRentTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = capacityRentSubtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Responsive Two-Column / Stacked Layout
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isNarrow = maxWidth < 340.dp
                            if (isNarrow) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalCapacityField(
                                        capacity = capacity,
                                        onCapacityChange = onCapacityChange,
                                        numBedsLabel = numBedsLabel,
                                        minCapacity = if (activeTenantsCount > 0) activeTenantsCount else 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    CanonicalRentField(
                                        ratePerBed = ratePerBed,
                                        onRatePerBedChange = onRatePerBedChange,
                                        rentLabel = rentLabel,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalCapacityField(
                                        capacity = capacity,
                                        onCapacityChange = onCapacityChange,
                                        numBedsLabel = numBedsLabel,
                                        minCapacity = if (activeTenantsCount > 0) activeTenantsCount else 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CanonicalRentField(
                                        ratePerBed = ratePerBed,
                                        onRatePerBedChange = onRatePerBedChange,
                                        rentLabel = rentLabel,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Active Tenant Capacity Warning Banner
                        if (activeTenantsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tenant_capacity_warning")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "This room currently has $activeTenantsCount active tenant${if (activeTenantsCount > 1) "s" else ""}. Bed capacity cannot be reduced below $activeTenantsCount.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================================================
                // SECTION 3 — ROOM TYPE
                // ==================================================
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF3E8FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Badge 3
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFF3E8FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "3",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9333EA)
                                )
                            }

                            Column {
                                Text(
                                    text = roomTypeTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = roomTypeSubtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Responsive Two Options: Non-AC & AC
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isNarrow = maxWidth < 340.dp
                            val isNonAc = roomType.equals("Non-AC", ignoreCase = true)
                            val isAc = roomType.equals("AC", ignoreCase = true)

                            if (isNarrow) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalRoomTypeOption(
                                        title = nonAcTitle,
                                        subtitle = nonAcSubtitle,
                                        isSelected = isNonAc,
                                        onClick = { onRoomTypeChange("Non-AC") },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFFDBEAFE), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bed,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        testTag = "type_chip_Non-AC",
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    CanonicalRoomTypeOption(
                                        title = acTitle,
                                        subtitle = acSubtitle,
                                        isSelected = isAc,
                                        onClick = { onRoomTypeChange("AC") },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AcUnit,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        testTag = "type_chip_AC",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CanonicalRoomTypeOption(
                                        title = nonAcTitle,
                                        subtitle = nonAcSubtitle,
                                        isSelected = isNonAc,
                                        onClick = { onRoomTypeChange("Non-AC") },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(Color(0xFFDBEAFE), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bed,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        testTag = "type_chip_Non-AC",
                                        modifier = Modifier.weight(1f)
                                    )

                                    CanonicalRoomTypeOption(
                                        title = acTitle,
                                        subtitle = acSubtitle,
                                        isSelected = isAc,
                                        onClick = { onRoomTypeChange("AC") },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AcUnit,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        testTag = "type_chip_AC",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================================================
                // SECTION 4 — ADDITIONAL INFORMATION
                // ==================================================
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Badge 4
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFF1F5F9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "4",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Column {
                                Text(
                                    text = additionalInfoTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = additionalInfoSubtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Notes Text Area
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    BasicTextField(
                                        value = notes,
                                        onValueChange = { if (it.length <= 200) onNotesChange(it) },
                                        textStyle = TextStyle(
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A),
                                            lineHeight = 18.sp
                                        ),
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("notes_input"),
                                        decorationBox = { innerTextField ->
                                            if (notes.isEmpty()) {
                                                Text(
                                                    text = "e.g. Near window, Attached bathroom, etc...",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }

                                Text(
                                    text = "${notes.length}/200",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Safe bottom margin
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CanonicalRoomNumberField(
    roomNumber: String,
    onRoomNumberChange: (String) -> Unit,
    roomNumberLabel: String,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isEditable) Color.White else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isEditable) Color(0xFFE2E8F0) else Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$roomNumberLabel ",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    if (isEditable) {
                        Text(
                            text = "*",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!isEditable) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(if (isEditable) Color(0xFFF1F5F9) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = if (isEditable) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isEditable) {
                    BasicTextField(
                        value = roomNumber,
                        onValueChange = onRoomNumberChange,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_number_input"),
                        decorationBox = { innerTextField ->
                            if (roomNumber.isEmpty()) {
                                Text(
                                    text = "352",
                                    fontSize = 15.sp,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            innerTextField()
                        }
                    )
                } else {
                    Text(
                        text = roomNumber.ifBlank { "—" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_number_disabled_input")
                    )
                }
            }
        }
    }
}

@Composable
private fun CanonicalFloorField(
    floor: String,
    onFloorChange: (String) -> Unit,
    floorLabel: String,
    floorOptions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .testTag("floor_input")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$floorLabel ",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "*",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = if (floor.isNotBlank()) floor else "3rd Floor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (floor.isNotBlank()) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            floorOptions.forEach { fl ->
                DropdownMenuItem(
                    text = { Text(fl, fontSize = 14.sp) },
                    onClick = {
                        onFloorChange(fl)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CanonicalCapacityField(
    capacity: String,
    onCapacityChange: (String) -> Unit,
    numBedsLabel: String,
    minCapacity: Int = 1,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$numBedsLabel ",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "*",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bed,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = capacity.ifBlank { "2" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.testTag("capacity_input")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                            .clickable {
                                val cur = capacity.toIntOrNull() ?: 1
                                if (cur > minCapacity) {
                                    onCapacityChange((cur - 1).toString())
                                } else if (cur > 1 && minCapacity == 1) {
                                    onCapacityChange((cur - 1).toString())
                                } else {
                                    // Trigger capacity change to let VM validate if below active tenants
                                    onCapacityChange((cur - 1).coerceAtLeast(1).toString())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease beds",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                            .clickable {
                                val cur = capacity.toIntOrNull() ?: 1
                                onCapacityChange((cur + 1).toString())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase beds",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonicalRentField(
    ratePerBed: String,
    onRatePerBedChange: (String) -> Unit,
    rentLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$rentLabel ",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "*",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFFEFF6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }

                BasicTextField(
                    value = ratePerBed,
                    onValueChange = onRatePerBedChange,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_input"),
                    decorationBox = { innerTextField ->
                        if (ratePerBed.isEmpty()) {
                            Text(
                                text = "6,000",
                                fontSize = 15.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

@Composable
private fun CanonicalRoomTypeOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFFF0F7FF) else Color.White,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
        ),
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 2,
                    lineHeight = 13.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, Color(0xFFCBD5E1), CircleShape)
                )
            }
        }
    }
}
