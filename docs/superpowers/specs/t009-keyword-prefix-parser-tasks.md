# Tasks: KeywordPrefixParser unit tests (T009)

## Tasks

- [ ] T001 Write `KeywordPrefixParserTest.kt` covering the 7 acceptance scenarios (prefixed extraction, case-insensitivity, optional comma separator, no-keyword discard, keyword-with-no-instruction discard, keyword-not-at-start discard, tolerated leading whitespace) in `app/src/test/kotlin/com/manuel/mvp/audio/KeywordPrefixParserTest.kt`
- [ ] T002 Confirm the file references a `KeywordPrefixParser.parse(String): String?` contract consistently across all test methods (no ad hoc signature drift), by inspection, depends on T001
