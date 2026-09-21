sed -i '/fun changePin(/,/^    }/c\
    fun changePin(currentPin: String, newPin: String, confirmPin: String) {\
        viewModelScope.launch {\
            _uiState.value = _uiState.value.copy(\
                currentPinError = null,\
                newPinError = null,\
                confirmPinError = null\
            )\
\
            val profile = pgRepository.getProfile()\
            val expectedPin = profile?.pinCode ?: "1234"\
\
            var isValid = true\
            val isMatch = if (expectedPin.startsWith("$2a$")) {\
                at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(currentPin.toCharArray(), expectedPin).verified\
            } else {\
                currentPin == expectedPin\
            }\
\
            if (!isMatch) {\
                _uiState.value = _uiState.value.copy(currentPinError = "Incorrect current PIN")\
                isValid = false\
            }\
\
            if (newPin.length != 4 || !newPin.all { it.isDigit() }) {\
                _uiState.value = _uiState.value.copy(newPinError = "PIN must be exactly 4 digits")\
                isValid = false\
            }\
\
            if (newPin != confirmPin) {\
                _uiState.value = _uiState.value.copy(confirmPinError = "PINs do not match")\
                isValid = false\
            }\
\
            if (isValid) {\
                val newHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, newPin.toCharArray())\
                val updatedProfile = profile?.copy(pinCode = newHash) ?: OwnerProfileEntity(id = 1, pgName = "My PG", ownerName = "Owner", phone = "", upiId = "", pinCode = newHash)\
                pgRepository.insertProfile(updatedProfile)\
                _messageEvent.emit("PIN changed successfully")\
            }\
        }\
    }' app/src/main/java/com/example/features/settings/ui/viewmodel/SecurityViewModel.kt
