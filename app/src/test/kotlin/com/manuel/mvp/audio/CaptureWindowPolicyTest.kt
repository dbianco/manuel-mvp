package com.manuel.mvp.audio

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract of [CaptureWindowPolicy] (T011): the pure,
 * Android-independent state machine that decides when [AudioCaptureManager]'s post-wake-word
 * microphone capture window should stop, and whether the result counts as "captured audio" or a
 * silent discard (FR-002/FR-003/FR-004 of `t011-audio-capture-manager-spec.md`).
 *
 * The policy is driven one frame at a time via [CaptureWindowPolicy.onFrame], fed the elapsed
 * time since the capture started and whether that frame was classified as speech (by the separate
 * [SpeechEnergyDetector], not exercised here) -- no `Clock`/timer dependency, since the caller
 * already tracks elapsed time per audio frame it reads.
 */
class CaptureWindowPolicyTest {

    private lateinit var policy: CaptureWindowPolicy

    @Before
    fun setUp() {
        policy = CaptureWindowPolicy(
            maxCaptureDurationMs = 8_000,
            noSpeechTimeoutMs = 3_000,
            endOfSpeechSilenceMs = 1_000,
        )
    }

    /** Before any timeout/cap is reached, and before speech has started, the policy keeps going. */
    @Test
    fun `continues while waiting for speech before the no-speech timeout`() {
        assertEquals(CaptureDecision.Continue, policy.onFrame(elapsedMs = 0, isSpeech = false))
        assertEquals(CaptureDecision.Continue, policy.onFrame(elapsedMs = 1_500, isSpeech = false))
        assertEquals(CaptureDecision.Continue, policy.onFrame(elapsedMs = 2_999, isSpeech = false))
    }

    /** Scenario 1: speech, then a trailing silence gap of the configured duration, stops with audio. */
    @Test
    fun `stops with audio once trailing silence follows detected speech`() {
        assertEquals(CaptureDecision.Continue, policy.onFrame(elapsedMs = 500, isSpeech = true))
        assertEquals(CaptureDecision.Continue, policy.onFrame(elapsedMs = 900, isSpeech = false))

        val result = policy.onFrame(elapsedMs = 1_500, isSpeech = false)

        assertEquals(CaptureDecision.StopWithAudio, result)
    }

    /** Scenario 2: no speech at all before the no-speech timeout elapses stops silently. */
    @Test
    fun `stops silently when the no-speech timeout elapses with no speech ever detected`() {
        policy.onFrame(elapsedMs = 0, isSpeech = false)
        policy.onFrame(elapsedMs = 1_500, isSpeech = false)

        val result = policy.onFrame(elapsedMs = 3_000, isSpeech = false)

        assertEquals(CaptureDecision.StopSilently, result)
    }

    /** Scenario 3: speech keeps happening (no full trailing-silence gap) until the hard cap -- stops with audio. */
    @Test
    fun `hard cap with prior speech stops with audio, not silently`() {
        policy.onFrame(elapsedMs = 1_000, isSpeech = true)
        policy.onFrame(elapsedMs = 4_000, isSpeech = true)
        policy.onFrame(elapsedMs = 7_500, isSpeech = true)

        val result = policy.onFrame(elapsedMs = 8_000, isSpeech = false)

        assertEquals(CaptureDecision.StopWithAudio, result)
    }

    /**
     * Scenario 4: the hard cap fires with no speech ever detected during the whole window -- stops
     * silently. Uses a `noSpeechTimeoutMs` longer than `maxCaptureDurationMs` so the hard cap, not
     * the no-speech timeout, is what actually fires first -- isolating this branch from scenario 2.
     */
    @Test
    fun `hard cap with no prior speech stops silently, not with empty audio`() {
        val policyWithLongNoSpeechTimeout = CaptureWindowPolicy(
            maxCaptureDurationMs = 8_000,
            noSpeechTimeoutMs = 10_000,
            endOfSpeechSilenceMs = 1_000,
        )
        policyWithLongNoSpeechTimeout.onFrame(elapsedMs = 1_000, isSpeech = false)
        policyWithLongNoSpeechTimeout.onFrame(elapsedMs = 5_000, isSpeech = false)

        val result = policyWithLongNoSpeechTimeout.onFrame(elapsedMs = 8_000, isSpeech = false)

        assertEquals(CaptureDecision.StopSilently, result)
    }
}
