sed -i '/val appSettings by settingsRepository.getAppSettings().collectAsState(initial = AppSettings())/a \
            val securitySettings by settingsRepository.getSecuritySettings().collectAsState(initial = com.example.features.settings.domain.model.SecuritySettings())\
\
            var appBackgroundTime by remember { mutableStateOf(0L) }\
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current\
\
            DisposableEffect(lifecycleOwner) {\
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {\
                        appBackgroundTime = System.currentTimeMillis()\
                    } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {\
                        if (appBackgroundTime > 0) {\
                            val timeInBackground = System.currentTimeMillis() - appBackgroundTime\
                            if (securitySettings.requirePinOnResume || (securitySettings.autoLockTimeout > 0 && timeInBackground >= securitySettings.autoLockTimeout)) {\
                                viewModel.lockApp()\
                                // Note: We might need to handle navigation here or just let the PinLoginScreen cover if isUnlocked is observed.\
                            }\
                        }\
                    }\
                }\
                lifecycleOwner.lifecycle.addObserver(observer)\
                onDispose {\
                    lifecycleOwner.lifecycle.removeObserver(observer)\
                }\
            }' app/src/main/java/com/example/MainActivity.kt
