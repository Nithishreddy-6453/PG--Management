import os

def fix_imports(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    if 'import androidx.compose.material.icons.filled.*' in content and 'import androidx.compose.material.icons.automirrored.filled.*' not in content:
        content = content.replace('import androidx.compose.material.icons.filled.*', 'import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.automirrored.filled.*')
        with open(filepath, 'w') as f:
            f.write(content)

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            fix_imports(os.path.join(root, file))

print("Wildcard imports patched.")
