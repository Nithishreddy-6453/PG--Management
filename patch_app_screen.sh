sed -i '/val uiState by viewModel.uiState.collectAsState()/a \
    val snackbarHostState = remember { SnackbarHostState() }\
\
    LaunchedEffect(key1 = true) {\
        viewModel.messageEvent.collect { message ->\
            snackbarHostState.showSnackbar(message)\
        }\
    }' app/src/main/java/com/example/features/settings/ui/AppPreferencesScreen.kt

sed -i '/modifier = modifier.fillMaxSize()/a \
        snackbarHost = { SnackbarHost(snackbarHostState) },' app/src/main/java/com/example/features/settings/ui/AppPreferencesScreen.kt
