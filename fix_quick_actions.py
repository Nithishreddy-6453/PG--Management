import os

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Replace QuickActionItem containerColor
old_quick = '''fun QuickActionItem(
    title: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),'''

new_quick = '''fun QuickActionItem(
    title: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),'''
content = content.replace(old_quick, new_quick)

# Replace Box background in QuickActionItem since the whole card is now colored
old_box = '''                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(bgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }'''
new_box = '''                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                }'''
content = content.replace(old_box, new_box)

# Update colors for QuickActionItems in the Row
old_add_room = '''QuickActionItem(
                    title = "Add Room",
                    icon = Icons.Default.AddHome,
                    color = Color(0xFF1A73E8),
                    bgColor = Color(0xFFE8F0FE),'''
new_add_room = '''QuickActionItem(
                    title = "Add Room",
                    icon = Icons.Default.AddHome,
                    color = Color(0xFF1A73E8),
                    bgColor = Color(0xFFF0F5FF),'''
content = content.replace(old_add_room, new_add_room)

old_add_tenant = '''QuickActionItem(
                    title = "Add Tenant",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF0D652D),
                    bgColor = Color(0xFFE6F4EA),'''
new_add_tenant = '''QuickActionItem(
                    title = "Add Tenant",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF0D652D),
                    bgColor = Color(0xFFF2FCEE),'''
content = content.replace(old_add_tenant, new_add_tenant)

old_rent = '''QuickActionItem(
                    title = "Collect Rent",
                    icon = Icons.Default.AttachMoney,
                    color = Color(0xFFE37400),
                    bgColor = Color(0xFFFEF7E0),'''
new_rent = '''QuickActionItem(
                    title = "Collect Rent",
                    icon = Icons.Default.AttachMoney,
                    color = Color(0xFFE37400),
                    bgColor = Color(0xFFFFF7EB),'''
content = content.replace(old_rent, new_rent)

old_expense = '''QuickActionItem(
                    title = "Add Expense",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF9334E6),
                    bgColor = Color(0xFFF3E8FD),'''
new_expense = '''QuickActionItem(
                    title = "Add Expense",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF9334E6),
                    bgColor = Color(0xFFF8F0FE),'''
content = content.replace(old_expense, new_expense)

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

