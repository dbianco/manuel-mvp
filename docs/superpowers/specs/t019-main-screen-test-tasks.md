# Tasks: MainScreen instrumented UI tests (T019)

## Tasks

- [ ] T001 Add `androidx.compose.ui:ui-test-junit4` (via the Compose BOM) as `androidTestImplementation` in `app/build.gradle.kts`, plus `androidx.compose.ui:ui-test-manifest` as `debugImplementation` if not already present
- [ ] T002 Write `MainScreenTest.kt` covering the 8 acceptance scenarios (button enabled-state inversion, per-state status text, contentDescription accessibility, click callbacks) in `app/src/androidTest/kotlin/com/manuel/mvp/ui/MainScreenTest.kt`, depends on T001
