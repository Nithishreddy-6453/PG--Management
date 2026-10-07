package com.example.features.rooms.ui

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.features.rooms.ui.viewmodel.EditRoomViewModel

/**
 * Edit Room Screen delegating to the single canonical RoomFormContent.
 */
@Composable
fun EditRoomScreen(
    viewModel: EditRoomViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // Handle save success
    LaunchedEffect(key1 = true) {
        viewModel.saveSuccessEvent.collect {
            Toast.makeText(context, "Room updated successfully", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    RoomFormContent(
        isEditMode = true,
        roomNumber = state.roomNumber,
        onRoomNumberChange = { },
        isRoomNumberEditable = false,
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
        activeTenantsCount = state.activeTenantsCount,
        isLoading = state.isLoading,
        isSaving = state.isSaving,
        error = state.error,
        onSaveClick = { viewModel.saveRoom() },
        onCancelClick = onBackClick,
        onBackClick = onBackClick,
        modifier = modifier
    )
}
