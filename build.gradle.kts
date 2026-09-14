// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.0" apply false
    // Kotlin support for Android modules is built into AGP 9.0+, so the separate
    // `org.jetbrains.kotlin.android` plugin is no longer applied here.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
