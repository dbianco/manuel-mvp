# Plan: MainScreen instrumented UI tests (T019)

## Technical Context

- Language: Kotlin, `app/src/androidTest/kotlin/com/manuel/mvp/ui/` (instrumented test source set, per the top-level `plan.md`'s file tree).
- Primary dependencies: `androidx.compose.ui.test.junit4` (`createComposeRule`), `androidx.compose.ui.test` assertions — not yet declared in `app/build.gradle.kts`; T020 (or this task) needs to add `androidTestImplementation(platform(compose-bom))` + `androidx.compose.ui:ui-test-junit4` when a real Gradle sync is possible. Declaring the dependency itself is safe to do now (see Research).
- Storage: none.
- Testing tools: Compose UI testing framework, instrumented (`androidTest`), requiring a real device/emulator to actually run — not just an Android SDK.
- Target platform: `com.manuel.mvp`, `minSdk = 29`.

## Constitution Check

- No personal data in logs: not applicable, this is a UI test with no data at all.
- HTTP/DB/public-API/secrets: not applicable.
- CI tests gate merge: instrumented tests typically run on a separate CI lane (an emulator matrix), not the same lane as JVM unit tests — T021 (CI config) should account for this; whether T021's initial CI actually runs instrumented tests or defers that is T021's call.
- Accessibility: this IS the accessibility check (`contentDescription` on both buttons), directly implementing the company constitution's "interactive elements are keyboard reachable and labelled" standard for this screen.

## Project Structure

```
app/src/androidTest/kotlin/com/manuel/mvp/ui/
└── MainScreenTest.kt   # new: pins the MainScreen + AssistantState contract
```

## Research

- **Declaring the `androidTestImplementation` Compose test dependency now**: since `app/build.gradle.kts` can be edited even though Gradle itself can't run in this sandbox, adding the dependency line now (rather than deferring to T020) means the project is one real Gradle sync away from actually running this test, instead of silently missing a needed dependency. Verified only by inspection (matching the file's own SC-002), not by a real sync.
- **Stateless `MainScreen` contract**: taking `AssistantState` and two callbacks as parameters (no internal `ViewModel`/pipeline reference) is what makes this composable renderable in a test with a fake/fixed state, rather than needing a real `ConversationPipeline` (which itself needs real hardware/native engines, per T018's Clarifications) — the same "extract the testable part" principle applied at the UI layer.
- **Button enabled-state as the armed/disarmed signal, not visibility**: both buttons stay on-screen at all times (only their enabled state toggles) — stable UI element positions are generally better for both usability and accessibility (a control that appears/disappears is harder for a screen-reader user or a young child to predict) than conditionally rendering one button or the other.
- **No exact literal-text pinning for status strings**: matches the `SYSTEM_INSTRUCTIONS`-style "pin structure, not exact prose" approach — tests look for a recognizable Spanish substring per state (e.g. containing "armado"), leaving T020 free to choose the exact final copy/formatting.
