# Spec: SessionMemory implementation (T008)

## User Scenarios

### Primary user story

As Manuel (the offline voice assistant app), I want an in-memory rolling window of the last 5 conversational exchanges so that multi-turn dialogs (FR-011) stay coherent within a session, while guaranteeing no data survives past the session per the app's no-persistence privacy requirement (FR-010).

### Acceptance scenarios

1. **Given** an empty `SessionMemory`, **When** 5 exchanges are recorded one at a time, **Then** `currentExchanges()` returns all 5 in recorded order (oldest first).
2. **Given** a `SessionMemory` already holding 5 exchanges, **When** a 6th exchange is recorded, **Then** the oldest exchange is evicted and `currentExchanges()` returns the remaining 5 with the newest last.
3. **Given** a `SessionMemory` holding exchanges, **When** `clear()` is called, **Then** `currentExchanges()` immediately returns an empty list, with no elapsed time required.
4. **Given** a `SessionMemory` with one recorded exchange, **When** the clock advances by exactly 5 minutes and `currentExchanges()` (or `record()`) is called, **Then** the window has been cleared of all exchanges recorded before that advance.
5. **Given** a `SessionMemory` with one recorded exchange, **When** the clock advances by less than 5 minutes (e.g. 4:59), **Then** the exchange is still present in `currentExchanges()`.

## Functional Requirements

- **FR-001**: The system MUST expose `SessionMemory(clock: Clock, maxExchanges: Int = 5, inactivityTimeout: Duration = Duration.ofMinutes(5))` in `app/src/main/kotlin/com/manuel/mvp/session/SessionMemory.kt`, matching the constructor shape asserted by the existing test `app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt`.
- **FR-002**: The system MUST expose a `data class Exchange(val question: String, val answer: String)` in the same package, with structural equality (as relied on by the test's `assertEquals` comparisons).
- **FR-003**: `record(question: String, answer: String)` MUST append a new `Exchange` to the window, and MUST evict the oldest exchange once the window would otherwise exceed `maxExchanges`.
- **FR-004**: `currentExchanges()` MUST return the exchanges currently held, oldest first, newest last, without mutating them.
- **FR-005**: `clear()` MUST empty the window immediately, independent of elapsed time.
- **FR-006**: Both `record()` and `currentExchanges()` MUST lazily evaluate inactivity timeout on every call by comparing `clock.instant()` against the timestamp of the most recently recorded exchange: WHEN elapsed time is greater than or equal to `inactivityTimeout`, the system MUST clear the window before proceeding with the call's own effect (recording the new exchange, or returning the now-empty list). No background timer or thread is used.
- **FR-007**: The system MUST NOT persist any exchange data outside process memory (no database, file, or disk cache), satisfying FR-010's "sin persistencia entre sesiones" requirement.
- **FR-008**: The implementation MUST satisfy `SessionMemoryTest.kt` unmodified — that file is the authoritative behavioral contract from T007 and is not touched by this feature.

## Success Criteria

- **SC-001**: All 6 test cases in `app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt` pass (0 failures) once `SessionMemory.kt` is added, verified via `./gradlew test` or an equivalent JVM test runner.
- **SC-002**: The window holds at most 5 `Exchange` entries at any point after any sequence of `record()` calls, verified by the eviction test.
- **SC-003**: The timeout boundary is exact: an elapsed duration of 5 minutes 0 seconds clears the window, and 4 minutes 59 seconds does not — 0 seconds of tolerance either side, verified by the two respective boundary tests.

## Clarifications

None — the behavioral contract was already fully pinned down during T007 (test-first split); this feature only implements it. No ambiguity required a clarifying question.
