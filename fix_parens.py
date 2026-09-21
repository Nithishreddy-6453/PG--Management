import os
import re

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    # Fix the extra parenthesis introduced by regex
    new_content = new_content.replace('colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)),', 'colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),')
    new_content = new_content.replace('colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) ),', 'colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),')
    new_content = new_content.replace('colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) )', 'colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)')
    new_content = new_content.replace('colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface))', 'colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)')

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Fixed {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

