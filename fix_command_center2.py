import os

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('title = "Add\nRoom",', 'title = "Add\\nRoom",')
content = content.replace('title = "Add\nTenant",', 'title = "Add\\nTenant",')
content = content.replace('title = "Collect\nRent",', 'title = "Collect\\nRent",')
content = content.replace('title = "Add\nExpense",', 'title = "Add\\nExpense",')

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

