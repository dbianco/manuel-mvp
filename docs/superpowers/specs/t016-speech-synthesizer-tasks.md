# Tasks: SpeechSynthesizer (T016)

## Tasks

- [ ] T001 Write `SpanishVoiceCheckerTest.kt` (Spanish-only locale, Spanish among others, no Spanish, empty set) in `app/src/test/kotlin/com/manuel/mvp/tts/SpanishVoiceCheckerTest.kt`
- [ ] T002 Implement `SpanishVoiceChecker.kt` in `app/src/main/kotlin/com/manuel/mvp/tts/SpanishVoiceChecker.kt` to satisfy T001, depends on T001
- [ ] T003 Implement `SpeechSynthesizer.kt` (TextToSpeech wrapper, init callback checks SpanishVoiceChecker, speak()/shutdown()) in `app/src/main/kotlin/com/manuel/mvp/tts/SpeechSynthesizer.kt`, depends on T002
- [ ] T004 Compile `SpeechSynthesizer.kt` standalone against the real `android.speech.tts.TextToSpeech`/`android.content.Context` classes, confirming no API mismatch, depends on T003
