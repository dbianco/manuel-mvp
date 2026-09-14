package com.manuel.mvp.stt

/**
 * Result of one whisper.cpp transcription attempt: the transcribed text and an overall confidence
 * score. [confidence] is the average per-token probability across every segment produced by the
 * underlying engine (see [NativeWhisperEngine]'s native bridge for the real computation) -- a
 * simple, direct proxy for "how sure was the model", not independently calibrated.
 */
data class TranscriptionResult(val text: String, val confidence: Float)

/**
 * Outcome of [WhisperTranscriber.transcribe]: either an accepted transcription, or a request that
 * the user repeat the instruction because the transcription wasn't confident enough (FR-005).
 */
sealed class TranscriptionOutcome {
    data class Transcribed(val text: String) : TranscriptionOutcome()
    object RepeatRequested : TranscriptionOutcome()
}

/**
 * The injectable boundary standing in for the real whisper.cpp JNI bridge. [NativeWhisperEngine]
 * is the production implementation; `WhisperTranscriberTest` (T012) substitutes a fake so the
 * confidence-threshold decision below can be unit-tested without real native code or a model file.
 */
interface WhisperEngine {
    fun transcribe(audioPcm16: ShortArray): TranscriptionResult
}

/**
 * Transcribes captured instruction audio (T011's `AudioCaptureManager` output: 16 kHz mono PCM16)
 * locally via [engine], accepting the result only when its confidence meets [confidenceThreshold]
 * (inclusive) -- otherwise requests that the user repeat the instruction (FR-005), rather than
 * acting on an unreliable transcription. See `WhisperTranscriberTest` (T012) for the full
 * behavioral contract this satisfies exactly.
 */
class WhisperTranscriber(
    private val engine: WhisperEngine,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
) {
    fun transcribe(audioPcm16: ShortArray): TranscriptionOutcome {
        val result = engine.transcribe(audioPcm16)
        return if (result.confidence >= confidenceThreshold) {
            TranscriptionOutcome.Transcribed(result.text)
        } else {
            TranscriptionOutcome.RepeatRequested
        }
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6f
    }
}
