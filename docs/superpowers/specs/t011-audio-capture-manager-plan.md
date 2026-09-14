# Plan: AudioCaptureManager (T011)

## Technical Context

- Language: Kotlin, `app/src/main/kotlin/com/manuel/mvp/audio/` (main) and `app/src/test/kotlin/com/manuel/mvp/audio/` (new pure-logic tests).
- Primary dependencies: `android.media.AudioRecord`/`AudioFormat`/`MediaRecorder` (Android framework, no new Gradle dependency), `kotlinx-coroutines-core` (already transitively present via T003's openWakeWord dependency) for the suspend capture function. No new library dependency needed.
- Storage: none — audio is returned in-memory as a `ShortArray`, never written to disk.
- Testing tools: JUnit 4 for the two pure components (`CaptureWindowPolicy`, the RMS speech classifier). `AudioCaptureManager` itself (the `AudioRecord`-driving orchestrator) has no dedicated unit test — verified instead by a standalone compile against the real Android/coroutines classes, same precedent as T010's `WakeWordListener`.
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`. `RECORD_AUDIO` permission is assumed already required by the app (openWakeWord's own `AudioRecorder.hasRecordPermission()` implies the manifest/runtime permission story already exists from T003); this feature does not re-request or re-declare it.
- Performance goals and constraints: capture must not block the calling coroutine's thread — recording runs via `AudioRecord.read()` in a loop inside a suspend function, expected to be called from a background dispatcher by its caller (T018), matching how `WakeWordListener`'s `CoroutineScope` is owned by the caller rather than internally.

## Constitution Check

- No personal data in logs or error messages: `AudioCaptureManager` does not log audio content or transcripts (it doesn't transcribe); only raw PCM samples are returned to the caller. Satisfied.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — no network calls, fully on-device.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — new internal types only.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved.
- Tests run in CI before merge and a red build blocks the merge: `CaptureWindowPolicyTest.kt` and the speech-classifier test become part of the T021 CI gate once configured; `AudioCaptureManager.kt` itself has no automated test (same as `WakeWordListener`), field-validated per T022.
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/audio/
├── CaptureWindowPolicy.kt      # new: pure stop-decision state machine (Continue/StopWithAudio/StopSilently)
├── SpeechEnergyDetector.kt     # new: pure RMS-based per-frame speech/silence classifier
└── AudioCaptureManager.kt      # new: AudioRecord-driven orchestrator wiring the two pure pieces together

app/src/test/kotlin/com/manuel/mvp/audio/
├── CaptureWindowPolicyTest.kt      # new: JUnit tests for the stop-decision policy (SC-001)
└── SpeechEnergyDetectorTest.kt     # new: JUnit tests for the RMS speech classifier (SC-002)
```

No existing files are modified except this plan/spec/tasks documentation and `docs/superpowers/specs/tasks.md` (marking T011 done).

## Research

- **Why extract `CaptureWindowPolicy` and `SpeechEnergyDetector` as separate pure components**: this mirrors the project's established pattern (`FragmentRowSource` for SQL access in T005/T006, `KeywordPrefixParser` for text matching in T009/T010) of pulling deterministic decision logic out of an Android-framework-backed wrapper so it can be unit-tested directly, without mocking hardware. `AudioCaptureManager` itself stays a thin orchestrator with no branching logic of its own to get wrong untested.
- **`CaptureWindowPolicy` shape**: a small state machine driven by `onFrame(elapsedMs: Long, isSpeech: Boolean): CaptureDecision` where `CaptureDecision` is a 3-case sealed type (`Continue`, `StopWithAudio`, `StopSilently`). Internally tracks only "has speech ever started" and "elapsed time since the last speech frame" — both derivable from the `elapsedMs`/`isSpeech` sequence the caller feeds it, so no `Clock`/timer dependency is needed (unlike `SessionMemory`'s `Clock`-based lazy timeout, which needed wall-clock awareness across separate calls; here the caller already tracks elapsed time per audio frame it reads).
- **`SpeechEnergyDetector` shape**: a pure function over `ShortArray` PCM16 samples computing RMS energy and comparing it to a configurable threshold — no Android dependency (`ShortArray`/`Double` only), so it's `kotlinc`+`JUnitCore`-testable exactly like `KeywordPrefixParser`.
- **Audio format (16 kHz mono PCM16)**: confirmed by inspecting the compiled `xyz.rementia:openwakeword:0.1.5` classes via `javap -constants` — `AudioRecorder.SAMPLE_RATE = 16000`, `CHANNEL_CONFIG = 16` (`AudioFormat.CHANNEL_IN_MONO`), `AUDIO_FORMAT = 2` (`AudioFormat.ENCODING_PCM_16BIT`). Matching this format avoids any resampling step before T013's Whisper bridge, and Whisper.cpp's own standard input format is 16 kHz mono PCM.
- **Not reusing openWakeWord's internal `AudioRecorder`**: it's a private implementation detail of `WakeWordEngine` (constructed internally, not exposed), and its `Flow<float[]>` output is normalized `Float` samples for ONNX inference — a different representation than the raw `ShortArray` PCM16 this feature wants to hand to Whisper. A second, independent `AudioRecord` session is simpler and keeps the two concerns (continuous keyword scanning vs. bounded instruction capture) decoupled, as already noted in spec.md's Clarifications.
- **Alternative considered and rejected**: a dedicated VAD model (e.g. Silero VAD via ONNX, already available in the dependency tree through onnxruntime-android). Rejected for the MVP as unnecessary complexity — an RMS energy threshold is simple, has no extra model asset to ship or license, and is consistent with this project's repeated preference for the lighter-weight option (lexical FTS5 over vector search, openWakeWord over a heavier wake-word stack). Can be revisited later if field testing (T022) shows the energy threshold is unreliable in classroom noise.
