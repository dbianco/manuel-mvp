plugins {
    id("com.android.application")
    // Kotlin support is built into AGP 9.0+; only the Compose compiler plugin is needed.
    id("org.jetbrains.kotlin.plugin.compose")
}

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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
