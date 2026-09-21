import re
with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Add AnimatedContent import
if 'import androidx.compose.animation.AnimatedContent' not in content:
    content = content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.animation.AnimatedContent\nimport androidx.compose.runtime.Composable')

# Wrap when(state) in AnimatedContent
old_block = """            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    DashboardSkeletonLoading()
                }
                is DashboardUiState.Empty -> {
                    DashboardEmptyRoomsState(
                        onAddRoomClick = { onNavigate(Screen.Rooms.route) }
                    )
                }
                is DashboardUiState.Error -> {
                    DashboardErrorState(
                        errorMessage = state.message,
                        onRetryClick = { dashboardViewModel.handleEvent(DashboardUiEvent.Retry) }
                    )
                }
                is DashboardUiState.Success -> {
                    val summary = state.data
                    DashboardContent(
                        ownerName = ownerName,
                        summary = summary,
                        onNavigate = onNavigate
                    )
                }
            }"""

new_block = """            AnimatedContent(targetState = uiState, label = "DashboardStateAnimation") { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        DashboardSkeletonLoading()
                    }
                    is DashboardUiState.Empty -> {
                        DashboardEmptyRoomsState(
                            onAddRoomClick = { onNavigate(Screen.Rooms.route) }
                        )
                    }
                    is DashboardUiState.Error -> {
                        DashboardErrorState(
                            errorMessage = state.message,
                            onRetryClick = { dashboardViewModel.handleEvent(DashboardUiEvent.Retry) }
                        )
                    }
                    is DashboardUiState.Success -> {
                        val summary = state.data
                        DashboardContent(
                            ownerName = ownerName,
                            summary = summary,
                            onNavigate = onNavigate
                        )
                    }
                }
            }"""

content = content.replace(old_block, new_block)

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

