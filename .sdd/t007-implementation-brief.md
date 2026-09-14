# T007 implementation brief — SessionMemory unit tests

Package: `com.manuel.mvp.session`. This task writes ONLY the test file
(`app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt`) — `SessionMemory.kt`/
`Exchange.kt` (production) do NOT exist yet and must NOT be created by you; they are a separate,
later task (T008). Your test file is expected to not compile in isolation right now — that's
correct, not a defect, same pattern as T005's relationship to T006.

## Exact API contract to write tests against (ruled on in advance — use verbatim)

```kotlin
package com.manuel.mvp.session

data class Exchange(val question: String, val answer: String)

class SessionMemory(
    private val clock: Clock = Clock.systemUTC(),
    private val maxExchanges: Int = 5,
    private val inactivityTimeout: Duration = Duration.ofMinutes(5),
) {
    fun record(question: String, answer: String)
    fun currentExchanges(): List<Exchange>
    fun clear()
}
```

(`Clock`/`Duration` are `java.time.Clock`/`java.time.Duration`.)

## Behavior to test (this IS the spec — no separate spec.md to cross-reference beyond this)

- `record(question, answer)`: appends a new `Exchange` to the window. If the window already has
  `maxExchanges` (5) entries, the oldest is dropped so the window never exceeds 5. Before
  appending, if `inactivityTimeout` (5 minutes) or more has elapsed since the last recorded
  exchange, the window is cleared first (session-timeout behavior), then the new exchange becomes
  the sole entry.
- `currentExchanges()`: returns the current window, chronological order (oldest first, most
  recently recorded last). If `inactivityTimeout` or more has elapsed since the last recorded
  exchange, the window is cleared as a side effect of this call too, and an empty list is
  returned.
- `clear()`: empties the window immediately, regardless of elapsed time.
- The timeout is evaluated lazily (checked inside `record`/`currentExchanges`, not via a
  background timer/thread) by comparing `clock.instant()` against the timestamp of the last
  recorded exchange.

## Required test scenarios (5, matching the feature's acceptance scenarios)

1. **Window fills up to 5**: record 5 exchanges one at a time; `currentExchanges()` returns all 5,
   in the order they were recorded (oldest first).
2. **Sliding window evicts the oldest**: record 6 exchanges; `currentExchanges()` returns exactly
   5, the oldest one is gone, and the most recently recorded one is last in the list.
3. **`clear()` empties immediately**: record some exchanges, call `clear()`, `currentExchanges()`
   returns an empty list — with no time having passed at all (proves `clear()` doesn't depend on
   the clock).
4. **Timeout clears the window**: using an injectable/fixed `Clock` you control, record an
   exchange, advance the simulated clock by 5 minutes or more, then either call
   `currentExchanges()` (should return empty) or `record()` again (should result in the window
   containing only the new exchange, not the old ones) — cover at least one of these two access
   paths explicitly; both if practical.
5. **No premature clearing**: record an exchange, advance the simulated clock by *less than* 5
   minutes (e.g. 4 minutes 59 seconds), call `currentExchanges()`, and confirm the exchange is
   still there.

## How to control time in the test

Do not use `Thread.sleep`. Implement a small, mutable `Clock` for the test — e.g. a private class
extending `java.time.Clock` that holds a mutable `Instant` field, overrides `instant()` to return
it and `withZone`/`getZone()` sensibly (fixed at `ZoneOffset.UTC` is fine), and exposes a way to
advance it (e.g. `fun advanceBy(duration: Duration)` that adds to the held instant). Construct
`SessionMemory` with an instance of this test clock so each test can move time forward
deterministically and instantly.

## Scope for THIS task only

- Only `app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt`. No production code, no
  changes to `app/build.gradle.kts` (JUnit4 is already a test dependency from T005 — verify this
  yourself by checking the current `app/build.gradle.kts` rather than assuming, and only add a
  dependency if something is genuinely missing).
- Do not touch anything under `com.manuel.mvp.rag` (T005/T006, already done) or any other package.

## Definition of done / verification

- The test file is syntactically valid Kotlin, using only real JUnit4/`java.time` APIs (verify
  signatures rather than guessing if unsure).
- You cannot get a real compile (the target class doesn't exist yet) — say so plainly. If useful,
  write a small throwaway program (outside the repo, e.g. plain Kotlin/Java) implementing a
  minimal version of the contract and running it against your test's actual logic/assertions, to
  sanity-check your scenario reasoning (especially the timeout math) is correct — this is
  optional but has been valuable in prior tasks (see T005's reports for the style of verification
  this project values).
- Report DONE_WITH_CONCERNS if anything can't be verified with confidence.

## Report

Write your full report to `.sdd/t007-implementation-report.md` in this worktree: what you wrote,
how you verified it, files changed, self-review, concerns. Then reply with a short status
(DONE / DONE_WITH_CONCERNS / BLOCKED / NEEDS_CONTEXT), commit SHA, one-line verification summary,
concerns, and the report file path.
