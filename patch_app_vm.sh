sed -i '/val uiState: StateFlow<AppSettings> = _uiState.asStateFlow()/a \
\
    private val _messageEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()\
    val messageEvent: kotlinx.coroutines.flow.SharedFlow<String> = _messageEvent.asSharedFlow()' app/src/main/java/com/example/features/settings/ui/viewmodel/AppPreferencesViewModel.kt

sed -i 's/settingsRepository.updateAppSettings(settings)/settingsRepository.updateAppSettings(settings)\
            _messageEvent.emit("Preferences saved")/g' app/src/main/java/com/example/features/settings/ui/viewmodel/AppPreferencesViewModel.kt
