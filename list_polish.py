import os
import re

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    
    # Increase padding in list items
    new_content = re.sub(r'Modifier\.padding\(spacing\.medium\)', 'Modifier.padding(spacing.large)', new_content)
    
    # Increase card elevation slightly if it's 0 to 1 or 2 for list items to make them pop out of background
    new_content = new_content.replace('CardDefaults.cardElevation(defaultElevation = 0.dp)', 'CardDefaults.cardElevation(defaultElevation = 1.dp)')

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Polished {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

