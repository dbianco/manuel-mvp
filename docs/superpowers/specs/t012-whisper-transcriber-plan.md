# Plan: WhisperTranscriber unit tests (T012)

## Technical Context

- Language: Kotlin (JVM target via Android Gradle Plugin), matching the rest of `app/src/test/kotlin`.
- Primary dependencies: JUnit 4 only (already declared in `app/build.gradle.kts` as `testImplementation("junit:junit:4.13.2")`) — no new dependency needed. No mocking library (MockK, mentioned as a testing tool in the top-level plan.md) is needed either: a hand-written fake implementing the engine seam interface is simpler than a mock for a single-method contract.
- Storage: none — pure decision logic over an in-memory fake, no I/O.
- Testing tools: JUnit 4, run as a JVM unit test under `app/src/test/kotlin/`. No Robolectric or instrumented test needed — the contract under test has no Android framework dependency, matching `SessionMemoryTest`'s (T007) and `KeywordPrefixParserTest`'s (T009) precedent.
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`. The test itself is platform-agnostic JVM code; the real JNI-backed engine (T013) is not exercised here.
- Performance goals and constraints: not applicable — a handful of threshold-comparison unit tests with no timing sensitivity.

## Constitution Check

- No personal data in logs or error messages: not applicable — test file contains only fixed example transcript/confidence literals, no real user data, no logging.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — no network calls; whisper.cpp runs fully on-device.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — this defines brand-new, not-yet-implemented internal types (`WhisperTranscriber` and its engine seam); nothing existing changes.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved.
- Tests run in CI before merge and a red build blocks the merge: this file becomes part of the T021 CI gate once added; until T013 implements `WhisperTranscriber` and the engine seam, the file intentionally fails to compile (documented, matching the T005/T007/T009 precedent), so it must not be wired into a build expected to be green before T013 lands.
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/test/kotlin/com/manuel/mvp/stt/
└── WhisperTranscriberTest.kt   # new: pins the WhisperTranscriber + engine-seam contract
```

New file: `app/src/test/kotlin/com/manuel/mvp/stt/WhisperTranscriberTest.kt` (new package `stt` under `test/kotlin`, mirroring the `main/kotlin/com/manuel/mvp/stt/` package T013 will populate, per the top-level `plan.md`'s file tree).

No other files are created or modified.

## Research

- **Engine seam shape**: a minimal functional-style interface — one method, raw PCM16 `ShortArray` in, a result carrying transcribed text plus a `Float` confidence out — is enough to decouple the confidence-threshold decision from the real JNI call. Named generically in spec.md as "a whisper engine contract" since the exact interface/class name is T013's call (this test only needs *some* injectable seam to exist so a fake can be substituted); the test file will pick a concrete name and T013 must match it exactly, same as T009 pinned `KeywordPrefixParser.parse`'s exact signature for T010.
- **Fake vs. mocking library**: a small hand-written fake class implementing the seam and returning a canned `Result` is simpler and more readable than pulling in MockK for a single-method interface — consistent with this codebase's existing preference for hand-written test doubles (`SessionMemoryTest`'s `MutableClock`) over a mocking framework, even though MockK is listed as an available testing tool in the top-level `plan.md`.
- **Threshold inclusivity (`>=`)**: chosen to match `SessionMemory`'s already-established `>=` convention for its inactivity timeout (T007/T008), for consistency across the codebase's threshold-style contracts — not independently dictated by the top-level spec's wording, which is silent on the exact boundary.
- **Pass-through assertion (scenario 6)**: verified by having the fake engine capture the `ShortArray` reference (or a copy) it received and asserting it against the array the test passed to `transcribe(...)`, confirming `WhisperTranscriber` doesn't mutate, truncate, or re-encode the audio before handing it to the engine.
- **Alternative considered and rejected**: testing the real JNI bridge directly with a tiny/mock whisper model bundled in test resources. Rejected — no Android SDK/NDK toolchain is available in this sandbox to build or run native code as part of a JVM test, and even with one, a real whisper.cpp inference run would make the test slow and non-deterministic (real audio-dependent confidence scores) rather than a fast, deterministic unit test of the threshold decision itself.
