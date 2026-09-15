# Plan: ConversationPipeline (T018)

## Technical Context

- Language: Kotlin, `app/src/main/kotlin/com/manuel/mvp/pipeline/`.
- Primary dependencies: every component built T006-T017 (no new library dependency); `kotlinx-coroutines-core` (`CoroutineScope`, `Job`, `StateFlow`/`MutableStateFlow`, `launch`), already transitively present via T003.
- Storage: none directly — delegates to `SessionMemory`/`LocalMetricsLogger`, both already in-memory-only.
- Testing tools: no dedicated unit test (see spec.md's Clarifications) — verified via one combined standalone compile against every real collaborator class.
- Target platform: `com.manuel.mvp`, `minSdk = 29`.
- Performance: not benchmarked here; SC-005's ≤15s end-to-end budget is a T022 field-test concern.

## Constitution Check

- No personal data in logs: `ConversationPipeline` never logs raw audio or question/answer text itself — it only passes them between collaborators and logs via `LocalMetricsLogger`, whose types structurally exclude that data (T017). The one risk is `Error(message)`/`TurnMetrics.error` carrying an exception message that happens to echo back input text (e.g. a parsing exception including the offending string) — mitigated by only ever passing `e.message` (a library/runtime-authored string), never re-embedding `instruction`/`response` into an error message directly.
- HTTP timeouts: not applicable — FR-010 requires (and this task verifies via grep) no network code.
- DB migrations / public API / secrets: not applicable, consistent with every prior task in this stack.
- CI tests gate merge: no test to gate on for this file specifically; the CI-visible tests are all the ones its collaborators already have.
- Accessibility: not applicable, no UI surface (T020 owns that).

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/pipeline/
├── PipelineState.kt          # new: Disarmed/Armed/Listening/Processing/Responding/Error sealed type
└── ConversationPipeline.kt   # new: the orchestrator described in spec.md
```

## Research

- **Constructor injection of every collaborator, plus an external `CoroutineScope`**: matches `WakeWordListener`'s established convention (T010) of taking lifecycle-owning dependencies from the caller rather than constructing them internally — keeps `ConversationPipeline` itself free of Android `Context`/lifecycle concerns; the caller (T020's `MainActivity`/`MainScreen`) is responsible for constructing every collaborator with what it needs (a `Context`, model file paths, etc.) and wiring them all into one `ConversationPipeline`.
- **`PipelineState` as its own file** (not nested in `ConversationPipeline`): mirrors `CaptureDecision`/`TranscriptionOutcome`'s top-level sealed-type placement (T011/T012), keeping the public state contract easy for T020 to reference without pulling in the whole orchestrator's implementation.
- **Sequential try/catch scope**: the entire `handleDetection()` body is wrapped in one `try`/`catch (e: Exception)`, not per-stage try/catches — a failure at any stage means the turn as a whole failed, and FR-009 only requires *a* `TurnMetrics.error` entry and an `Error` state, not per-stage error attribution (which stage failed can still often be inferred from `e.message`/the exception type during manual review or field testing, T022).
- **No collaborator interfaces retrofitted for testability**: considered and explicitly rejected for this task's scope — see spec.md's Clarifications. Flagged as a possible follow-up if pipeline-level test coverage becomes a priority later.
- **Verification via one big combined compile**: reuses every classpath fragment already assembled across T009-T017 (openwakeword/onnxruntime/kotlinx-coroutines jars, Robolectric's `android-all` jar) in a single `kotlinc` invocation compiling `ConversationPipeline.kt` together with all its real collaborator `.kt` files — the most thorough compile-time check available in this sandbox (no Android SDK/NDK).
