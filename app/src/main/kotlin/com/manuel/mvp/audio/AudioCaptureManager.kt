package com.manuel.mvp.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a bounded window of microphone audio for the instruction that follows a wake-word
 * detection (T010's `WakeWordListener`), per `t011-audio-capture-manager-spec.md`.
 *
 * Records at 16 kHz mono 16-bit PCM (FR-001) -- matching both openWakeWord's own recording format
 * (`com.rementia.openwakeword.lib.audio.AudioRecorder.SAMPLE_RATE = 16000`) and whisper.cpp's
 * expected input, so T013's `WhisperTranscriber` needs no resampling. Opens its own [AudioRecord]
 * session rather than reusing openWakeWord's internal recorder (private to `WakeWordEngine`, and a
 * different sample representation) -- see spec.md's Clarifications.
 *
 * The stop decision is delegated entirely to [CaptureWindowPolicy], fed a per-frame speech/silence
 * signal from [SpeechEnergyDetector] -- this class contains no decision logic of its own beyond
 * wiring `AudioRecord` reads to those two pure components (FR-003/FR-004).
 */
class AudioCaptureManager(
    private val sampleRate: Int = SAMPLE_RATE,
    private val maxCaptureDurationMs: Long = DEFAULT_MAX_CAPTURE_DURATION_MS,
    private val noSpeechTimeoutMs: Long = DEFAULT_NO_SPEECH_TIMEOUT_MS,
    private val endOfSpeechSilenceMs: Long = DEFAULT_END_OF_SPEECH_SILENCE_MS,
    private val speechEnergyThreshold: Double = DEFAULT_SPEECH_ENERGY_THRESHOLD,
) {

    /**
     * Starts recording and returns the captured audio as raw PCM16 samples once the capture
     * window ends with detected speech (FR-002), or `null` when it ends without any (FR-004's
     * "silencio... sin producir una respuesta hablada") -- callers MUST treat `null` as "return to
     * keyword-waiting state silently", the same way [WakeWordListener.resolveInstruction] returning
     * `null` is handled.
     *
     * Requires `android.permission.RECORD_AUDIO`, granted by the caller before arming (see
     * `MainActivity`'s runtime permission request) -- callers must not invoke this before that
     * permission is confirmed granted, or the underlying `AudioRecord` construction will throw.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun captureInstruction(): ShortArray? = withContext(Dispatchers.IO) {
        val frameSize = sampleRate / FRAMES_PER_SECOND
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        check(minBufferSize > 0) { "AudioRecord.getMinBufferSize returned an invalid size: $minBufferSize" }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(minBufferSize, frameSize * BUFFER_SIZE_IN_FRAMES * Short.SIZE_BYTES),
        )

        val policy = CaptureWindowPolicy(
            maxCaptureDurationMs = maxCaptureDurationMs,
            noSpeechTimeoutMs = noSpeechTimeoutMs,
            endOfSpeechSilenceMs = endOfSpeechSilenceMs,
        )
        val speechEnergyDetector = SpeechEnergyDetector(threshold = speechEnergyThreshold)
        val capturedFrames = mutableListOf<ShortArray>()

        try {
            audioRecord.startRecording()
            val startElapsedMs = System.nanoTime() / NANOS_PER_MILLI

            while (true) {
                val frameBuffer = ShortArray(frameSize)
                val samplesRead = audioRecord.read(frameBuffer, 0, frameBuffer.size)
                if (samplesRead <= 0) continue

                val frame = if (samplesRead == frameBuffer.size) frameBuffer else frameBuffer.copyOf(samplesRead)
                capturedFrames.add(frame)

                val elapsedMs = System.nanoTime() / NANOS_PER_MILLI - startElapsedMs
                val isSpeech = speechEnergyDetector.isSpeech(frame)

                when (policy.onFrame(elapsedMs, isSpeech)) {
                    CaptureDecision.Continue -> continue
                    CaptureDecision.StopWithAudio -> return@withContext concatenate(capturedFrames)
                    CaptureDecision.StopSilently -> return@withContext null
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private fun concatenate(frames: List<ShortArray>): ShortArray {
        val result = ShortArray(frames.sumOf { it.size })
        var offset = 0
        for (frame in frames) {
            frame.copyInto(result, offset)
            offset += frame.size
        }
        return result
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        const val DEFAULT_MAX_CAPTURE_DURATION_MS = 8_000L
        const val DEFAULT_NO_SPEECH_TIMEOUT_MS = 3_000L
        const val DEFAULT_END_OF_SPEECH_SILENCE_MS = 1_000L
        const val DEFAULT_SPEECH_ENERGY_THRESHOLD = 500.0

        private const val FRAMES_PER_SECOND = 50 // 20ms frames at 16kHz
        private const val BUFFER_SIZE_IN_FRAMES = 4
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
