# Spec: SpeechSynthesizer (T016)

## User Scenarios

### Primary user story

As Manuel, I want to speak the LLM's response aloud via Android's offline TTS engine in Spanish, and know at first startup whether a Spanish voice is actually available, so that the app can degrade gracefully (and be diagnosable) if the device has no Spanish voice installed (FR-009).

### Acceptance scenarios

1. **Given** a set of locales the TTS engine reports as available, **When** checked for Spanish support, **Then** the check returns `true` if any locale's language code is `"es"` (any country/variant), `false` otherwise.
2. **Given** an empty set of available locales, **When** checked, **Then** the check returns `false` without throwing.
3. **Given** `SpeechSynthesizer` has successfully initialized with a Spanish voice available, **When** `speak(text)` is called, **Then** it forwards `text` to the underlying `TextToSpeech.speak(...)` call.
4. **Given** `SpeechSynthesizer.shutdown()` is called, **When** it runs, **Then** it stops any in-progress speech and releases the underlying `TextToSpeech` engine.

## Functional Requirements

- **FR-001**: The system MUST add a pure, Android-independent `SpanishVoiceChecker` (or equivalent) at `app/src/main/kotlin/com/manuel/mvp/tts/`, checking a `Set<java.util.Locale>` for Spanish-language support (`Locale.getLanguage() == "es"`, case-insensitive) — `java.util.Locale` is a JDK class, not Android-only, so this check is unit-testable on the plain JVM.
- **FR-002**: The system MUST add `SpeechSynthesizer.kt`, wrapping `android.speech.tts.TextToSpeech`: constructs the engine with a Spanish `Locale`, and on the engine's init callback, checks `getAvailableLanguages()` against `SpanishVoiceChecker` to determine and expose whether a Spanish voice is available (FR-009's "verificando en el primer inicio").
- **FR-003**: `SpeechSynthesizer` MUST expose `speak(text: String)` (delegates to `TextToSpeech.speak`) and `shutdown()` (stops and releases the engine).
- **FR-004**: The system MUST add a JUnit test file covering `SpanishVoiceChecker`'s behavior (acceptance scenarios 1-2), runnable as a plain JVM test with no Android dependency.

## Success Criteria

- **SC-001**: `SpanishVoiceChecker`'s tests pass for real (standalone `kotlinc` + `JUnitCore`, per the project's no-Android-SDK constraint) — at least 4 cases: Spanish-only locale present, Spanish among other locales, no Spanish present, empty set.
- **SC-002**: `SpeechSynthesizer.kt` compiles cleanly against the real `android.speech.tts.TextToSpeech`/`android.content.Context` classes (Robolectric's `android-all` jar, no Android SDK in this sandbox), verified via standalone compile, same precedent as `WakeWordListener`/`NativeWhisperEngine`.

## Clarifications

- 2026-09-14: `SpeechSynthesizer` has no automated test of its own (only its extracted `SpanishVoiceChecker` seam does) — matches the established pattern (`WakeWordListener`, `AudioCaptureManager`, `NativeWhisperEngine`) of extracting the one genuinely pure decision (here: "is Spanish available") out of an Android-framework wrapper that needs a real TTS engine to exercise for real.
- 2026-09-14: FR-009 doesn't specify what the app should *do* when no Spanish voice is available (e.g. show an error, fall back to a default voice) — that's a UI/UX decision for T020 (`MainScreen`/`AssistantState`), which will read `SpeechSynthesizer`'s exposed availability flag. This task only makes that flag available; it doesn't dictate the UI response to it.
