import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

bad_settings_block = r"""                        // Settings Module
                        composable\(route = Screen\.SettingsHome\.route\) \{
.*?
                        composable\(route = Screen\.AboutSettings\.route\) \{
                            com\.example\.features\.settings\.ui\.AboutScreen\(
                                onBackClick = \{ navController\.popBackStack\(\) \}
                            \)
                        \}"""

content = re.sub(bad_settings_block, "", content, flags=re.DOTALL)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
