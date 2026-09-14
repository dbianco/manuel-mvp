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
 * keyword (FR-002), and validates the STT transcript captured after a detection against
 * [KeywordPrefixParser] (FR-003/FR-004): only a transcript that clearly starts with the keyword
 * and is followed by a non-blank instruction is surfaced as a conversation turn; anything else
 * ([KeywordPrefixParser.parse] returning `null`) means the caller returns to the keyword-waiting
 * state without producing a spoken response or registering a visible failed turn.
 *
 * The acoustic detector ([WakeWordEngine], non-deterministic and hardware-dependent) and the
 * textual keyword-prefix check ([KeywordPrefixParser], pure and already unit-tested by T009) stay
 * independent: [resolveInstruction] is a thin pass-through, not a merge of the two.
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
     * Validates the STT transcript captured after a wake-word detection against the keyword-prefix
     * contract (FR-003/FR-004). Returns the extracted instruction, or `null` when the transcript
     * doesn't clearly start with the keyword followed by an instruction -- callers MUST treat
     * `null` as "return to keyword-waiting state silently".
     */
    fun resolveInstruction(transcript: String): String? = KeywordPrefixParser.parse(transcript)

    companion object {
        const val KEYWORD_MODEL_NAME = "manuel"
        const val DEFAULT_MODEL_ASSET_PATH = "wakeword/manuel.onnx"
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_DETECTION_COOLDOWN_MS = 2_000L
    }
}
