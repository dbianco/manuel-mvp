# Plan: KeywordPrefixParser unit tests (T009)

## Technical Context

- Language: Kotlin (JVM target via Android Gradle Plugin), matching the rest of `app/src/test/kotlin`.
- Primary dependencies: JUnit 4 only (already declared in `app/build.gradle.kts` as `testImplementation("junit:junit:4.13.2")`) — no new dependency needed.
- Storage: none — pure string parsing, no I/O.
- Testing tools: JUnit 4, run as a JVM unit test under `app/src/test/kotlin/`. No Robolectric or instrumented test needed — `KeywordPrefixParser` has no Android framework dependency, matching `SessionMemoryTest`'s precedent (T007).
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`. The test itself is platform-agnostic JVM code.
- Performance goals and constraints: not applicable — a handful of string-comparison unit tests with no timing sensitivity.

## Constitution Check

- No personal data in logs or error messages: not applicable — test file contains only fixed example transcripts, no real user data, no logging.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — no network calls.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — this defines a brand-new, not-yet-implemented internal type (`KeywordPrefixParser`); nothing existing changes.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved.
- Tests run in CI before merge and a red build blocks the merge: this file becomes part of the T021 CI gate once added; until T010 implements `KeywordPrefixParser`, the file intentionally fails to compile (documented, matching the T007→T008 precedent), so it must not be wired into a build that's expected to be green before T010 lands.
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/test/kotlin/com/manuel/mvp/audio/
└── KeywordPrefixParserTest.kt   # new: pins the KeywordPrefixParser.parse(String): String? contract
```

New file: `app/src/test/kotlin/com/manuel/mvp/audio/KeywordPrefixParserTest.kt` (new package `audio` under `test/kotlin`, mirroring the existing `main/kotlin/com/manuel/mvp/audio/` package that T010 will populate).

No other files are created or modified.

## Research

- **Contract shape (`parse(transcript: String): String?`)**: chosen over an exception-throwing or sealed-result-type API because the caller (T010's `WakeWordListener`) only needs a binary outcome — instruction text, or nothing — and FR-004 explicitly requires *silent* discard (no error surfaced), so a nullable return is the simplest faithful shape. An `object KeywordPrefixParser` (stateless) is assumed for the same reason `FragmentSearcher`/`SessionMemory` are the precedent-setting types in this codebase for small, focused, single-purpose classes — but the exact declaration (`object` vs class with no state) is left to T010, since the test only calls the function, not the declaration form.
- **Case-insensitivity approach**: tests assert behavior, not implementation, so no `Regex`/`startsWith(ignoreCase = true)` choice is pinned by the test — T010 is free to implement either.
- **Keyword anchoring at start vs. "contains"**: this is the one FR-003-driven decision the tests must pin down structurally (acceptance scenario 6: "che Manuel qué hora es" → `null`), since it directly guards against false activations on ambient speech that merely mentions the assistant's name mid-sentence.
- **Alternative considered and rejected**: making the parser also responsible for the acoustic wake-word gating (i.e. merging it with `WakeWordListener`). Rejected because `plan.md` already lists "parseo de prefijo de palabra clave" as separate deterministic/JUnit-testable logic, distinct from the (non-deterministic, hardware-dependent) openWakeWord engine — keeping them separate is what makes T009 testable at all without mocking audio hardware.
