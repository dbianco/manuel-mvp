# Tasks: LocalMetricsLogger (T017)

## Tasks

- [ ] T001 Write `LocalMetricsLoggerTest.kt` covering the 6 acceptance scenarios (record+count, errorCount, null average with no data, bounded eviction, validActivationRate, null rate with no data) in `app/src/test/kotlin/com/manuel/mvp/metrics/LocalMetricsLoggerTest.kt`
- [ ] T002 Implement `LocalMetricsLogger.kt` (`TurnMetrics`, `WakeWordActivationOutcome`, `LocalMetricsLogger` with bounded rolling window) in `app/src/main/kotlin/com/manuel/mvp/metrics/LocalMetricsLogger.kt` to satisfy T001, depends on T001
