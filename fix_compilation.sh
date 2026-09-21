sed -i '9d' app/src/main/java/com/example/PgApplication.kt
sed -i 's/modifier = Modifier.clickable {/modifier = Modifier.clickable(onClick = {/g' app/src/main/java/com/example/features/settings/ui/AboutScreen.kt
sed -i 's/} )/})/' app/src/main/java/com/example/features/settings/ui/AboutScreen.kt

sed -i '/private fun Modifier.clickable(onClick: () -> Unit): Modifier {/,/}/d' app/src/main/java/com/example/features/settings/ui/AboutScreen.kt
