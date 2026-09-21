with open('app/src/main/java/com/example/features/settings/ui/viewmodel/SecurityViewModel.kt', 'r') as f:
    content = f.read()

correct_code = """
            val isMatch = if (expectedPin.startsWith("$2")) {
                try {
                    at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(cleanCurrentPin.toCharArray(), expectedPin).verified
                } catch (e: Exception) {
                    false
                }
            } else {
                cleanCurrentPin == expectedPin
            }
"""

import re
content = re.sub(r'val isMatch = if \(expectedPin\.startsWith\("\$2"\)\) \{.*?cleanCurrentPin == expectedPin\n            \}', correct_code.strip(), content, flags=re.DOTALL)

with open('app/src/main/java/com/example/features/settings/ui/viewmodel/SecurityViewModel.kt', 'w') as f:
    f.write(content)
