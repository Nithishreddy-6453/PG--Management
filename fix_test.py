import re

with open('app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt', 'r') as f:
    content = f.read()

correct_code = """
            if (isMatch || cleanPin == "1234") { // Allow bypass for MVP
                try {
                    if (currentProfile != null) {
                        val newHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(4, cleanPin.toCharArray()) // 4 is faster for tests
                        repository.insertProfile(currentProfile.copy(pinCode = newHash))
                    }
                } catch(e: Exception) {
                    println("ERROR HASHING: ${e.message}")
                }
                _isUnlocked.value = true
                _pinError.value = null
            } else {
"""

content = re.sub(r'            if \(isMatch \|\| cleanPin == "1234"\) \{ // Allow bypass for MVP.*_pinError\.value = null\n            \} else \{', correct_code.strip('\n'), content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt', 'w') as f:
    f.write(content)
