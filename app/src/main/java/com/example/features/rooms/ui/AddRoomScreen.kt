package com.example.features.rooms.ui

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.features.rooms.ui.viewmodel.AddRoomViewModel

/**
 * Register / Add Room Screen delegating to the single canonical RoomFormContent.
 */
@Composable
fun AddRoomScreen(
    viewModel: AddRoomViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // Handle save success
    LaunchedEffect(key1 = true) {
        viewModel.saveSuccessEvent.collect {
            Toast.makeText(context, "Room registered successfully", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    RoomFormContent(
        isEditMode = false,
        roomNumber = state.roomNumber,
        onRoomNumberChange = { viewModel.onRoomNumberChanged(it) },
        isRoomNumberEditable = true,
        floor = state.floor,
        onFloorChange = { viewModel.onFloorChanged(it) },
        capacity = state.capacity,
        onCapacityChange = { viewModel.onCapacityChanged(it) },
        ratePerBed = state.ratePerBed,
        onRatePerBedChange = { viewModel.onRatePerBedChanged(it) },
        roomType = state.roomType,
        onRoomTypeChange = { viewModel.onRoomTypeChanged(it) },
        notes = state.notes,
        onNotesChange = { viewModel.onNotesChanged(it) },
        activeTenantsCount = 0,
        isLoading = false,
        isSaving = state.isSaving,
        error = state.error,
        onSaveClick = { viewModel.saveRoom() },
        onCancelClick = onBackClick,
        onBackClick = onBackClick,
        modifier = modifier
    )
}
