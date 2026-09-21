import os
import re

def replace_in_file(filepath, replacements):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    new_content = content
    for pattern, replacement in replacements:
        new_content = re.sub(pattern, replacement, new_content)
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)

icon_imports = {
    'TrendingUp', 'Sort', 'ReceiptLong', 'ArrowBack', 'ExitToApp'
}

patterns = [(f'import androidx.compose.material.icons.filled.{icon}', f'import androidx.compose.material.icons.automirrored.filled.{icon}') for icon in icon_imports]

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            replace_in_file(os.path.join(root, file), patterns)

print("Imports patched.")
