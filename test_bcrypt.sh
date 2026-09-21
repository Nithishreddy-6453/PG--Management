sed -i 's/verify(pin.toCharArray(), expectedPin).verified/verify(pin.toCharArray(), expectedPin.toCharArray()).verified/g' app/src/main/java/com/example/ui/viewmodel/PgViewModel.kt
sed -i 's/verify(currentPin.toCharArray(), expectedPin).verified/verify(currentPin.toCharArray(), expectedPin.toCharArray()).verified/g' app/src/main/java/com/example/features/settings/ui/viewmodel/SecurityViewModel.kt
