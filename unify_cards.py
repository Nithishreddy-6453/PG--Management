import os
import re

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    
    # Unify Card Colors
    new_content = re.sub(r'CardDefaults\.cardColors\([^)]*\)', 'CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)', new_content)
    
    # Unify Shape
    new_content = re.sub(r'shape = MaterialTheme\.shapes\.medium', 'shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)', new_content)
    new_content = re.sub(r'shape = MaterialTheme\.shapes\.large', 'shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)', new_content)
    new_content = re.sub(r'shape = RoundedCornerShape\([0-9]+\.dp\)', 'shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)', new_content)

    # Convert onPrimaryContainer etc in Text to onSurface
    new_content = new_content.replace('color = MaterialTheme.colorScheme.onPrimaryContainer', 'color = MaterialTheme.colorScheme.onSurface')
    new_content = new_content.replace('color = MaterialTheme.colorScheme.onSecondaryContainer', 'color = MaterialTheme.colorScheme.onSurface')
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Polished {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

