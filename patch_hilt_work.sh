sed -i '/\[versions\]/a hiltWork = "1.2.0"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }' gradle/libs.versions.toml
sed -i '/\[libraries\]/a androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }' gradle/libs.versions.toml

sed -i '/implementation(libs.hilt.android)/a \    implementation(libs.androidx.hilt.work)\n    ksp(libs.androidx.hilt.compiler)' app/build.gradle.kts
