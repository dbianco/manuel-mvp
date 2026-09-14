import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin support is built into AGP 9.0+; only the Compose compiler plugin is needed.
    id("org.jetbrains.kotlin.plugin.compose")
}

// T003: Picovoice Porcupine (wake-word) requires a free per-user AccessKey from
// console.picovoice.ai. That key is a secret and must never be committed, so it
// is read from the gitignored root `local.properties` file (the same file Gradle
// uses for the local SDK path) rather than from any source file. Add it there as:
//
//   picovoice.accessKey=<your-key-from-console.picovoice.ai>
//
// If `local.properties` or the property is missing (e.g. a fresh clone before the
// human partner has set up their own key), this resolves to an empty string so the
// Gradle build still succeeds. Porcupine will simply fail at *runtime* init in that
// case (a concern for T010's WakeWordListener.kt, not this build wiring).
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val picovoiceAccessKey: String = localProperties.getProperty("picovoice.accessKey", "")

android {
    namespace = "com.manuel.mvp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.manuel.mvp"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // T002: native libraries (llama.cpp, whisper.cpp) are only built for
        // 64-bit targets — arm64-v8a for real devices ("gama media/alta"
        // hardware per the MVP spec) and x86_64 for the emulator during
        // development. armeabi-v7a/x86 (32-bit) are explicitly out of scope.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // T003: expose the Picovoice AccessKey (see above) to Kotlin as
        // BuildConfig.PICOVOICE_ACCESS_KEY, for T010's WakeWordListener.kt to consume.
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceAccessKey\"")
    }

    // T002: wires app/CMakeLists.txt (vendored llama.cpp/whisper.cpp
    // submodules under ../third_party/, plus the placeholder manuel_native
    // JNI bridge target) into the Android Gradle Plugin's native build.
    //
    // No ndkVersion is pinned here: this sandbox has no NDK installed to
    // validate a specific version against, so AGP's default NDK resolution
    // is left in place. Revisit if/when a concrete version requirement
    // shows up (e.g. during T013/T015 real JNI bridge work).
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // T003: Picovoice Porcupine wake-word engine SDK. Actual usage
    // (WakeWordListener.kt) is T010 — this task only wires the dependency
    // and the AccessKey loading mechanism above.
    implementation("ai.picovoice:porcupine-android:4.0.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
