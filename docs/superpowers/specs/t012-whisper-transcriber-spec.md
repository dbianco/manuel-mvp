# Spec: WhisperTranscriber unit tests (T012)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), after capturing the post-wake-word instruction audio (T011's `AudioCaptureManager`), I want to transcribe it locally with whisper.cpp and accept the result only when the transcription confidence is high enough, so that a low-confidence guess is never treated as the user's instruction — instead the app asks the user to repeat themselves (FR-005, spec.md acceptance scenario 5), rather than acting on a dubious transcription.

### Acceptance scenarios

1. **Given** the native engine returns a transcription with confidence at or above the configured threshold, **When** `WhisperTranscriber.transcribe(audio)` is called, **Then** it returns the transcribed text unchanged.
2. **Given** the native engine returns a transcription with confidence exactly equal to the configured threshold, **When** transcribed, **Then** the result is still accepted (the threshold is inclusive — "insuficiente" means strictly below it).
3. **Given** the native engine returns a transcription with confidence below the configured threshold, **When** transcribed, **Then** `WhisperTranscriber` signals that the user should repeat the instruction, and does NOT return the (unreliable) transcribed text.
4. **Given** a very low confidence (e.g. 0.0, "no meaningful transcription possible"), **When** transcribed, **Then** the same repeat-request outcome applies, not a crash or an empty-string "success".
5. **Given** `WhisperTranscriber` constructed with a custom (non-default) confidence threshold, **When** transcribed with a confidence between the default and the custom threshold, **Then** the outcome reflects the *custom* threshold used at construction, not a hardcoded default.
6. **Given** any call to `transcribe(audio)`, **When** it runs, **Then** the exact same audio array is passed through to the underlying engine unchanged (no re-encoding, no truncation) — `WhisperTranscriber` only makes the accept/repeat decision, it doesn't touch the audio data itself.

## Functional Requirements

- **FR-001**: The system MUST define `WhisperTranscriberTest.kt` at `app/src/test/kotlin/com/manuel/mvp/stt/WhisperTranscriberTest.kt`, pinning the behavioral contract of a not-yet-written `WhisperTranscriber`, following the same test-first split used by T005→T006, T007→T008, and T009→T010.
- **FR-002**: The contract under test MUST separate the untestable native boundary (an actual whisper.cpp JNI call, requiring the real vendored library and a loaded GGUF model — out of reach for a JVM unit test) from the confidence-threshold decision logic that sits in front of it, via an injectable seam interface (referred to generically here as a "whisper engine" contract: given raw PCM16 audio, returns transcribed text plus a confidence score). `WhisperTranscriber` itself MUST be constructible and callable as a plain JVM unit test, with a fake implementation of that seam supplying canned results — no real JNI, no real model file, no Android dependency.
- **FR-003**: The tests MUST assert that a confidence at or above the constructed threshold returns the engine's transcribed text unchanged (inclusive boundary, scenarios 1-2).
- **FR-004**: The tests MUST assert that a confidence below the constructed threshold produces a distinct "ask the user to repeat" outcome rather than the (unreliable) transcribed text (scenarios 3-4) — matching FR-005 of the top-level spec ("DEBE pedir que se repita la instrucción cuando la confianza de transcripción sea insuficiente").
- **FR-005**: The tests MUST assert that the confidence threshold is configurable per instance (constructor parameter), not a single hardcoded global constant, and that the configured value — not any default — is what the accept/repeat decision is evaluated against (scenario 5).
- **FR-006**: The tests MUST assert that the audio array handed to `transcribe(...)` is passed through to the injected engine seam unmodified (scenario 6) — confirming `WhisperTranscriber`'s only job here is the accept/repeat decision, not audio manipulation.
- **FR-007**: The tests MUST NOT depend on Android framework classes, the real whisper.cpp JNI bridge, or any model asset — this is a pure JVM/Kotlin contract, runnable as a plain JUnit test (matching the test-first convention already used for `session`, `rag`, and `audio`).

## Success Criteria

- **SC-001**: `WhisperTranscriberTest.kt` contains at least 6 test cases, one per acceptance scenario above, each independently runnable and each asserting a single expected outcome.
- **SC-002**: The test file compiles standalone against JUnit 4 (already declared in `app/build.gradle.kts`) once a matching `WhisperTranscriber` (and its engine seam interface) exist; it is expected to fail to compile until T013 adds them, same "red by construction" state left by T005, T007, and T009 for their respective implementation tasks.
- **SC-003**: 100% of the acceptance scenarios above (6 of 6) are covered by a named test method, verifiable by inspection of the file's test method names against this list.

## Clarifications

- 2026-09-14: The confidence-threshold boundary is inclusive (`>=`) at the threshold value itself — a confidence exactly equal to the threshold is accepted, not treated as "insufficient". This mirrors the same `>=` convention already used for `SessionMemory`'s inactivity timeout (T007/T008), applied here for consistency across the codebase's threshold-style contracts, even though the top-level spec's Spanish wording ("confianza... sea insuficiente") doesn't itself pin the boundary.
- 2026-09-14: The injectable "whisper engine" seam (FR-002) is this feature's version of the pattern already used for `FragmentRowSource` (T005/T006) and (implicitly, via constructor-injected `Context`/`CoroutineScope`) `WakeWordListener` (T009/T010): pull the part that needs real native code, hardware, or a loaded model out from under the part with actual decision logic, so the decision logic gets real JUnit coverage. T013 is expected to provide the real JNI-backed implementation of this seam; this task only defines and tests the contract around it.
- 2026-09-14: This feature covers only the confidence-threshold decision (the "pedido de repetición" — request-to-repeat — half of FR-005). Actually invoking whisper.cpp via JNI, loading the GGUF model, and producing a real confidence score from real audio are T013's responsibility, not testable here without the native SDK.
