package com.manuel.mvp.audio

import kotlin.math.sqrt

/**
 * Pure, Android-independent per-frame speech/silence classifier over raw PCM16 samples, per
 * `t011-audio-capture-manager-spec.md` FR-004. Computes the RMS (root-mean-square) energy of a
 * frame and compares it against [threshold]; no dedicated VAD model, consistent with this
 * project's dependency-light approach elsewhere (see plan.md's Research).
 */
class SpeechEnergyDetector(private val threshold: Double) {

    fun isSpeech(frame: ShortArray): Boolean {
        if (frame.isEmpty()) return false

        var sumOfSquares = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            sumOfSquares += value * value
        }
        val rms = sqrt(sumOfSquares / frame.size)

        return rms >= threshold
    }
}
