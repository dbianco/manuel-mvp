# Spec: KeywordPrefixParser unit tests (T009)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), I want a deterministic parser that extracts the instruction from a transcript prefixed with the wake word ("Manuel, <instrucción>") and silently discards anything that isn't a well-formed prefixed instruction, so that the assistant never responds to, transcribes as a turn, or otherwise processes ambient audio that wasn't clearly directed at it (FR-003), and never produces an audible response when the wake word fired but no clear instruction followed (FR-004).

### Acceptance scenarios

1. **Given** a transcript "Manuel, ¿cuánto es tres por cuatro?", **When** it is parsed, **Then** the parser extracts the instruction "¿cuánto es tres por cuatro?" (keyword prefix and its separator removed, remaining text trimmed).
2. **Given** a transcript "manuel, contame un cuento", **When** it is parsed (keyword in lowercase), **Then** the parser still extracts "contame un cuento" — matching is case-insensitive.
3. **Given** a transcript "MANUEL dime la tabla del 5" (no comma after the keyword), **When** it is parsed, **Then** the parser still extracts "dime la tabla del 5" — the comma is an optional separator, not a required token.
4. **Given** a transcript "qué lindo día para jugar" (no keyword anywhere), **When** it is parsed, **Then** the parser returns no instruction (silent discard) — satisfies FR-003's "ignore audio that doesn't include the keyword".
5. **Given** a transcript "Manuel" alone, or "Manuel," alone, or "Manuel   " (only trailing whitespace after the keyword), **When** it is parsed, **Then** the parser returns no instruction — satisfies FR-004's "keyword detected but no clear instruction followed".
6. **Given** a transcript "che Manuel qué hora es" (keyword present but not at the start), **When** it is parsed, **Then** the parser returns no instruction — the keyword MUST anchor the start of the transcript, per FR-003's "toda instrucción DEBE comenzar con la palabra clave".
7. **Given** a transcript with leading whitespace before the keyword, e.g. "  Manuel, hola" (STT transcripts may carry incidental leading whitespace), **When** it is parsed, **Then** the parser still extracts "hola" — leading whitespace around the transcript is not itself a "different start".

## Functional Requirements

- **FR-001**: The system MUST define a `KeywordPrefixParserTest.kt` unit test file at `app/src/test/kotlin/com/manuel/mvp/audio/KeywordPrefixParserTest.kt` that pins the behavioral contract of a not-yet-written `KeywordPrefixParser`, following the same test-first split used by T005→T006 and T007→T008.
- **FR-002**: The contract under test MUST expose a pure function shaped `parse(transcript: String): String?` (exact type name left to T010, referenced generically here as `KeywordPrefixParser`) taking a full STT transcript and returning the extracted instruction, or `null` when no valid prefixed instruction is present.
- **FR-003**: The tests MUST assert that a transcript beginning with the configured keyword ("Manuel"), followed by an optional comma and/or whitespace, then non-blank text, extracts that trailing text (trimmed) as the instruction.
- **FR-004**: The tests MUST assert case-insensitive matching of the keyword ("Manuel", "manuel", "MANUEL" all match).
- **FR-005**: The tests MUST assert that a transcript whose keyword is not at the very start (only incidental leading whitespace tolerated) returns `null` — the keyword anchors the start of the utterance, it is not merely "present somewhere".
- **FR-006**: The tests MUST assert that a transcript with the keyword but no non-blank text after it (nothing, only a comma, or only whitespace) returns `null` — this is the FR-004 "keyword detected, no clear instruction" case, which must produce silent discard, not an empty-string instruction.
- **FR-007**: The tests MUST assert that a transcript without the keyword anywhere at the start returns `null` — the FR-003 "ignore audio without the keyword" case.
- **FR-008**: The tests MUST NOT depend on Android framework classes, openWakeWord, or any I/O — this is a pure string-parsing contract, runnable as a plain JVM unit test (matching the existing `test/kotlin/com/manuel/mvp/audio/` — a new package — test-first convention already used for `session` and `rag`).

## Success Criteria

- **SC-001**: `KeywordPrefixParserTest.kt` contains at least 7 test cases, one per acceptance scenario above, each independently runnable and each asserting a single expected outcome.
- **SC-002**: The test file compiles standalone against JUnit 4 (already declared in `app/build.gradle.kts`) once a matching `KeywordPrefixParser` exists; it is expected to fail to compile until T010 adds that class (same "red by construction" state T007 left for T008).
- **SC-003**: 100% of the acceptance scenarios above (7 of 7) are covered by a named test method, verifiable by inspection of the file's test method names against this list.

## Clarifications

- 2026-09-14: The keyword-prefix text pattern (FR-003/FR-004) is a separate, purely textual mechanism from the acoustic wake-word detector (FR-002, `WakeWordListener`/openWakeWord). Per `plan.md`'s explicit split ("parseo de prefijo de palabra clave" listed alongside — but distinct from — the wake-word engine as deterministic, JUnit-testable logic), `KeywordPrefixParser` operates on the STT transcript of the captured audio, not on the acoustic detection event itself. This lets FR-003's "ignore audio that doesn't include the keyword" and FR-004's "no clear instruction after the keyword" be enforced/tested independently of the (non-deterministic, hardware-dependent) acoustic engine.
- 2026-09-14: The comma after the keyword is treated as an optional stylistic separator (per acceptance scenario 3, "MANUEL dime la tabla del 5" with no comma), not a required delimiter — Whisper transcripts are not guaranteed to punctuate speech consistently.
