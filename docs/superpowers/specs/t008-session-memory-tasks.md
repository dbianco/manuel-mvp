# Tasks: SessionMemory implementation (T008)

## Tasks

- [ ] T001 Implement `Exchange` (data class: `question: String`, `answer: String`) and `SessionMemory` (constructor `clock: Clock, maxExchanges: Int = 5, inactivityTimeout: Duration = Duration.ofMinutes(5)`; methods `record(question, answer)`, `currentExchanges(): List<Exchange>`, `clear()`; lazy `>=` timeout check on every `record`/`currentExchanges` call) in `app/src/main/kotlin/com/manuel/mvp/session/SessionMemory.kt`
- [ ] T002 Run `app/src/test/kotlin/com/manuel/mvp/session/SessionMemoryTest.kt` (unmodified) and confirm all 6 test cases pass, depends on T001
