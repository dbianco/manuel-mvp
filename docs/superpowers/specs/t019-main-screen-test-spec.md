# Spec: MainScreen instrumented UI tests (T019)

## User Scenarios

### Primary user story

As Manuel, I want the main screen to visibly reflect the assistant's current state (armed, listening, processing, responding, error) and to have its two control buttons properly labelled for screen readers, so sighted and non-sighted users alike can tell what the assistant is doing and control it (FR-001, plus the company's accessibility standard).

### Acceptance scenarios

1. **Given** `MainScreen` rendered with `AssistantState.Disarmed`, **When** inspected, **Then** the "Escuchar" button is enabled and the "Dejar de escuchar" button is disabled.
2. **Given** `MainScreen` rendered with `AssistantState.Armed`, **When** inspected, **Then** the "Escuchar" button is disabled, "Dejar de escuchar" is enabled, and a status text indicating "armado" is displayed.
3. **Given** `MainScreen` rendered with `AssistantState.Listening`, **When** inspected, **Then** a status text indicating "escuchando" is displayed.
4. **Given** `MainScreen` rendered with `AssistantState.Processing`, **When** inspected, **Then** a status text indicating "procesando" is displayed.
5. **Given** `MainScreen` rendered with `AssistantState.Responding`, **When** inspected, **Then** a status text indicating "respondiendo" is displayed.
6. **Given** `MainScreen` rendered with `AssistantState.Error("algo salió mal")`, **When** inspected, **Then** a status text indicating an error is displayed.
7. **Given** `MainScreen` in any state, **When** inspected for accessibility, **Then** both the "Escuchar" and "Dejar de escuchar" buttons have a non-blank `contentDescription` distinct from each other.
8. **Given** `MainScreen` with `AssistantState.Disarmed`, **When** the "Escuchar" button is clicked, **Then** the `onEscucharClick` callback fires; given `AssistantState.Armed`, clicking "Dejar de escuchar" fires `onDejarDeEscucharClick`.

## Functional Requirements

- **FR-001**: The system MUST add `MainScreenTest.kt` at `app/src/androidTest/kotlin/com/manuel/mvp/ui/MainScreenTest.kt`, pinning the behavioral contract of a not-yet-written, stateless `MainScreen` composable and `AssistantState` sealed type, following the same test-first split used by T005→T006, T007→T008, T009→T010, T012→T013, and T014→T015 — this file will not compile until T020 adds them.
- **FR-002**: The contract under test MUST be a stateless composable `MainScreen(state: AssistantState, onEscucharClick: () -> Unit, onDejarDeEscucharClick: () -> Unit, modifier: Modifier = Modifier)` and a sealed `AssistantState` (`Disarmed`, `Armed`, `Listening`, `Processing`, `Responding`, `Error(message: String)`) in `com.manuel.mvp.ui` — deliberately decoupled from `com.manuel.mvp.pipeline.PipelineState` (T018) so the UI layer doesn't depend on pipeline internals; T020 is responsible for mapping one to the other.
- **FR-003**: The tests MUST render `MainScreen` directly with an explicit `AssistantState` value via Compose's test APIs (`createComposeRule`), not through a real `ConversationPipeline` or `MainActivity` — this is what makes the UI states testable without any hardware/native dependency.
- **FR-004**: The tests MUST assert the "Escuchar" button's enabled state is the inverse of "Dejar de escuchar"'s across at least `Disarmed` and `Armed` (scenarios 1-2) — exactly one of the two is ever actionable at a time.
- **FR-005**: The tests MUST assert a distinct, human-readable status indication exists for each of the five non-error states plus the error state (scenarios 2-6), located via Compose's text-matching test APIs.
- **FR-006**: The tests MUST assert both buttons carry a non-blank, distinct `contentDescription` (scenario 7), located via `onNodeWithContentDescription`.
- **FR-007**: The tests MUST assert both button callbacks fire on click (scenario 8).

## Success Criteria

- **SC-001**: `MainScreenTest.kt` contains at least 8 test cases, one per acceptance scenario above.
- **SC-002**: The test file is written and reviewed against the real, stable `androidx.compose.ui.test`/`androidx.compose.ui.test.junit4` APIs (`createComposeRule`, `onNodeWithText`, `onNodeWithContentDescription`, `assertIsEnabled`/`assertIsNotEnabled`, `performClick`) — it cannot be compiled or run in this sandbox (no Android SDK/emulator, and the Compose UI/test artifacts aren't resolvable without a working Gradle sync, unlike the Robolectric `android-all` jar used for other Android-framework compile-checks). This is a stricter version of the same "written against the real API but not compiled" limitation already applied to the native C++ JNI files (T013/T015).
- **SC-003**: 100% of the acceptance scenarios above (8 of 8) are covered by a named test method.

## Clarifications

- 2026-09-14: **`AssistantState` is a distinct type from `PipelineState`**, even though their cases line up 1:1. This keeps `com.manuel.mvp.ui` free of a dependency on `com.manuel.mvp.pipeline`'s internals (and vice versa) — `MainActivity`/a thin view-model-like adapter (T020) is expected to map `PipelineState` to `AssistantState`, not have `MainScreen` accept `PipelineState` directly.
- 2026-09-14: **No compile/run verification is possible for this file in this sandbox.** Unlike every other Android-framework file so far (verified via a standalone `kotlinc` compile against Robolectric's `android-all` jar, which mirrors real `android.*` framework classes), Jetpack Compose's UI and testing libraries (`androidx.compose.ui`, `androidx.compose.ui.test`, `androidx.activity.compose`) are separate Maven artifacts not present in this sandbox's Gradle cache and not fetchable without a working Gradle sync (which itself needs the Android SDK, per the standing no-SDK constraint). This file is written carefully against the well-established, stable Compose testing API from training knowledge, but is unverified beyond manual review until a real Android dev environment is available.
- 2026-09-14: Status text wording (scenarios 2-6) is a UI-copy choice for T020, not pinned exactly by these tests beyond containing a recognizable Spanish keyword for each state ("armado", "escuchando", "procesando", "respondiendo", an error indicator) — same "pin structure/substring, not exact prose" approach already used for `PromptBuilder.SYSTEM_INSTRUCTIONS` (T014/T015).
