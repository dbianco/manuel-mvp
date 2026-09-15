# Plan: SessionMemory implementation (T008)

## Technical Context

- Language: Kotlin (JVM target via Android Gradle Plugin), matching the rest of `app/src/main/kotlin`.
- Primary dependencies: `java.time` (`Clock`, `Instant`, `Duration`) — already used by the T007 test; no new library dependency needed.
- Storage: none — this feature is explicitly in-memory-only (an `ArrayDeque<Exchange>` or `MutableList<Exchange>` held on the instance, plus a nullable `Instant` last-activity timestamp). No SQLite, no file, no Room.
- Testing tools: JUnit 4 (already declared in `app/build.gradle.kts` as `testImplementation("junit:junit:4.13.2")`), run as a JVM unit test under `app/src/test/kotlin/`. No Robolectric or instrumented test needed since `SessionMemory` has no Android framework dependency.
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`.
- Performance goals and constraints: negligible — bounded to 5 in-memory entries, no I/O, no threading; timeout check is an O(1) comparison done lazily on `record()`/`currentExchanges()` calls, no background timer/thread (per FR-006 and the class doc already written into T007's test file).

## Constitution Check

- No personal data in logs or error messages: `SessionMemory` performs no logging; question/answer text is only ever held in memory and returned to the caller, never logged. Satisfied.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — this feature makes no network calls.
- Database schema changes ship as reversible migrations: not applicable — this feature adds no schema, by design (FR-007: no persistence).
- Public API changes are additive within a major version: not applicable — `SessionMemory`/`Exchange` are new internal types, not a published API; T007's test already pins their shape, so this plan does not change it.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved.
- Tests run in CI before merge and a red build blocks the merge: `SessionMemoryTest.kt` (T007) is the acceptance gate; this feature is done only when it passes, and T021 (CI) will run it on every PR going forward.
- Accessibility: not applicable — `SessionMemory` has no UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/session/
└── SessionMemory.kt   # new: `SessionMemory` class + `Exchange` data class
```

New file: `app/src/main/kotlin/com/manuel/mvp/session/SessionMemory.kt`

No other files are created or modified. In particular, `app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt` (T007) is read-only input to this feature and is not touched.

## Research

- **In-memory collection choice**: a `MutableList<Exchange>` (backed by `ArrayList`) is used over `ArrayDeque` — both give O(1) amortized append and O(1) `removeAt(0)`-equivalent for a fixed cap of 5, but `MutableList` keeps `currentExchanges()`'s "return in recorded order" contract trivial via `toList()`, and the eviction step (`if (size > maxExchanges) removeAt(0)`) reads directly against the acceptance scenarios. `ArrayDeque` was considered and rejected only for being marginally less readable at this scale (5 elements); no measurable performance difference at this size.
- **Timeout evaluation strategy**: lazy evaluation on each `record()`/`currentExchanges()` call (comparing `clock.instant()` to a stored `lastActivityAt: Instant?`) rather than a scheduled/background clear. This matches the test file's own documented contract ("no background timer/thread") and keeps the class free of any lifecycle/threading concerns — it only ever runs on the calling thread, when called.
- **Boundary semantics (`>=` vs `>`)**: confirmed against the T007 test's `MutableClock`-driven boundary tests (5:00 exactly clears, 4:59 does not) — implemented as `Duration.between(lastActivityAt, clock.instant()) >= inactivityTimeout`.
- **Alternative considered and rejected**: persisting the window to `ContentDatabase`'s SQLite instance (reusing T006's infra) — rejected because FR-010 explicitly requires no persistence between sessions, and reusing the DB would add a code path (delete-on-clear) that has no requirement to exist.
