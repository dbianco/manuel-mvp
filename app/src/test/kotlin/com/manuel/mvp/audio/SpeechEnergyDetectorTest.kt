package com.manuel.mvp.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests defining the behavioral contract of [SpeechEnergyDetector] (T011): a pure,
 * Android-independent per-frame speech/silence classifier over raw PCM16 samples, used by
 * [CaptureWindowPolicy] (via [AudioCaptureManager]) to decide when a post-wake-word capture window
 * has speech in it. Classification is a simple RMS-energy-vs-threshold check -- no Android
 * dependency, no real microphone needed.
 */
class SpeechEnergyDetectorTest {

    private val detector = SpeechEnergyDetector(threshold = 500.0)

    /** A frame of near-maximum-amplitude samples (loud) is classified as speech. */
    @Test
    fun `classifies a loud frame as speech`() {
        val loudFrame = ShortArray(320) { i ->
            (Short.MAX_VALUE * sin(i * 0.1)).toInt().toShort()
        }

        assertTrue(detector.isSpeech(loudFrame))
    }

    /** A frame of all-zero (or near-zero) samples -- silence -- is classified as non-speech. */
    @Test
    fun `classifies a silent frame as non-speech`() {
        val silentFrame = ShortArray(320) { 0 }

        assertFalse(detector.isSpeech(silentFrame))
    }

    /** An empty frame (nothing read yet) is treated as non-speech, never throws. */
    @Test
    fun `classifies an empty frame as non-speech`() {
        val emptyFrame = ShortArray(0)

        assertFalse(detector.isSpeech(emptyFrame))
    }

    /** RMS energy exactly at the configured threshold counts as speech (inclusive boundary). */
    @Test
    fun `treats RMS energy exactly at the threshold as speech`() {
        val constantAmplitudeFrame = ShortArray(10) { 500 }

        assertTrue(detector.isSpeech(constantAmplitudeFrame))
    }

    /** RMS energy just below the configured threshold does not count as speech. */
    @Test
    fun `treats RMS energy just below the threshold as non-speech`() {
        val constantAmplitudeFrame = ShortArray(10) { 499 }

        assertFalse(detector.isSpeech(constantAmplitudeFrame))
    }
}
