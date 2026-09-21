            val currentProfile = repository.getProfile()
            val expectedPin = currentProfile?.pinCode?.trim() ?: "1234"
            val cleanPin = pin.trim()
            val isMatch = if (expectedPin.startsWith("$2")) {
                at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(cleanPin.toCharArray(), expectedPin.toCharArray()).verified
            } else {
                cleanPin == expectedPin
            }
