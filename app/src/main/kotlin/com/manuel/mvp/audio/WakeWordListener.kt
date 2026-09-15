package com.manuel.mvp.audio

import android.content.Context
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordDetection
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Wraps openWakeWord's [WakeWordEngine] to arm/disarm background listening for the "Manuel"
 * keyword (FR-002). [resolveInstruction] validates the STT transcript captured *after* a
 * detection (FR-003/FR-004): only a non-blank transcript is surfaced as a conversation turn;
 * a blank one means the caller returns to the keyword-waiting state without producing a spoken
 * response or registering a visible failed turn. See [resolveInstruction]'s doc for why this does
 * not check for a "manuel" text prefix, despite [KeywordPrefixParser] existing for that purpose.
 *
 * [Context] and [CoroutineScope] are taken from the caller rather than owned internally, so
 * lifecycle (cancellation, cleanup) stays with whoever constructs this listener.
 */
class WakeWordListener(
    context: Context,
    scope: CoroutineScope,
    modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
    threshold: Float = DEFAULT_THRESHOLD,
    detectionCooldownMs: Long = DEFAULT_DETECTION_COOLDOWN_MS,
) {

    private val engine = WakeWordEngine(
        context = context,
        models = listOf(
            WakeWordModel(
                name = KEYWORD_MODEL_NAME,
                modelPath = modelAssetPath,
                threshold = threshold,
            ),
        ),
        detectionMode = DetectionMode.SINGLE_BEST,
        detectionCooldownMs = detectionCooldownMs,
        scope = scope,
    )

    /** Emits once per acoustic detection of the "Manuel" keyword while armed. */
    val armedDetections: Flow<WakeWordDetection> = engine.detections

    /** Starts background listening for the keyword (FR-002). Call on "Escuchar". */
    fun arm() {
        engine.start()
    }

    /** Stops background listening, halting any capture in progress (FR-001's "Dejar de escuchar"). */
    fun disarm() {
        engine.stop()
    }

    /** Releases the underlying engine's resources. Call when the listener is no longer needed. */
    fun release() {
        engine.release()
    }

    /**
     * Validates the STT transcript captured after a wake-word detection (FR-003/FR-004). Returns
     * the extracted instruction, or `null` when there's no clear instruction -- callers MUST treat
     * `null` as "return to keyword-waiting state silently".
     *
     * Does NOT delegate to [KeywordPrefixParser] here: [transcript] is [AudioCaptureManager]'s
     * capture of the audio *following* [engine]'s acoustic wake-word detection -- a fresh
     * `AudioRecord` session started only after the detection fires, so it can never contain the
     * word "manuel" itself (that utterance was already consumed by [engine]'s own separate
     * recorder). Requiring a textual "manuel" prefix here -- verified against a real device, not
     * just review -- meant every real transcript was silently discarded, regardless of how
     * accurately whisper transcribed it. [KeywordPrefixParser] remains a correct, tested component
     * for its own documented contract (parsing "Manuel, <instrucción>"-shaped text); it's simply
     * the wrong tool for text that was never going to contain the keyword in the first place.
     */
    fun resolveInstruction(transcript: String): String? = transcript.trim().ifEmpty { null }

    companion object {
        const val KEYWORD_MODEL_NAME = "manuel"
        const val DEFAULT_MODEL_ASSET_PATH = "wakeword/manuel.onnx"
        // Lowered from 0.5 after real-device testing: the trained model (synthetic TTS voices
        // only, reduced sample count) misses real speech often enough at 0.5 to be unreliable.
        // 0.35 trades a few more false positives for meaningfully better recall.
        const val DEFAULT_THRESHOLD = 0.35f
        const val DEFAULT_DETECTION_COOLDOWN_MS = 2_000L
    }
}
