// Top-level build.gradle.kts
plugins {
    alias(libs.plugins.android.application)            apply false
    alias(libs.plugins.jetbrains.kotlin.android)       apply false
    alias(libs.plugins.jetbrains.kotlin.compose)       apply false
    alias(libs.plugins.google.gms.google.services)     apply false
    alias(libs.plugins.google.devtools.ksp)            apply false
    alias(libs.plugins.hilt.android)                   apply false
    alias(libs.plugins.google.firebase.crashlytics)    apply false  // ← ADD THIS
}