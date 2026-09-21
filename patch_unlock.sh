sed -i '/fun unlock(pin: String): Boolean {/,/^    }/c\
    fun unlock(pin: String): Boolean {\
        viewModelScope.launch {\
            val currentProfile = repository.getProfile()\
            val expectedPin = currentProfile?.pinCode ?: "1234"\
            val isMatch = if (expectedPin.startsWith("$2a$")) {\
                at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(pin.toCharArray(), expectedPin).verified\
            } else {\
                pin == expectedPin\
            }\
            if (isMatch) {\
                if (currentProfile != null && !expectedPin.startsWith("$2a$")) {\
                    val newHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, pin.toCharArray())\
                    repository.insertProfile(currentProfile.copy(pinCode = newHash))\
                }\
                _isUnlocked.value = true\
                _pinError.value = null\
            } else {\
                _pinError.value = "Incorrect PIN code. Please try again."\
            }\
        }\
        return false\
    }' app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt
