sed -i '/import androidx.compose.runtime.getValue/a \
import androidx.compose.runtime.DisposableEffect\
import androidx.compose.runtime.remember\
import androidx.compose.runtime.mutableStateOf\
import androidx.compose.runtime.setValue' app/src/main/java/com/example/MainActivity.kt
