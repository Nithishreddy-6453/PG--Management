import os
import re

directories = ['app/src/main/java/com/example/features/rent/ui/viewmodel', 'app/src/main/java/com/example/features/rent/data/repository']

for directory in directories:
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith('.kt'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()
                
                content = content.replace('LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)', 'java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())')
                content = content.replace('LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)', 'java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))')
                content = content.replace('LocalDate.now().plusDays(30).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))', 'java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))')
                
                # Removing imports if they are not used anymore
                content = content.replace('import java.time.LocalDate\n', '')
                content = content.replace('import java.time.format.DateTimeFormatter\n', '')

                with open(filepath, 'w') as f:
                    f.write(content)
