import os
import re

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    
    # Replace standard Scaffold TopAppBar with styled one
    new_content = new_content.replace(
        'TopAppBarDefaults.topAppBarColors(\n                    containerColor = MaterialTheme.colorScheme.surface,\n                    titleContentColor = MaterialTheme.colorScheme.onSurface\n                )',
        'TopAppBarDefaults.topAppBarColors(\n                    containerColor = MaterialTheme.colorScheme.background,\n                    titleContentColor = MaterialTheme.colorScheme.onBackground,\n                    scrolledContainerColor = MaterialTheme.colorScheme.surface\n                )'
    )
    
    new_content = new_content.replace(
        'containerColor = MaterialTheme.colorScheme.primary',
        'containerColor = MaterialTheme.colorScheme.background'
    )
    new_content = new_content.replace(
        'titleContentColor = MaterialTheme.colorScheme.onPrimary',
        'titleContentColor = MaterialTheme.colorScheme.onBackground'
    )
    new_content = new_content.replace(
        'actionIconContentColor = MaterialTheme.colorScheme.onPrimary',
        'actionIconContentColor = MaterialTheme.colorScheme.primary'
    )

    # Convert Cards to premium styles
    new_content = new_content.replace(
        'CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)',
        'CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)'
    )
    
    # Let's add OutlinedCard where appropriate, but usually we just add elevation or border
    
    # Improve Empty states styling
    new_content = re.sub(r'Modifier\.size\(\d+\.dp\)', 'Modifier.size(80.dp)', new_content) # Boost icons a bit

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Polished {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

