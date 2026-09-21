import os

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

old_command_center = '''        // 2. Command Center (Quick Actions)
        item {
            Text(
                text = "Command Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(spacing.small))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                QuickActionItem(
                    title = "Add Room",
                    icon = Icons.Default.AddHome,
                    color = Color(0xFF1A73E8),
                    bgColor = Color(0xFFF0F5FF),
                    onClick = { onNavigate(Screen.Rooms.route) }
                )
                QuickActionItem(
                    title = "Add Tenant",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF0D652D),
                    bgColor = Color(0xFFF2FCEE),
                    onClick = { onNavigate(Screen.Tenants.route) }
                )
                QuickActionItem(
                    title = "Collect Rent",
                    icon = Icons.Default.AttachMoney,
                    color = Color(0xFFE37400),
                    bgColor = Color(0xFFFFF7EB),
                    onClick = { onNavigate(Screen.RentLedger.route) }
                )
                QuickActionItem(
                    title = "Add Expense",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF9334E6),
                    bgColor = Color(0xFFF8F0FE),
                    onClick = { onNavigate(Screen.Expenses.route) }
                )
            }
        }'''

new_command_center = '''        // 2. Command Center (Quick Actions)
        item {
            Text(
                text = "Command Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(spacing.small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                QuickActionItem(
                    title = "Add\nRoom",
                    icon = Icons.Default.AddHome,
                    color = Color(0xFF1A73E8),
                    bgColor = Color(0xFFF0F5FF),
                    onClick = { onNavigate(Screen.Rooms.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Add\nTenant",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF0D652D),
                    bgColor = Color(0xFFF2FCEE),
                    onClick = { onNavigate(Screen.Tenants.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Collect\nRent",
                    icon = Icons.Default.AttachMoney,
                    color = Color(0xFFE37400),
                    bgColor = Color(0xFFFFF7EB),
                    onClick = { onNavigate(Screen.RentLedger.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Add\nExpense",
                    icon = Icons.Default.Receipt,
                    color = Color(0xFF9334E6),
                    bgColor = Color(0xFFF8F0FE),
                    onClick = { onNavigate(Screen.Expenses.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Reports",
                    icon = Icons.Default.Analytics,
                    color = Color(0xFF00838F),
                    bgColor = Color(0xFFE0F7FA),
                    onClick = { onNavigate(Screen.Reports.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }'''

content = content.replace(old_command_center, new_command_center)


old_quick_action_item = '''fun QuickActionItem(
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.size(110.dp, 120.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
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
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}'''

new_quick_action_item = '''fun QuickActionItem(
    title: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(bgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}'''

content = content.replace(old_quick_action_item, new_quick_action_item)

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

