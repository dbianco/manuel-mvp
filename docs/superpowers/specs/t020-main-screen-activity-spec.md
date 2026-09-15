# Spec: MainScreen, AssistantState, MainActivity (T020)

## User Scenarios

### Primary user story

As Manuel, I want a single screen with the two explicit control buttons and a visible status (FR-001), wired to the real conversational pipeline (T018), so a user can arm/disarm the assistant and see what it's doing without any other manual step.

### Acceptance scenarios

1. **Given** `MainScreenTest.kt` (T019, unmodified), **When** run against this feature's `MainScreen`/`AssistantState`, **Then** all 9 existing test methods pass.
2. **Given** the app launches, **When** `MainActivity` starts, **Then** it does not crash even if the whisper/llama model files are missing from internal storage — construction failures surface as `AssistantState.Error`, not an app crash.
3. **Given** the "Escuchar" button is pressed, **When** `RECORD_AUDIO` isn't yet granted, **Then** the system permission dialog is requested before `ConversationPipeline.arm()` is called.
4. **Given** permission is granted (or was already granted), **When** "Escuchar" is pressed, **Then** `ConversationPipeline.arm()` is called and the screen's state tracks `ConversationPipeline.state` (mapped `PipelineState` → `AssistantState`).
5. **Given** "Dejar de escuchar" is pressed, **When** handled, **Then** `ConversationPipeline.disarm()` is called.

## Functional Requirements

- **FR-001**: The system MUST add `AssistantState.kt` and `MainScreen.kt` at `app/src/main/kotlin/com/manuel/mvp/ui/`, implementing exactly the contract already pinned by T019's `MainScreenTest.kt` (stateless `MainScreen(state, onEscucharClick, onDejarDeEscucharClick, modifier)`; `AssistantState` sealed type with `Disarmed`/`Armed`/`Listening`/`Processing`/`Responding`/`Error(message)`), without modifying that test file.
- **FR-002**: Both buttons MUST carry a `contentDescription` equal to their own visible label ("Escuchar"/"Dejar de escuchar"), satisfying T019's accessibility assertions and the company's "interactive elements are keyboard reachable and labelled" standard.
- **FR-003**: `MainActivity.kt` MUST construct every `ConversationPipeline` collaborator (T006-T017) and wire them into one `ConversationPipeline`, mapping its `StateFlow<PipelineState>` (T018) into `AssistantState` for `MainScreen` to render.
- **FR-004**: Pipeline construction MUST be wrapped so a missing/invalid model file (whisper or llama GGUF — neither is bundled, per T013/T015's "packaging is out of scope" clarifications) surfaces as `AssistantState.Error`, not an uncaught exception crashing the activity.
- **FR-005**: The system MUST declare the `android.permission.RECORD_AUDIO` runtime permission in `AndroidManifest.xml` and request it via the modern Activity Result API before the first `arm()` call, if not already granted — this permission was not yet declared anywhere in the project (verified by inspection: `AndroidManifest.xml` had no `<uses-permission>` entries at all before this task), correcting an unverified assumption recorded in T016's plan.md.
- **FR-006**: `onEscucharClick` MUST call `ConversationPipeline.arm()` (only after RECORD_AUDIO is granted); `onDejarDeEscucharClick` MUST call `ConversationPipeline.disarm()`.
- **FR-007**: `MainScreenTest.kt` (T019) MUST NOT be modified — it is the authoritative behavioral contract for `MainScreen`/`AssistantState`.

## Success Criteria

- **SC-001**: All 9 test methods in `MainScreenTest.kt` (T019) pass once `MainScreen.kt`/`AssistantState.kt` are added — cannot be verified by compiling in this sandbox (same limitation T019 already documented for Compose artifacts); verified instead by careful manual review confirming every matcher T019 uses (text, contentDescription, enabled state) has a corresponding, correctly-labelled element in `MainScreen`.
- **SC-002**: `MainActivity.kt` is reviewed against the real, stable Jetpack Compose/Activity Result API (same unverifiable-by-compiler tier as T019, see its Clarifications) and against every real collaborator constructor signature already implemented in T006-T018 (checked by re-reading each file), to catch integration-time signature mismatches by inspection since no compiler is available for this package.
- **SC-003**: `AndroidManifest.xml` gains exactly one new `<uses-permission>` line, reviewed for well-formed XML.

## Clarifications

- 2026-09-14: **Lazy, async, error-caught pipeline construction.** `MainActivity` builds the `ConversationPipeline` inside a `LaunchedEffect` (on a background dispatcher, since several collaborator constructors do blocking I/O or JNI init), catching any exception into `AssistantState.Error` rather than crashing at launch — this matters concretely today, since neither the whisper/llama GGUF models nor the trained `manuel.onnx` wake-word model exist yet in this repository (T003/T013/T015's standing notes), so a fresh checkout's `MainActivity` is expected to show an error state until those files are provisioned, not crash.
- 2026-09-14: **RECORD_AUDIO permission gap found and fixed.** T016's plan.md had assumed "openWakeWord's own `hasRecordPermission()` implies the manifest/runtime permission story already exists from T003" — checking `AndroidManifest.xml` during this task showed that was wrong: no `<uses-permission>` was ever declared. This task adds the manifest declaration and the runtime request flow, since `MainActivity`/`MainScreen`'s "Escuchar" button is the first and only place in the app that actually needs microphone access.
- 2026-09-14: **No dedicated `ViewModel` layer.** State is held directly in the composable via `remember { mutableStateOf(...) }` and updated from a `LaunchedEffect`-collected `Flow` — a `ViewModel` would be the more conventional Android architecture for surviving configuration changes cleanly, but is not required by any functional requirement here, and adding one is a straightforward, low-risk refactor to make later if `MainActivity`'s state-restoration behavior proves inadequate during field testing (T022).
