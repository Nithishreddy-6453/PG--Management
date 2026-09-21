import os
import re

def replace_in_file(filepath, replacements):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    new_content = content
    for pattern, replacement in replacements:
        new_content = re.sub(pattern, replacement, new_content)
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

# AutoMirrored replacements
icons_map = {
    'Icons.Filled.TrendingUp': 'Icons.AutoMirrored.Filled.TrendingUp',
    'Icons.Filled.Sort': 'Icons.AutoMirrored.Filled.Sort',
    'Icons.Filled.ReceiptLong': 'Icons.AutoMirrored.Filled.ReceiptLong',
    'Icons.Filled.ArrowBack': 'Icons.AutoMirrored.Filled.ArrowBack',
    'Icons.Filled.ExitToApp': 'Icons.AutoMirrored.Filled.ExitToApp'
}

auto_mirrored_patterns = [(r'\b' + k.replace('.', r'\.') + r'\b', v) for k, v in icons_map.items()]

def fix_file(filepath):
    replace_in_file(filepath, auto_mirrored_patterns)
    replace_in_file(filepath, [
        (r'\bDivider\(', 'HorizontalDivider('),
        (r'\.menuAnchor\(\)', '.menuAnchor(MenuAnchorType.PrimaryNotEditable)')
    ])
    
    # Progress indicator replacement for RoomListScreen
    if "RoomListScreen.kt" in filepath:
        replace_in_file(filepath, [
            (r'LinearProgressIndicator\(\s*progress\s*=\s*([^,]+)(.*?)', r'LinearProgressIndicator(progress = { \1 }\2')
        ])
        
# Get all kotlin files
for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            fix_file(os.path.join(root, file))

print("Warnings patched.")
