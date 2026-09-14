package com.manuel.mvp.audio

/**
 * The outcome of feeding one audio frame to [CaptureWindowPolicy.onFrame].
 */
sealed class CaptureDecision {
    /** Keep recording; none of the stop conditions have fired yet. */
    object Continue : CaptureDecision()

    /** Stop recording; speech was detected during the window, return the captured audio. */
    object StopWithAudio : CaptureDecision()

    /** Stop recording; no speech was ever detected, silently discard (no audio, no error). */
    object StopSilently : CaptureDecision()
}

/**
 * Pure, Android-independent state machine deciding when [AudioCaptureManager]'s post-wake-word
 * microphone capture window should stop, per `t011-audio-capture-manager-spec.md` FR-003.
 *
 * Driven one frame at a time via [onFrame], given the elapsed time since the capture started and
 * whether that frame was classified as speech (by [SpeechEnergyDetector], not this class). No
 * `Clock`/timer dependency: the caller already tracks elapsed time per audio frame it reads, so
 * this class only tracks *when* (in that elapsed-time timeline) speech started and was last seen.
 *
 * Stop conditions, in priority order:
 * 1. [maxCaptureDurationMs] elapsed (hard cap) -- stops with audio if speech was ever detected,
 *    otherwise stops silently. Checked first so it always wins over the other two.
 * 2. No speech detected within [noSpeechTimeoutMs] of the start -- stops silently.
 * 3. [endOfSpeechSilenceMs] of trailing silence following the last detected speech -- stops with
 *    audio.
 */
class CaptureWindowPolicy(
    private val maxCaptureDurationMs: Long,
    private val noSpeechTimeoutMs: Long,
    private val endOfSpeechSilenceMs: Long,
) {
    private var speechStartedAtMs: Long? = null
    private var lastSpeechAtMs: Long? = null

    fun onFrame(elapsedMs: Long, isSpeech: Boolean): CaptureDecision {
        if (isSpeech) {
            if (speechStartedAtMs == null) {
                speechStartedAtMs = elapsedMs
            }
            lastSpeechAtMs = elapsedMs
        }

        if (elapsedMs >= maxCaptureDurationMs) {
            return if (speechStartedAtMs != null) CaptureDecision.StopWithAudio else CaptureDecision.StopSilently
        }

        val hasSpeechStarted = speechStartedAtMs != null
        if (!hasSpeechStarted) {
            return if (elapsedMs >= noSpeechTimeoutMs) CaptureDecision.StopSilently else CaptureDecision.Continue
        }

        val silenceSinceLastSpeechMs = elapsedMs - (lastSpeechAtMs ?: elapsedMs)
        return if (silenceSinceLastSpeechMs >= endOfSpeechSilenceMs) {
            CaptureDecision.StopWithAudio
        } else {
            CaptureDecision.Continue
        }
    }
}
