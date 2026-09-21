import os

def rewrite_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content
    new_content = new_content.replace('backgroundContainer', 'background')
    new_content = new_content.replace('backgroundVariant', 'background')
    new_content = new_content.replace('surfaceVariantVariant', 'surfaceVariant')

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Fixed {filepath}")

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            rewrite_file(os.path.join(root, file))

