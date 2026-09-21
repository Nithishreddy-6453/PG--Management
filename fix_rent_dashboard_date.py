with open('app/src/main/java/com/example/features/rent/ui/viewmodel/RentDashboardViewModel.kt', 'r') as f:
    content = f.read()

old_block = '''                val today = LocalDate.now()
                val dueDateStr = today.plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE)
                val billingMonthStr = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))'''

new_block = '''                val currentTime = System.currentTimeMillis()
                val dueDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(currentTime + 5L * 24 * 60 * 60 * 1000))
                val billingMonthStr = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(java.util.Date(currentTime))'''

content = content.replace(old_block, new_block)
content = content.replace('import java.time.LocalDate\n', '')
content = content.replace('import java.time.format.DateTimeFormatter\n', '')

with open('app/src/main/java/com/example/features/rent/ui/viewmodel/RentDashboardViewModel.kt', 'w') as f:
    f.write(content)
