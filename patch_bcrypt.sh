sed -i '/\[versions\]/a bcrypt = "0.10.2"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a bcrypt = { group = "at.favre.lib", name = "bcrypt", version.ref = "bcrypt" }' gradle/libs.versions.toml
sed -i '/implementation(libs.androidx.biometric)/a \  implementation(libs.bcrypt)' app/build.gradle.kts
