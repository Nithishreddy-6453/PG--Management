import os
import re

def replace_in_file(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    new_content = content
    # Replace Icons.Default.TrendingUp -> Icons.AutoMirrored.Filled.TrendingUp
    new_content = new_content.replace('Icons.Default.TrendingUp', 'Icons.AutoMirrored.Filled.TrendingUp')
    new_content = new_content.replace('Icons.Filled.TrendingUp', 'Icons.AutoMirrored.Filled.TrendingUp')
    new_content = new_content.replace('Icons.Default.ReceiptLong', 'Icons.AutoMirrored.Filled.ReceiptLong')
    new_content = new_content.replace('Icons.Filled.ReceiptLong', 'Icons.AutoMirrored.Filled.ReceiptLong')

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            replace_in_file(os.path.join(root, file))

print("Icons patched.")
