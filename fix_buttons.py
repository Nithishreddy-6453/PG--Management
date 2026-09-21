import os

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    new_content = new_content.replace('containerColor = MaterialTheme.colorScheme.background,\n                        contentColor = MaterialTheme.colorScheme.onPrimary',
                                    'containerColor = MaterialTheme.colorScheme.primary,\n                        contentColor = MaterialTheme.colorScheme.onPrimary')
                                    
    # Restore any Buttons that were accidentally made to background container color when they should be primary
    new_content = new_content.replace('ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background)',
                                    'ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)')
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Fixed {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

