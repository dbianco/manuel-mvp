# Tasks: AudioCaptureManager (T011)

## Tasks

- [ ] T001 Write `CaptureWindowPolicyTest.kt` covering the 5 named cases (continue, stop-with-audio on trailing silence, stop-silently on no-speech timeout, hard-cap-with-audio, hard-cap-without-audio) in `app/src/test/kotlin/com/manuel/mvp/audio/CaptureWindowPolicyTest.kt`
- [ ] T002 Implement `CaptureWindowPolicy.kt` (pure state machine, `onFrame(elapsedMs, isSpeech): CaptureDecision`) in `app/src/main/kotlin/com/manuel/mvp/audio/CaptureWindowPolicy.kt` to satisfy T001, depends on T001
- [ ] T003 Write `SpeechEnergyDetectorTest.kt` covering a loud/speech frame, a quiet/non-speech frame, and the threshold boundary in `app/src/test/kotlin/com/manuel/mvp/audio/SpeechEnergyDetectorTest.kt`
- [ ] T004 Implement `SpeechEnergyDetector.kt` (pure RMS-based classifier over `ShortArray` PCM16) in `app/src/main/kotlin/com/manuel/mvp/audio/SpeechEnergyDetector.kt` to satisfy T003, depends on T003
- [ ] T005 Implement `AudioCaptureManager.kt` (wraps `android.media.AudioRecord` at 16kHz mono PCM16, drives `CaptureWindowPolicy` + `SpeechEnergyDetector` per read frame, releases the recorder in `finally`) in `app/src/main/kotlin/com/manuel/mvp/audio/AudioCaptureManager.kt`, depends on T002, T004
- [ ] T006 Compile `AudioCaptureManager.kt` standalone against the real `android.media.*`/`kotlinx-coroutines-core` classes, confirming no API mismatch, depends on T005
