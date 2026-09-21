import os
import re

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    # Make Cards have 0 elevation and subtle outline/container color
    # Wait, Material 3 cards come in 3 types: Elevated, Filled, Outlined. 
    # Usually `Card` defaults to filled in M3 (containerColor = surfaceVariant).
    # Let's ensure that Card shapes are large.
    content = content.replace('shape = MaterialTheme.shapes.medium', 'shape = MaterialTheme.shapes.large')

    # Convert TopAppBar colors to surface
    content = content.replace('containerColor = MaterialTheme.colorScheme.primary', 'containerColor = MaterialTheme.colorScheme.surface')
    content = content.replace('titleContentColor = MaterialTheme.colorScheme.onPrimary', 'titleContentColor = MaterialTheme.colorScheme.onSurface')
    content = content.replace('actionIconContentColor = MaterialTheme.colorScheme.onPrimary', 'actionIconContentColor = MaterialTheme.colorScheme.onSurface')
    content = content.replace('navigationIconContentColor = MaterialTheme.colorScheme.onPrimary', 'navigationIconContentColor = MaterialTheme.colorScheme.onSurface')

    # FloatingActionButton to Primary
    content = content.replace('containerColor = MaterialTheme.colorScheme.secondary', 'containerColor = MaterialTheme.colorScheme.primaryContainer')
    content = content.replace('contentColor = MaterialTheme.colorScheme.onSecondary', 'contentColor = MaterialTheme.colorScheme.onPrimaryContainer')

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Polished {filepath}")

