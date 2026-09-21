# Remove the old DisposableEffect and vars
sed -i '/var appBackgroundTime by remember/d' app/src/main/java/com/example/MainActivity.kt
sed -i '/val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current/d' app/src/main/java/com/example/MainActivity.kt
sed -i '/DisposableEffect(lifecycleOwner) {/,/}/d' app/src/main/java/com/example/MainActivity.kt

# Re-insert inside PgTheme
sed -i '/val navController = rememberNavController()/a \
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
                                    navController.navigate(Screen.PinLogin.route)\
                                }\
                            }\
                        }\
                    }\
                    lifecycleOwner.lifecycle.addObserver(observer)\
                    onDispose {\
                        lifecycleOwner.lifecycle.removeObserver(observer)\
                    }\
                }' app/src/main/java/com/example/MainActivity.kt
