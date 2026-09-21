import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# I will delete from the start of the inserted Settings block (line 93) down to `}}` (line 151)
# and put back `)\n                        }`

bad_block = r"""                        // Settings Module
                        composable\(route = Screen\.SettingsHome\.route\) \{
.*?
                        composable\(route = Screen\.AboutSettings\.route\) \{
                            com\.example\.features\.settings\.ui\.AboutScreen\(
                                onBackClick = \{ navController\.popBackStack\(\) \}
                            \)
                        \}
                    \}
                \}
            \}
        \}
    \}
\}"""

content = re.sub(bad_block, ")\n                        }", content, flags=re.DOTALL)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
