# Spec: LocalMetricsLogger (T017)

## User Scenarios

### Primary user story

As Manuel, I want to record per-turn stage timings and errors, plus the rate of wake-word activations that led to a real turn versus those that didn't (a possible-false-positive proxy), entirely locally and without ever storing a child's question text or audio (FR-013), so the app's performance and the wake-word's real-world reliability can be reviewed after a session without any privacy risk.

### Acceptance scenarios

1. **Given** a `TurnMetrics` record (capture/transcription/search/generation/total durations, an optional error), **When** `recordTurn(...)` is called, **Then** it's retained and reflected in the logger's counts/aggregates.
2. **Given** several recorded turns, **When** aggregates are queried, **Then** `turnCount()` matches the number recorded and `errorCount()` counts only the turns with a non-null error.
3. **Given** no turns have been recorded, **When** `averageTotalDurationMs()` is queried, **Then** it returns `null` (no data), not zero (which would misleadingly imply "instant turns").
4. **Given** more turns are recorded than the logger's configured maximum, **When** the cap is exceeded, **Then** the oldest turn is evicted (a bounded, in-memory rolling window, mirroring `SessionMemory`'s size-bounding), keeping memory use flat over a long session.
5. **Given** a wake-word activation outcome (`VALID_TURN` or `POSSIBLE_FALSE_POSITIVE`) is recorded via `recordWakeWordActivation(...)`, **When** `validActivationRate()` is queried, **Then** it returns the fraction of recorded activations classified `VALID_TURN`.
6. **Given** no wake-word activations have been recorded, **When** `validActivationRate()` is queried, **Then** it returns `null` (no data).

## Functional Requirements

- **FR-001**: The system MUST add `LocalMetricsLogger.kt` at `app/src/main/kotlin/com/manuel/mvp/metrics/`, exposing `TurnMetrics(captureDurationMs, transcriptionDurationMs, searchDurationMs, generationDurationMs, totalDurationMs, error: String? = null)` and a `LocalMetricsLogger` class with `recordTurn(metrics: TurnMetrics)`, `recordWakeWordActivation(outcome: WakeWordActivationOutcome)`, and query methods (`turnCount()`, `errorCount()`, `averageTotalDurationMs()`, `validActivationRate()`).
- **FR-002**: `TurnMetrics` and `WakeWordActivationOutcome` MUST NOT carry any field for audio data or question/answer text — only numeric durations, an optional error message, and an activation-outcome enum (FR-013's "sin almacenar audio ni texto de las preguntas"). Callers are responsible for not embedding question/answer text inside the `error` message string; this class does not (and cannot) validate that.
- **FR-003**: `LocalMetricsLogger` MUST bound its retained turn history to a configurable maximum (default a reasonable fixed size), evicting the oldest turn once the cap is exceeded — an in-memory, per-session rolling log, not persisted to disk (an MVP simplification; FR-013 only requires the data to stay local/on-device, not that it survive an app restart).
- **FR-004**: `WakeWordActivationOutcome` MUST distinguish `VALID_TURN` (the activation led to a usable instruction and a generated response) from `POSSIBLE_FALSE_POSITIVE` (the activation fired but nothing usable followed — no speech captured, low transcription confidence, or the keyword-prefix check rejected the transcript) — this is the app's own observable proxy for FR-013's "posibles falsos positivos" (the word "posibles"/"possible" in the spec already acknowledges the app can't know for certain; a genuine false *negative* — a missed activation — is undetectable by the app itself, since no event fires at all, and remains a T022 field-test concern, not something this class can report).

## Success Criteria

- **SC-001**: A new unit test file covers all 6 acceptance scenarios above, runnable as a plain JVM JUnit test with no Android dependency (the class needs none — it only aggregates values its caller already computed).
- **SC-002**: All tests pass for real, verified via standalone `kotlinc` + `JUnitCore` (per the project's no-Android-SDK constraint).

## Clarifications

- 2026-09-14: **In-memory only, no SQLite/file persistence.** The top-level `plan.md` lists "tabla SQLite separada o archivo de log local acotado en tamaño" as options; this task picks the simpler in-memory option (bounded rolling window, like `SessionMemory`) for the MVP, since FR-013 only requires metrics to stay local and not include audio/question text — it doesn't require surviving an app restart. Revisit if a later task needs metrics to persist across sessions (e.g. for the T022 field-test protocol to review after multiple sessions).
- 2026-09-14: **False negatives are out of scope for this class.** FR-013 asks for a rate of "activaciones válidas vs. posibles falsos positivos/negativos" — but a missed wake-word activation (a true negative case gone wrong) produces no event at all, so the app has no way to observe or count it. `LocalMetricsLogger` only tracks what it can actually observe: activations that did fire, split into valid vs. possible-false-positive. Real false-negative measurement (SC-011's "9 de 10 intentos deliberados") is a human-supervised field-test metric (T022), not an app-internal one.
