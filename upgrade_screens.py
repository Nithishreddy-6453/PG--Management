import os
import re

def rewrite_file(filepath, rewrite_func):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return
    with open(filepath, 'r') as f:
        content = f.read()
    new_content = rewrite_func(content)
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

def upgrade_dashboard(content):
    # Polish the Command Center
    content = content.replace('containerColor = MaterialTheme.colorScheme.surfaceVariant,', 'containerColor = MaterialTheme.colorScheme.primaryContainer,')
    content = content.replace('contentColor = MaterialTheme.colorScheme.onSurfaceVariant', 'contentColor = MaterialTheme.colorScheme.onPrimaryContainer')
    
    # Polish stats cards
    content = content.replace('containerColor = MaterialTheme.colorScheme.surface,', 'containerColor = MaterialTheme.colorScheme.surfaceVariant,')
    
    # Replace top app bar colors to be more premium
    content = content.replace('containerColor = MaterialTheme.colorScheme.surface', 'containerColor = MaterialTheme.colorScheme.background')
    
    # Increase corner radius for cards
    if 'shape = MaterialTheme.shapes.medium' in content:
        content = content.replace('shape = MaterialTheme.shapes.medium', 'shape = MaterialTheme.shapes.large')

    return content

def upgrade_lists(content):
    # Convert dividers to horizontal dividers with opacity
    content = content.replace('HorizontalDivider()', 'HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))')
    content = content.replace('Divider()', 'HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))')
    
    # Polish Card elevation
    content = content.replace('elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)', 'elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)')
    content = content.replace('containerColor = MaterialTheme.colorScheme.surface', 'containerColor = MaterialTheme.colorScheme.surfaceVariant')

    return content

def main():
    dashboard = 'app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt'
    rewrite_file(dashboard, upgrade_dashboard)
    
    lists = [
        'app/src/main/java/com/example/features/rooms/ui/RoomListScreen.kt',
        'app/src/main/java/com/example/features/tenants/TenantsScreen.kt',
        'app/src/main/java/com/example/features/expenses/ExpensesScreen.kt',
        'app/src/main/java/com/example/features/rent/ui/screens/RentLedgerScreen.kt'
    ]
    for lst in lists:
        rewrite_file(lst, upgrade_lists)

if __name__ == '__main__':
    main()
