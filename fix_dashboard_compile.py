import os

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('summary.expenses.monthlyExpenses', 'summary.expenses.totalExpenses')

with open('app/src/main/java/com/example/features/dashboard/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)

