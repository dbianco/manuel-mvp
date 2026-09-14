# Spec: AudioCaptureManager (T011)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), after the wake word "Manuel" is acoustically detected (T010's `WakeWordListener`), I want to record a bounded window of microphone audio for the instruction that follows, stopping automatically once the user finishes speaking (or once it's clear nothing was said), so that the captured audio can be handed to local transcription (T013's `WhisperTranscriber`) without recording indefinitely or requiring the user to press a button to end their turn.

### Acceptance scenarios

1. **Given** a capture has just started, **When** speech is detected and then followed by a configured duration of trailing silence, **Then** capture stops and the manager returns the recorded audio (from the start of the window through the trailing silence).
2. **Given** a capture has just started, **When** no speech is detected at all before the no-speech timeout elapses, **Then** capture stops and the manager returns no audio (`null`) — this is the case that lets a caller apply FR-004 (silence with no instruction → return to keyword-waiting, no error, no visible failed turn) without ever invoking STT.
3. **Given** a capture is in progress with speech ongoing (never a full trailing-silence gap), **When** the maximum capture duration is reached, **Then** capture stops and the manager returns whatever audio was recorded up to that point (a hard safety cap, not a silent discard, since speech was in fact detected).
4. **Given** the maximum capture duration is reached and no speech was ever detected during the whole window, **When** the cap fires, **Then** the manager returns no audio (`null`) — the hard cap does not turn "nothing was said" into "return empty audio anyway".
5. **Given** the underlying decision logic (elapsed time + a per-frame speech/silence signal), **When** driven through a sequence of frames via unit tests, **Then** it produces exactly the continue/stop-with-audio/stop-silently decisions described by scenarios 1-4, independent of any real microphone or Android framework class.

## Functional Requirements

- **FR-001**: The system MUST add `AudioCaptureManager.kt` at `app/src/main/kotlin/com/manuel/mvp/audio/AudioCaptureManager.kt`, recording microphone audio via `android.media.AudioRecord` at 16 kHz, mono, 16-bit PCM — matching both openWakeWord's own recording format (`AudioRecorder.SAMPLE_RATE = 16000`, confirmed in the T003 dependency's compiled classes) and whisper.cpp's expected input format, so T013 needs no resampling.
- **FR-002**: The system MUST expose a suspend function that starts recording and returns the captured audio as a `ShortArray` (raw PCM16 samples) when the window ends with detected speech, or `null` when it ends without any detected speech (FR-004's "silencio... sin producir una respuesta hablada").
- **FR-003**: The stop decision MUST be driven by a pure, Android-independent policy component that: (a) stops with audio once a configured duration of trailing silence follows the first detected speech; (b) stops silently (no audio) if no speech is detected within a configured no-speech timeout; (c) stops at a hard maximum capture duration regardless of (a)/(b), returning audio if any speech was ever detected during the window, or `null` otherwise.
- **FR-004**: The per-frame speech/silence classification MUST be provided by a pure, Android-independent function operating on raw PCM16 sample data (an energy/RMS threshold check), separate from the stop-decision policy in FR-003 — each is independently unit-testable without a real microphone.
- **FR-005**: `AudioCaptureManager` MUST release its `AudioRecord` instance (`stop()`/`release()`) in a `finally` block so a cancelled or failed capture never leaks the microphone resource.
- **FR-006**: The system MUST NOT perform any transcription, keyword parsing, or network activity in this feature — it only captures and returns raw audio samples; T013 (`WhisperTranscriber`) and T009/T010's `KeywordPrefixParser` remain the consumers of its output.

## Success Criteria

- **SC-001**: A new unit test file for the pure stop-decision policy (FR-003) covers all 4 acceptance scenarios above (continue, stop-with-audio on trailing silence, stop-silently on no-speech timeout, hard-cap-with-audio, hard-cap-without-audio — 5 distinct named cases for the 4 scenarios, since the hard cap has two outcomes depending on prior speech), runnable as a plain JVM JUnit test with no Android dependency.
- **SC-002**: A new unit test file for the pure speech/silence energy classifier (FR-004) covers at least: a clearly loud frame classified as speech, a clearly quiet/silent frame classified as non-speech, and the configured threshold boundary.
- **SC-003**: `AudioCaptureManager.kt` compiles cleanly against the real `android.media.AudioRecord`/`android.media.MediaRecorder`/`android.media.AudioFormat` classes and `kotlinx-coroutines-core`, verified via a standalone compile (this sandbox has no Android SDK, so this substitutes for `./gradlew build`), following the precedent set for `WakeWordListener.kt` (T010).
- **SC-004**: All new pure-logic unit tests pass (0 failures), verified via a JVM-runnable check (standalone `kotlinc` + `JUnitCore`, per the project's no-SDK constraint).

## Clarifications

- 2026-09-14: `AudioCaptureManager` opens its own `AudioRecord` instance rather than reusing openWakeWord's internal `AudioRecorder` (used by `WakeWordEngine`/`WakeWordListener` for continuous background keyword listening). The library's `AudioRecorder` is a private implementation detail of `WakeWordEngine` (not exposed on the class), and semantically the two recordings serve different purposes — continuous low-level keyword scanning vs. a bounded post-detection instruction capture — so they are naturally separate `AudioRecord` sessions. `WakeWordListener.disarm()` (T010) is expected to be called by the future pipeline (T018) before `AudioCaptureManager` starts recording, so the two never contend for the microphone at once; enforcing that ordering is out of scope for this feature (it belongs to T018's orchestration).
- 2026-09-14: The trailing-silence-ends-capture / no-speech-timeout / hard-max-duration policy is a simple, deliberately conservative design for the MVP — no dedicated VAD model (e.g. Silero VAD) is used, only an energy/RMS threshold, consistent with the project's on-device, dependency-light approach elsewhere (SQLite/FTS5 lexical search instead of vector search, openWakeWord instead of a heavier engine). Default durations (trailing silence, no-speech timeout, max duration) are left as constructor parameters with reasonable defaults, not fixed acceptance numbers — field tuning happens during T022's manual test protocol, same as SC-010/SC-011's provisional thresholds.
