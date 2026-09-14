# Tasks: PromptBuilder unit tests (T014)

## Tasks

- [ ] T001 Write `PromptBuilderTest.kt` covering the 7 acceptance scenarios (current instruction present, fragments present, history present, empty-fragments graceful, empty-history graceful, current-turn-after-history ordering, SYSTEM_INSTRUCTIONS non-blank and embedded) in `app/src/test/kotlin/com/manuel/mvp/llm/PromptBuilderTest.kt`
- [ ] T002 Confirm the file references a single, consistently-named `PromptBuilder` contract across all test methods (no ad hoc signature drift), by inspection, depends on T001
