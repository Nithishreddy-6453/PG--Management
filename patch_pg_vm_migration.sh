sed -i '/existing?.pinCode ?: at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, "1234".toCharArray())/c\
                val current = existing?.pinCode ?: "1234"\
                if (!current.startsWith("$2a$")) {\
                    at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, current.toCharArray())\
                } else {\
                    current\
                }' app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt
