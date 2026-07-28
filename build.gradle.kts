// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin.android removed: AGP 9 compiles Kotlin built-in and rejects the KGP plugin.
    alias(libs.plugins.kotlin.compose) apply false
}