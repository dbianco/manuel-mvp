# Spec: WakeWordListener + KeywordPrefixParser implementation (T010)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), while armed ("Escuchar"), I want to run an on-device acoustic detector for the wake word "Manuel" in the background, and validate the transcript captured after each detection against the keyword-prefix contract, so that the assistant only reacts to audio that was clearly directed at it (FR-002/FR-003), and silently returns to waiting — without a spoken response or a visible failed turn — when the keyword fires but no clear instruction follows (FR-004).

### Acceptance scenarios

1. **Given** the app is armed, **When** `WakeWordListener.arm()` is called, **Then** the underlying openWakeWord engine starts listening in the background for the "Manuel" model.
2. **Given** the wake word has been detected, **When** the resulting STT transcript is passed to `WakeWordListener.resolveInstruction(transcript)`, **Then** it returns the same result `KeywordPrefixParser.parse(transcript)` would — extracted instruction, or `null` for a keyword-less or instruction-less transcript.
3. **Given** the app is armed, **When** `WakeWordListener.disarm()` is called ("Dejar de escuchar"), **Then** the underlying engine stops and any in-flight background listening ends — matching FR-001's requirement that "Dejar de escuchar" halts any capture in progress.
4. **Given** `KeywordPrefixParser` (implemented by this feature), **When** run against `KeywordPrefixParserTest.kt` (T009, unmodified), **Then** all 7 existing test cases pass.

## Functional Requirements

- **FR-001**: The system MUST add `KeywordPrefixParser.kt` at `app/src/main/kotlin/com/manuel/mvp/audio/KeywordPrefixParser.kt`, implementing exactly the contract already pinned by T009's `KeywordPrefixParserTest.kt` (`parse(transcript: String): String?`), without modifying that test file.
- **FR-002**: The system MUST add `WakeWordListener.kt` at `app/src/main/kotlin/com/manuel/mvp/audio/WakeWordListener.kt`, wrapping `com.rementia.openwakeword.lib.WakeWordEngine` (the `xyz.rementia:openwakeword:0.1.5` dependency already declared in T003) configured with a single `WakeWordModel` pointing at the "Manuel" keyword asset (`wakeword/manuel.onnx`, per T003's asset convention).
- **FR-003**: `WakeWordListener` MUST expose `arm()` (starts the engine, satisfying FR-002's "mientras está armado... correr un detector... en background") and `disarm()` (stops the engine, satisfying FR-001's "detiene cualquier captura en curso").
- **FR-004**: `WakeWordListener` MUST expose the engine's detection stream (`Flow<WakeWordDetection>`) so a caller (a future pipeline component, T018) can react to an acoustic wake-word detection by starting audio capture — this feature does not itself capture or transcribe audio (that's T011/T013).
- **FR-005**: `WakeWordListener` MUST expose a `resolveInstruction(transcript: String): String?` function that delegates directly to `KeywordPrefixParser.parse`, so callers have one entry point per FR-003/FR-004's textual validation, without duplicating the parsing logic inline.
- **FR-006**: The system MUST NOT modify `app/src/test/kotlin/com/manuel/mvp/audio/KeywordPrefixParserTest.kt` — it is the authoritative behavioral contract from T009.
- **FR-007**: `WakeWordListener` MUST NOT transcribe or otherwise process audio content itself (no STT, no LLM calls) — it only detects the acoustic keyword and validates already-transcribed text, keeping FR-005's transcription concern (T013) separate.

## Success Criteria

- **SC-001**: All 7 test cases in `KeywordPrefixParserTest.kt` (T009) pass once `KeywordPrefixParser.kt` is added, verified via a JVM-runnable check (standalone `kotlinc` + `JUnitCore`, per [[manuel-mvp.constraint — no Android SDK]], or `./gradlew test` once the SDK is available).
- **SC-002**: `WakeWordListener.kt` compiles cleanly against the real `openwakeword-0.1.5.aar` classes, `kotlinx-coroutines-core`, and an Android class surface (`android.content.Context`) — verified via a standalone `kotlinc` compile against the cached dependency jars (Robolectric's `android-all` jar standing in for `android.jar`, since no Android SDK is present in this sandbox), not merely by inspection.
- **SC-003**: Neither new file references Whisper, llama.cpp, or any LLM/RAG API — confirmed by grep, keeping this feature scoped to FR-002/FR-003/FR-004 only.

## Clarifications

- 2026-09-14: `WakeWordListener` wraps `WakeWordEngine` (not `ParallelWakeWordEngine`, the other public engine class in the openwakeword AAR) because the MVP has exactly one keyword model ("Manuel") — `ParallelWakeWordEngine` exists in the library for running multiple models concurrently, which this app doesn't need.
- 2026-09-14: `DetectionMode.SINGLE_BEST` is used (the library's other option, `ALL`, is for multi-model setups) — with one model configured, both modes are behaviorally equivalent, but `SINGLE_BEST` states the single-keyword intent explicitly.
- 2026-09-14: Per plan.md and T009's spec, the acoustic detection (`WakeWordEngine`'s `Flow<WakeWordDetection>`) and the textual keyword-prefix validation (`KeywordPrefixParser`) remain two independent, composable pieces inside `WakeWordListener` — the former is non-deterministic/hardware-dependent, the latter is pure and already fully unit-tested by T009. `resolveInstruction` is a thin pass-through, not a merge of the two mechanisms.
