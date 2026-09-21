import os

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

if 'import androidx.compose.ui.text.style.TextOverflow' not in content:
    content = content.replace('import androidx.compose.ui.text.font.FontWeight', 'import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow')

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

