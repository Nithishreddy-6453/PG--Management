sed -i '/import dagger.hilt.android.AndroidEntryPoint/a \
import com.example.features.settings.domain.repository.SettingsRepository\
import com.example.features.settings.domain.model.AppSettings' app/src/main/java/com/example/MainActivity.kt

sed -i '/lateinit var startupManager: StartupManager/a \
    @Inject\
    lateinit var settingsRepository: SettingsRepository' app/src/main/java/com/example/MainActivity.kt

sed -i 's/PgTheme {/val appSettings by settingsRepository.getAppSettings().collectAsState(initial = AppSettings())\
            PgTheme(appTheme = appSettings.theme, dynamicColor = appSettings.dynamicColor) {/g' app/src/main/java/com/example/MainActivity.kt
