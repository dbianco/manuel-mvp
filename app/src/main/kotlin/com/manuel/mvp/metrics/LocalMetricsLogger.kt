package com.manuel.mvp.metrics

/**
 * Per-turn stage timings and an optional error message, for one valid conversation turn (FR-013).
 * Carries no audio or question/answer text by design -- only numeric durations and an error
 * message. Callers MUST NOT embed question/answer text inside [error]; this type cannot enforce
 * that itself.
 */
data class TurnMetrics(
    val captureDurationMs: Long,
    val transcriptionDurationMs: Long,
    val searchDurationMs: Long,
    val generationDurationMs: Long,
    val totalDurationMs: Long,
    val error: String? = null,
)

/**
 * The app's own observable classification of a wake-word acoustic detection, per FR-013's
 * "posibles falsos positivos": [VALID_TURN] means the activation led to a usable instruction and a
 * generated response; [POSSIBLE_FALSE_POSITIVE] means it fired but nothing usable followed (no
 * speech captured, low transcription confidence, or the keyword-prefix check rejected the
 * transcript). A genuine false *negative* (a missed activation) produces no event at all, so it
 * cannot be represented here -- that's a T022 field-test concern, not something this app can
 * self-report.
 */
enum class WakeWordActivationOutcome {
    VALID_TURN,
    POSSIBLE_FALSE_POSITIVE,
}

/**
 * In-memory, size-bounded aggregator of [TurnMetrics] and [WakeWordActivationOutcome] records
 * (FR-013). Not persisted to disk -- an MVP simplification, since FR-013 only requires metrics to
 * stay local and free of audio/question text, not that they survive an app restart (see
 * `t017-local-metrics-logger-spec.md`'s Clarifications).
 */
class LocalMetricsLogger(private val maxTurnHistory: Int = DEFAULT_MAX_TURN_HISTORY) {

    private val turns = mutableListOf<TurnMetrics>()
    private val activationOutcomes = mutableListOf<WakeWordActivationOutcome>()

    fun recordTurn(metrics: TurnMetrics) {
        turns.add(metrics)
        if (turns.size > maxTurnHistory) {
            turns.removeAt(0)
        }
    }

    fun recordWakeWordActivation(outcome: WakeWordActivationOutcome) {
        activationOutcomes.add(outcome)
    }

    fun turnCount(): Int = turns.size

    fun errorCount(): Int = turns.count { it.error != null }

    /** The mean of every retained turn's [TurnMetrics.totalDurationMs], or `null` if none are recorded. */
    fun averageTotalDurationMs(): Double? =
        if (turns.isEmpty()) null else turns.map { it.totalDurationMs }.average()

    /** The fraction of recorded activations classified [WakeWordActivationOutcome.VALID_TURN], or `null` if none are recorded. */
    fun validActivationRate(): Double? =
        if (activationOutcomes.isEmpty()) {
            null
        } else {
            activationOutcomes.count { it == WakeWordActivationOutcome.VALID_TURN }.toDouble() / activationOutcomes.size
        }

    companion object {
        const val DEFAULT_MAX_TURN_HISTORY = 100
    }
}
