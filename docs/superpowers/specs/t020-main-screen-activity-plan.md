# Plan: MainScreen, AssistantState, MainActivity (T020)

## Technical Context

- Language: Kotlin, `app/src/main/kotlin/com/manuel/mvp/ui/` (new) + `app/src/main/kotlin/com/manuel/mvp/MainActivity.kt` (replacing T001's placeholder) + `app/src/main/AndroidManifest.xml`.
- Primary dependencies: `androidx.compose.material3`/`.ui` (already declared), `androidx.activity.compose` (already declared, for `setContent`/`rememberLauncherForActivityResult`), `androidx.lifecycle:lifecycle-runtime-ktx` (already declared, for `lifecycleScope`). No new Gradle dependency.
- Storage: none directly — `MainActivity` wires `ContentDatabase`/`SessionMemory`/etc., which own their own storage.
- Testing tools: `MainScreen`/`AssistantState` verified against T019's existing `MainScreenTest.kt` (unverifiable by compiler in this sandbox, per T019's own Clarifications — Compose artifacts aren't resolvable without a working Gradle sync). `MainActivity.kt` has no automated test of its own — it's pure wiring, verified by manual cross-reference against every real collaborator's constructor.
- Target platform: `com.manuel.mvp`, `minSdk = 29`.

## Constitution Check

- No personal data in logs: `MainActivity`'s only error surface (`AssistantState.Error(e.message)`) carries exception messages, same reasoning already accepted for `ConversationPipeline` (T018).
- HTTP/DB migrations/public API/secrets: not applicable, consistent with the rest of this stack.
- CI tests gate merge: `MainScreenTest.kt` (T019) is the only automated coverage in this area; `MainActivity.kt` has none.
- Accessibility: directly satisfied by FR-002 (`contentDescription` on both buttons) — the company constitution's standard for this screen.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/ui/
├── AssistantState.kt   # new: Disarmed/Armed/Listening/Processing/Responding/Error(message)
└── MainScreen.kt        # new: the stateless composable T019 tests

app/src/main/kotlin/com/manuel/mvp/MainActivity.kt   # modified: replaces T001's placeholder
app/src/main/AndroidManifest.xml                       # modified: adds RECORD_AUDIO permission
```

## Research

- **Collaborator wiring cross-referenced against real constructors**: `ContentDatabase.create(context)` + `ContentDao(contentDatabase.readableDatabase)` + `FragmentSearcher(contentDao)` (T006); `WakeWordListener(context, scope)` (T010, defaults its model path to `wakeword/manuel.onnx`); `AudioCaptureManager()` (T011, no `Context` needed — pure `AudioRecord` usage); `NativeWhisperEngine(modelPath)` + `WhisperTranscriber(engine)` (T012/T013); `SessionMemory(clock = Clock.systemUTC())` (T007/T008); `PromptBuilder()` (T014/T015, no-arg); `LlamaEngine(modelPath)` (T015); `SpeechSynthesizer(context)` (T016); `LocalMetricsLogger()` (T017); `ConversationPipeline(...)` (T018, takes all of the above plus a `CoroutineScope`). Every one of these was re-read from its actual source file during this task (not from memory of having written it) to catch any signature drift.
- **Model file paths**: `File(filesDir, "models/ggml-tiny.bin")` (whisper) and `File(filesDir, "models/llama-3.2-3b-instruct-q4_k_m.gguf")` (llama) — internal-storage locations a human/first-launch download step is expected to populate (plan.md's stated packaging approach), matching T013/T015's "packaging is out of scope" stance; `MainActivity` only needs *a* path, not to provision the file.
- **`LaunchedEffect(Unit)` for async, error-caught construction**: runs once when `MainScreen`'s hosting composable enters composition, on `Dispatchers.IO` (since `ContentDatabase.create`, `NativeWhisperEngine`/`LlamaEngine`'s JNI init are blocking calls) — building the pipeline in a coroutine avoids blocking the UI thread, and wrapping it in `try`/`catch` turns a missing-model-file exception into a visible `AssistantState.Error` instead of a launch-time crash (FR-004).
- **RECORD_AUDIO via the modern Activity Result API**: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`, triggered from `onEscucharClick` when `ContextCompat.checkSelfPermission(...)` reports not-yet-granted; `ConversationPipeline.arm()` is only called once granted (either already, or via the launcher's callback). This is the currently-recommended replacement for the older `requestPermissions`/`onRequestPermissionsResult` callback pair.
- **No `ViewModel`**: considered and deferred — see spec.md's Clarifications.
