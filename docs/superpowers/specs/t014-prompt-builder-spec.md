# Spec: PromptBuilder unit tests (T014)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), I want a deterministic assembler that combines the current turn's instruction, the current turn's RAG fragments, and the session's recent conversation history into a single prompt for the local LLM, so that the LLM sees exactly and exclusively those three inputs (FR-007) and always operates under the app's response rules — Spanish, 2-4 sentences, simple language, explicit "I don't know" instead of inventing an answer (FR-008) — with the current turn's information structurally favored over history when the two might conflict.

### Acceptance scenarios

1. **Given** a current instruction, some RAG fragments, and some session history, **When** `PromptBuilder().build(...)` is called, **Then** the resulting prompt contains the current instruction text verbatim.
2. **Given** one or more RAG fragments, **When** built, **Then** the resulting prompt contains every fragment's `texto` content.
3. **Given** one or more session history exchanges, **When** built, **Then** the resulting prompt contains every exchange's `question` and `answer` text.
4. **Given** an empty RAG fragment list (nothing relevant found), **When** built, **Then** the call does not fail and the resulting prompt still contains the current instruction and any history, with no fabricated fragment content.
5. **Given** an empty session history (first turn of a session), **When** built, **Then** the call does not fail and the resulting prompt still contains the current instruction and any fragments, with no fabricated history content.
6. **Given** both history and current-turn content (fragments and/or the instruction) are present, **When** built, **Then** the current turn's content appears later in the assembled prompt than the history — a structural, testable proxy for FR-007's "prioritize the current turn over history when they conflict" (recency positioning, not a runtime conflict-resolution algorithm, which is the LLM's job at inference time).
7. **Given** any call to `build(...)`, **When** the prompt is assembled, **Then** it embeds `PromptBuilder.SYSTEM_INSTRUCTIONS` (a non-blank constant carrying the FR-008 response rules) somewhere in the output.

## Functional Requirements

- **FR-001**: The system MUST define `PromptBuilderTest.kt` at `app/src/test/kotlin/com/manuel/mvp/llm/PromptBuilderTest.kt`, pinning the behavioral contract of a not-yet-written `PromptBuilder`, following the same test-first split used by T005→T006, T007→T008, T009→T010, and T012→T013.
- **FR-002**: The contract under test MUST expose `PromptBuilder().build(currentInstruction: String, ragFragments: List<ContentFragment>, sessionHistory: List<Exchange>): String`, reusing the existing `ContentFragment` (T006, `com.manuel.mvp.rag`) and `Exchange` (T008, `com.manuel.mvp.session`) types as-is — no new data types are introduced for these two inputs.
- **FR-003**: The contract MUST expose a `PromptBuilder.SYSTEM_INSTRUCTIONS: String` companion constant, asserted by the tests to be non-blank and to appear in every built prompt — the tests pin that such a rules section exists and is included, not its exact wording (which is T015's implementation choice, covering FR-008's Spanish/length/simplicity/honesty rules).
- **FR-004**: The tests MUST assert that the current instruction and every RAG fragment's/history exchange's text appears verbatim in the output (scenarios 1-3) — `PromptBuilder` must not paraphrase, truncate, or drop any of the three inputs' content.
- **FR-005**: The tests MUST assert graceful behavior (no exception, no fabricated content) when `ragFragments` is empty (scenario 4) and separately when `sessionHistory` is empty (scenario 5) — both are normal, expected states (a first turn has no history; a query with nothing relevant to retrieve has no fragments).
- **FR-006**: The tests MUST assert that current-turn content (fragments and/or the instruction) is positioned after the history content in the assembled string (scenario 6), operationalizing FR-007's priority rule as a testable ordering property.
- **FR-007**: The tests MUST NOT depend on Android framework classes, a real LLM, or any native/JNI code — this is a pure string-assembly contract over plain Kotlin types, runnable as a plain JUnit test (matching the test-first convention already used for `session`, `rag`, `audio`, and `stt`).

## Success Criteria

- **SC-001**: `PromptBuilderTest.kt` contains at least 7 test cases, one per acceptance scenario above, each independently runnable and each asserting a single expected outcome.
- **SC-002**: The test file compiles standalone against JUnit 4 (already declared in `app/build.gradle.kts`) plus the existing `ContentFragment`/`Exchange` types (already implemented, T006/T008) once a matching `PromptBuilder` exists; it is expected to fail to compile until T015 adds it, same "red by construction" state left by T005, T007, T009, and T012 for their respective implementation tasks.
- **SC-003**: 100% of the acceptance scenarios above (7 of 7) are covered by a named test method, verifiable by inspection of the file's test method names against this list.

## Clarifications

- 2026-09-14: **Priority is operationalized as position, not runtime conflict resolution.** FR-007's "prioritize the current turn's fragment over history when they conflict" describes a *generation-time* behavior of the LLM, which `PromptBuilder` cannot itself enforce (it has no way to detect or resolve a semantic conflict between two pieces of text) — the actual mechanism `PromptBuilder` controls is where each input is placed in the assembled prompt. Placing history first and current-turn content (fragments, then the instruction) last is the same "recency = higher priority" convention used broadly in LLM prompting; this task tests that ordering, not the LLM's eventual behavior (which is a T022 field-test concern).
- 2026-09-14: **`SYSTEM_INSTRUCTIONS` as a named constant, not literal prose in the test.** Pinning an exact Spanish sentence in the test would coupling the test to wording that's really an implementation/prompt-engineering choice. Instead, the test asserts that a non-blank, named constant exists and is embedded in every built prompt — enforcing "there is a real rules section, always included" without dictating its exact phrasing. T015 is expected to write that constant's actual text to satisfy FR-008 substantively (Spanish, 2-4 sentences, simple language, explicit "no sé" instead of inventing).
- 2026-09-14: This feature covers only the prompt-assembly contract. Actually running the LLM (llama.cpp via JNI, `LlamaEngine`) and combining it with `PromptBuilder`'s output is T015's responsibility, not testable here without the native SDK.
