package com.example.features.rooms.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.core.designsystem.LocalSpacing
import com.example.features.rooms.ui.viewmodel.EditRoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRoomScreen(
    viewModel: EditRoomViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Handle save success
    LaunchedEffect(key1 = true) {
        viewModel.saveSuccessEvent.collect {
            Toast.makeText(context, "Room updated successfully", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Room ${state.roomNumber}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("edit_room_top_bar")
            )
        },
        modifier = modifier.fillMaxSize().testTag("edit_room_screen_container")
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // Error Display Banner
                state.error?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().testTag("error_banner")
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(spacing.large)
                        )
                    }
                }

                // Info banner about current occupancy
                if (state.activeTenantsCount > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "This room currently has ${state.activeTenantsCount} active tenant(s). You cannot set the bed capacity below ${state.activeTenantsCount}.",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(spacing.large)
                        )
                    }
                }

                // 1. Room Number Field (Locked/Disabled)
                OutlinedTextField(
                    value = state.roomNumber,
                    onValueChange = { },
                    label = { Text("Room Number") },
                    enabled = false,
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_number_disabled_input")
                )

                // 2. Floor Field
                OutlinedTextField(
                    value = state.floor,
                    onValueChange = { viewModel.onFloorChanged(it) },
                    label = { Text("Floor *") },
                    placeholder = { Text("e.g. Ground Floor, 1st Floor") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floor_input")
                )

                // 3. Bed Capacity
                OutlinedTextField(
                    value = state.capacity,
                    onValueChange = { viewModel.onCapacityChanged(it) },
                    label = { Text("Bed Capacity *") },
                    placeholder = { Text("Number of beds, e.g. 1, 2, 3") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capacity_input")
                )

                // 4. Rate Per Bed (Rent)
                OutlinedTextField(
                    value = state.ratePerBed,
                    onValueChange = { viewModel.onRatePerBedChanged(it) },
                    label = { Text("Monthly Rent Per Bed *") },
                    placeholder = { Text("₹ e.g. 6000") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_input")
                )

                // 5. Room Type Selector
                Column {
                    Text(
                        "Room Type *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = spacing.extraSmall)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val types = listOf("Non-AC", "AC")
                        types.forEach { type ->
                            FilterChip(
                                selected = state.roomType == type,
                                onClick = { viewModel.onRoomTypeChanged(type) },
                                label = { Text(type) },
                                modifier = Modifier.testTag("type_chip_$type")
                            )
                        }
                    }
                }

                // 6. Notes Field
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { viewModel.onNotesChanged(it) },
                    label = { Text("Room Notes (Optional)") },
                    placeholder = { Text("e.g. Near balcony, includes attached washroom...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input")
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                // 7. Save Button
                Button(
                    onClick = { viewModel.saveRoom() },
                    enabled = !state.isSaving,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_room_button")
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
