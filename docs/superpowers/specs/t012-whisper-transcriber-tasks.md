# Tasks: WhisperTranscriber unit tests (T012)

## Tasks

- [ ] T001 Write `WhisperTranscriberTest.kt` covering the 6 acceptance scenarios (confidence above threshold accepted, confidence exactly at threshold accepted, confidence below threshold requests repeat, very-low confidence requests repeat, custom threshold changes the boundary, audio array passed through unmodified to the engine seam) in `app/src/test/kotlin/com/manuel/mvp/stt/WhisperTranscriberTest.kt`
- [ ] T002 Confirm the file references a single, consistently-named `WhisperTranscriber` + engine-seam contract across all test methods (no ad hoc signature drift), by inspection, depends on T001
