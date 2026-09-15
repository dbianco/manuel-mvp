# Plan: LocalMetricsLogger (T017)

## Technical Context

- Language: Kotlin, `app/src/main/kotlin/com/manuel/mvp/metrics/` + `app/src/test/kotlin/com/manuel/mvp/metrics/`.
- Primary dependencies: none beyond the Kotlin stdlib — no Android dependency, no `Clock` (unlike `SessionMemory`, this class aggregates durations its caller already measured; it doesn't need wall-clock awareness itself).
- Storage: none — bounded in-memory list, per spec.md's Clarifications.
- Testing tools: JUnit 4, plain JVM unit test.
- Target platform: `com.manuel.mvp`, `minSdk = 29`. Fully platform-agnostic.

## Constitution Check

- No personal data in logs or error messages: directly enforced by FR-002's type shape (no audio/text fields exist to log) — the strongest satisfaction of this rule of any component so far, since the type system itself rules out the mistake for the numeric/enum fields (the free-text `error` field still relies on caller discipline, documented in FR-002).
- HTTP/DB/public-API/secrets: not applicable.
- CI tests gate merge: this file's tests become part of the T021 CI gate.
- Accessibility: not applicable, no UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/metrics/
└── LocalMetricsLogger.kt   # new: TurnMetrics, WakeWordActivationOutcome, LocalMetricsLogger

app/src/test/kotlin/com/manuel/mvp/metrics/
└── LocalMetricsLoggerTest.kt   # new: JUnit tests covering all 6 acceptance scenarios
```

## Research

- **Why no `Clock` dependency**: unlike `SessionMemory` (which needs to detect elapsed *wall-clock* inactivity on its own), `LocalMetricsLogger` never measures time itself — its caller (`ConversationPipeline`, T018) is the one timing each pipeline stage and handing over an already-computed `TurnMetrics` record. This keeps the class simpler and needs no test double for time.
- **Bounded window default**: 100 turns as `maxTurnHistory`'s default — large enough to cover a full session's worth of turns for review, small enough that memory stays flat; a constructor parameter, not a hardcoded constant, matching `AudioCaptureManager`'s pattern of exposing tunable defaults.
- **`averageTotalDurationMs()`/`validActivationRate()` returning `Double?`**: `null` distinguishes "no data yet" from "data averaging to exactly zero" — same reasoning already used for `AudioCaptureManager.captureInstruction()` returning `null` rather than an empty array to distinguish "no speech" from "empty speech".
