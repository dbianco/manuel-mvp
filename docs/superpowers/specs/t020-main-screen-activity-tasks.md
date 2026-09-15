# Tasks: MainScreen, AssistantState, MainActivity (T020)

## Tasks

- [ ] T001 Implement `AssistantState.kt` (`Disarmed`/`Armed`/`Listening`/`Processing`/`Responding`/`Error(message)`) in `app/src/main/kotlin/com/manuel/mvp/ui/AssistantState.kt`
- [ ] T002 Implement `MainScreen.kt` (stateless composable, status text per state, two buttons with matching `contentDescription`, enabled-state inversion) in `app/src/main/kotlin/com/manuel/mvp/ui/MainScreen.kt` to satisfy `MainScreenTest.kt` (T019), depends on T001
- [ ] T003 Review `MainScreen.kt`/`AssistantState.kt` against every matcher in `MainScreenTest.kt` line-by-line, confirming SC-001, depends on T002
- [ ] T004 Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to `app/src/main/AndroidManifest.xml`
- [ ] T005 Implement `MainActivity.kt` (constructs every `ConversationPipeline` collaborator, `LaunchedEffect`-based async/error-caught construction, RECORD_AUDIO runtime request, `PipelineState`→`AssistantState` mapping, renders `MainScreen`), depends on T002, T004
- [ ] T006 Cross-reference every collaborator construction call in `MainActivity.kt` against its real constructor signature (re-reading each source file), depends on T005
