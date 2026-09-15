package com.manuel.mvp.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LocalMetricsLogger] (T017): an in-memory, size-bounded aggregator of per-turn
 * stage timings/errors and wake-word activation outcomes, per FR-013. Carries no audio or
 * question/answer text by construction -- [TurnMetrics] and [WakeWordActivationOutcome] have no
 * field for either.
 */
class LocalMetricsLoggerTest {

    /** Scenario 1/2: a recorded turn is retained and reflected in turnCount()/errorCount(). */
    @Test
    fun `records a turn and reflects it in counts`() {
        val logger = LocalMetricsLogger()

        logger.recordTurn(turn(error = null))
        logger.recordTurn(turn(error = "timeout"))

        assertEquals(2, logger.turnCount())
        assertEquals(1, logger.errorCount())
    }

    /** Scenario 3: averageTotalDurationMs() is null, not zero, when nothing has been recorded. */
    @Test
    fun `average total duration is null when no turns are recorded`() {
        val logger = LocalMetricsLogger()

        assertNull(logger.averageTotalDurationMs())
    }

    /** averageTotalDurationMs() computes the mean of recorded totalDurationMs values. */
    @Test
    fun `average total duration reflects recorded turns`() {
        val logger = LocalMetricsLogger()

        logger.recordTurn(turn(totalDurationMs = 1_000))
        logger.recordTurn(turn(totalDurationMs = 3_000))

        assertEquals(2_000.0, logger.averageTotalDurationMs())
    }

    /** Scenario 4: recording beyond the configured max evicts the oldest turn (bounded window). */
    @Test
    fun `evicts the oldest turn once the configured maximum is exceeded`() {
        val logger = LocalMetricsLogger(maxTurnHistory = 2)

        logger.recordTurn(turn(totalDurationMs = 1_000))
        logger.recordTurn(turn(totalDurationMs = 2_000))
        logger.recordTurn(turn(totalDurationMs = 3_000))

        assertEquals(2, logger.turnCount())
        assertEquals(2_500.0, logger.averageTotalDurationMs())
    }

    /** Scenario 5: validActivationRate() reflects the fraction of VALID_TURN outcomes. */
    @Test
    fun `valid activation rate reflects recorded outcomes`() {
        val logger = LocalMetricsLogger()

        logger.recordWakeWordActivation(WakeWordActivationOutcome.VALID_TURN)
        logger.recordWakeWordActivation(WakeWordActivationOutcome.VALID_TURN)
        logger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)
        logger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)

        assertEquals(0.5, logger.validActivationRate())
    }

    /** Scenario 6: validActivationRate() is null when no activations have been recorded. */
    @Test
    fun `valid activation rate is null when no activations are recorded`() {
        val logger = LocalMetricsLogger()

        assertNull(logger.validActivationRate())
    }

    private fun turn(
        totalDurationMs: Long = 100,
        error: String? = null,
    ): TurnMetrics = TurnMetrics(
        captureDurationMs = 10,
        transcriptionDurationMs = 20,
        searchDurationMs = 5,
        generationDurationMs = 60,
        totalDurationMs = totalDurationMs,
        error = error,
    )
}
