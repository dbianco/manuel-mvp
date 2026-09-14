# Plan: WakeWordListener + KeywordPrefixParser implementation (T010)

## Technical Context

- Language: Kotlin (JVM target via Android Gradle Plugin), `app/src/main/kotlin/com/manuel/mvp/audio/`.
- Primary dependencies: `xyz.rementia:openwakeword:0.1.5` (already declared in `app/build.gradle.kts` by T003) — specifically `com.rementia.openwakeword.lib.WakeWordEngine`, `.model.WakeWordModel`, `.model.WakeWordDetection`, `.model.DetectionMode`. Transitively pulls in `kotlinx-coroutines-core`/`-android` and `com.microsoft.onnxruntime:onnxruntime-android` (confirmed present in the Gradle cache from T003's sync). No new Gradle dependency needed for this feature.
- Storage: none.
- Testing tools: `KeywordPrefixParser` is verified against T009's existing JUnit4 test. `WakeWordListener` has no dedicated unit test (it wraps a stateful Android/ONNX engine that needs instrumentation to exercise for real) — verified instead by a standalone compile against the real dependency classes, matching this sandbox's precedent for Android-framework-backed code (T006's `ContentDao`/`ContentDatabase` were verified the same way, only `FragmentSearcher`'s pure logic got a JVM test).
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`.
- Performance goals and constraints: not specified further than SC-010/SC-011 in the top-level spec (false-positive rate, activation rate) — those are field-test criteria (T022), out of scope for this unit of work.

## Constitution Check

- No personal data in logs or error messages: `WakeWordListener` and `KeywordPrefixParser` do no logging of their own; transcripts/instructions are only ever passed through to the caller, never logged. Satisfied.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — openWakeWord runs entirely on-device (ONNX Runtime), no network calls.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — both are new internal types; `KeywordPrefixParser`'s shape is already pinned by T009's test and is not changed here.
- Secrets come from the environment or the secret manager: not applicable — openWakeWord (unlike the earlier, discarded Porcupine choice) requires no account or API key.
- Tests run in CI before merge and a red build blocks the merge: `KeywordPrefixParserTest.kt` becomes green with this feature's `KeywordPrefixParser.kt`, satisfying the T021 CI gate for that file once CI is configured. `WakeWordListener.kt` has no automated test to gate on (see Testing tools above); its correctness is field-validated per SC-010/SC-011 in T022's manual protocol.
- Accessibility: not applicable — neither type has a UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/audio/
├── KeywordPrefixParser.kt   # new: implements the T009-pinned parse(String): String? contract
└── WakeWordListener.kt      # new: wraps WakeWordEngine (arm/disarm, detections Flow, resolveInstruction)
```

New files: `KeywordPrefixParser.kt`, `WakeWordListener.kt`. No existing files are modified except this plan/spec/tasks documentation and `docs/superpowers/specs/tasks.md` (marking T010 done).

## Research

- **`WakeWordEngine` vs `ParallelWakeWordEngine`**: the AAR (`xyz.rementia:openwakeword:0.1.5`, inspected via `javap` against the cached `.aar`'s `classes.jar` since no published API docs were available offline) exposes both. `ParallelWakeWordEngine` runs multiple models concurrently; the MVP has exactly one keyword ("Manuel"), so plain `WakeWordEngine` is the simpler, sufficient choice.
- **Constructor shape**: `WakeWordEngine(context: Context, models: List<WakeWordModel>, detectionMode: DetectionMode, detectionCooldownMs: Long, scope: CoroutineScope)`; `WakeWordModel(name: String, modelPath: String, threshold: Float)`. `WakeWordListener` builds a single-element model list from a configurable asset path (defaulting to `wakeword/manuel.onnx`, T003's convention) and threshold, and takes the `Context`/`CoroutineScope` from its caller rather than constructing its own — keeping lifecycle ownership (and thus cancellation/cleanup) with the caller, consistent with how the rest of this codebase avoids hidden global state (e.g. `SessionMemory` takes its `Clock` from the caller).
- **Detection stream surface**: `engine.getDetections(): Flow<WakeWordDetection>` is re-exposed as-is (`armedDetections`) rather than wrapped/transformed, since T010 has no requirement yet for what a caller does with a detection (that's T011's audio-capture trigger and T018's pipeline orchestration) — transforming it now would be speculative.
- **`resolveInstruction` as a thin pass-through**: rather than have `WakeWordListener` re-implement or call into keyword matching itself, it forwards directly to `KeywordPrefixParser.parse`. This keeps the acoustic (`WakeWordEngine`) and textual (`KeywordPrefixParser`) mechanisms genuinely independent and testable in isolation, per the T009 clarification already on record.
- **Verifying `WakeWordListener.kt` without an Android SDK**: this sandbox has no `ANDROID_HOME` (see [[manuel-mvp.constraint — no Android SDK]]). Unlike `KeywordPrefixParser` (plain JVM, runs real tests), `WakeWordListener` needs `android.content.Context` and the openWakeWord/ONNX Runtime classes to even compile. Robolectric's cached `android-all-15-robolectric-*.jar` (already in the Gradle cache from earlier tasks' Robolectric evaluation, see T005's report) provides a real, JVM-loadable `android.content.Context` class, and the openWakeWord/onnxruntime/`kotlinx-coroutines-core` jars are already cached locally from T003's Gradle sync. Assembling these on a `kotlinc` classpath lets `WakeWordListener.kt` be compiled for real (not just read for syntax) even without the Android SDK — this doesn't exercise the engine at runtime (that needs a real device/emulator with the trained `manuel.onnx` model, per T003's note that the model doesn't exist yet), but it does catch API-mismatch errors against the real library classes.
