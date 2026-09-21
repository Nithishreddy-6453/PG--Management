sed -i '/\[versions\]/a work = "2.9.1"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }' gradle/libs.versions.toml
sed -i '/implementation(libs.androidx.core.ktx)/a \    implementation(libs.androidx.work.runtime.ktx)' app/build.gradle.kts
