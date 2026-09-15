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

    // T003: openWakeWord wake-word engine SDK (Kotlin wrapper around ONNX
    // Runtime; runs fully on-device — no account, API key, or secret of any
    // kind, unlike Picovoice's Porcupine (the engine originally used for
    // T003), which required a per-account AccessKey with free-tier usage
    // limits). Actual usage (WakeWordListener.kt) is T010 — this task only
    // wires the dependency.
    //
    // At runtime this needs three .onnx assets under
    // app/src/main/assets/wakeword/:
    //   - melspectrogram.onnx, embedding_model.onnx: generic, shared models
    //     from the openWakeWord project (Apache-2.0), vendored by this task.
    //   - manuel.onnx: the custom "Manuel" keyword classifier. This file does
    //     NOT exist yet — it must be trained by a human partner using the
    //     openWakeWord project's training notebook/Colab pipeline
    //     (github.com/dscripka/openWakeWord), then placed at exactly
    //     app/src/main/assets/wakeword/manuel.onnx (T010 will reference this
    //     path). Until that file exists, wake-word detection cannot function
    //     — expected at this stage of the project.
    implementation("xyz.rementia:openwakeword:0.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // T006 (revised post-T022, on real hardware): ContentDatabase/ContentDao
    // (app/src/main/kotlin/com/manuel/mvp/rag/) originally used
    // androidx.sqlite:sqlite-framework's FrameworkSQLiteOpenHelperFactory, which wraps the OS's own
    // bundled SQLite -- but the first real on-device run (an Android 37 emulator, google_apis
    // image) failed with "no such module: fts5", confirming the OS's system SQLite doesn't have
    // FTS5 compiled in (exactly the limitation already known for Robolectric's SQLite, now also
    // confirmed for real Android). Switched to androidx.sqlite:sqlite-bundled, Google's own
    // bundled-SQLite artifact built specifically for this problem (ships a recent SQLite compiled
    // with FTS5, independent of the OS version) -- this uses the newer SQLiteDriver/SQLiteConnection
    // API rather than SupportSQLiteOpenHelper/SupportSQLiteDatabase, so ContentDatabase.kt/
    // ContentDao.kt were migrated accordingly. Version matches the sqlite-framework version it
    // replaces (2.7.1, verified live against Google's Maven repository).
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")

    // T005: FragmentSearcherTest (app/src/test/kotlin/com/manuel/mvp/rag/) runs FragmentSearcher's
    // real search/ranking logic as a JVM unit test (test/, not androidTest/) against a real,
    // FTS5-capable SQLite database via org.xerial:sqlite-jdbc, through the FragmentRowSource
    // raw-SQL seam -- FragmentSearcher itself doesn't exist yet (T006). Test-scope only.
    //
    // An earlier version of this dependency list used Robolectric + androidx.sqlite instead, but
    // empirical verification found Robolectric 4.17's SQLite engine doesn't support FTS5 at all
    // (see task-5-report.md) -- switched to org.xerial:sqlite-jdbc, whose FTS5 support (including
    // the exact "unicode61 remove_diacritics 2" tokenizer used here) was verified empirically
    // before adopting it. Versions verified live against Maven Central at the time of this task
    // (latest stable: junit 4.13.2, org.xerial:sqlite-jdbc 3.53.4.0).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")

    // T019: MainScreenTest (app/src/androidTest/kotlin/com/manuel/mvp/ui/) instrumented-tests the
    // not-yet-existing MainScreen composable (T020) via Compose's own test APIs. Gradle resolves
    // dependency versions per source-set configuration independently, so the compose-bom platform
    // needs re-declaring here for androidTestImplementation even though it's already declared
    // above for implementation. debugImplementation("androidx.compose.ui:ui-test-manifest")
    // (already present above, from T001) supplies the test activity manifest entry
    // createComposeRule() needs. Cannot be verified via a real Gradle sync in this sandbox (see
    // t019-main-screen-test-spec.md's Clarifications) -- versions chosen to match what's already
    // pinned above (compose-bom 2026.09.00) plus androidx.test.ext:junit, the standard JUnit4
    // runner for Android instrumented tests -- 1.3.0 confirmed as the current latest/release
    // version via dl.google.com/dl/android/maven2/androidx/test/ext/junit/maven-metadata.xml.
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
