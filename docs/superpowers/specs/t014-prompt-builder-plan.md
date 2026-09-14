# Plan: PromptBuilder unit tests (T014)

## Technical Context

- Language: Kotlin (JVM target via Android Gradle Plugin), `app/src/test/kotlin/com/manuel/mvp/llm/`.
- Primary dependencies: JUnit 4 only (already declared). Reuses `com.manuel.mvp.rag.ContentFragment` (T006) and `com.manuel.mvp.session.Exchange` (T008) — both already implemented, plain data classes, no new dependency.
- Storage: none — pure string assembly.
- Testing tools: JUnit 4, plain JVM unit test, no Robolectric/instrumented test needed, matching `SessionMemoryTest`/`KeywordPrefixParserTest`/`WhisperTranscriberTest` precedent.
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`. The test itself is platform-agnostic JVM code; the real LLM (`LlamaEngine`, T015) is not exercised here.
- Performance goals and constraints: not applicable — pure string concatenation with no timing sensitivity.

## Constitution Check

- No personal data in logs or error messages: not applicable — test file contains only fixed example strings, no real user data, no logging.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — no network calls; the LLM runs on-device (T015).
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — `PromptBuilder` is a brand-new, not-yet-implemented internal type; `ContentFragment`/`Exchange` are reused unchanged.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved.
- Tests run in CI before merge and a red build blocks the merge: this file becomes part of the T021 CI gate once added; until T015 implements `PromptBuilder`, the file intentionally fails to compile (documented, matching the T005/T007/T009/T012 precedent).
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/test/kotlin/com/manuel/mvp/llm/
└── PromptBuilderTest.kt   # new: pins the PromptBuilder.build(...) + SYSTEM_INSTRUCTIONS contract
```

New file: `app/src/test/kotlin/com/manuel/mvp/llm/PromptBuilderTest.kt` (new package `llm` under `test/kotlin`, mirroring the `main/kotlin/com/manuel/mvp/llm/` package T015 will populate, per the top-level `plan.md`'s file tree).

No other files are created or modified.

## Research

- **Reusing `ContentFragment`/`Exchange` directly**: both types are already stable, tested contracts (T006/T008) with the exact fields `PromptBuilder` needs (`ContentFragment.texto`, `Exchange.question`/`Exchange.answer`) — introducing parallel/wrapper types for this feature would add indirection with no benefit, so the test constructs these existing data classes directly.
- **Ordering assertion mechanics (scenario 6)**: implemented via `String.indexOf(...)` comparisons — the index of a distinctive history-only substring (e.g. an exchange's answer text) must be less than the index of a distinctive current-turn substring (a fragment's `texto`, or the current instruction itself) somewhere in the built prompt. This is a black-box assertion on the returned `String`, not on `PromptBuilder`'s internal construction steps, consistent with `quality.tdd`'s "test behaviour at the boundary the caller sees" guidance already retrieved in this feature's context pack.
- **Why a named `SYSTEM_INSTRUCTIONS` constant instead of literal text in the test**: matches the approach already used for `KeywordPrefixParser`/`WhisperTranscriber`-style contracts, where the test pins structure and presence, not implementation-chosen prose — see spec.md's Clarifications. This also means `PromptBuilderTest.kt` can assert `PromptBuilder.SYSTEM_INSTRUCTIONS.isNotBlank()` and `prompt.contains(PromptBuilder.SYSTEM_INSTRUCTIONS)` without knowing or caring what the actual Spanish rules text says.
- **Alternative considered and rejected**: asserting on an exact, fully-formatted expected prompt string (golden-file style). Rejected as too brittle — it would tightly couple the test to T015's exact section headers/whitespace/formatting choices, making any future formatting tweak a T014-contract-breaking change for no behavioral reason. The per-scenario "contains" and "ordering" assertions capture the actual functional requirements (FR-004/FR-005/FR-006) without over-constraining implementation.
