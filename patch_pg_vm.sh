sed -i '/fun updateProfile(/,/^    }/c\
    fun updateProfile(pgName: String, ownerName: String, phone: String, upiId: String, pinCode: String) {\
        viewModelScope.launch {\
            val existing = repository.getProfile()\
            val pinToSave = if (pinCode.isNotBlank() && pinCode != existing?.pinCode && !pinCode.startsWith("$2a$")) {\
                at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, pinCode.toCharArray())\
            } else {\
                existing?.pinCode ?: at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, "1234".toCharArray())\
            }\
            repository.insertProfile(\
                OwnerProfileEntity(\
                    id = 1,\
                    pgName = pgName,\
                    ownerName = ownerName,\
                    phone = phone,\
                    upiId = upiId,\
                    pinCode = pinToSave\
                )\
            )\
        }\
    }' app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt
