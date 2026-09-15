package com.manuel.mvp.pipeline

/**
 * The assistant's visible state (FR-001's "estado visible"), exposed by [ConversationPipeline] for
 * the UI layer (T020) to observe and render.
 */
sealed class PipelineState {
    /** Not listening at all -- the initial state, and the state after "Dejar de escuchar". */
    object Disarmed : PipelineState()

    /** Armed and waiting for the wake word ("Anita"). */
    object Armed : PipelineState()

    /** A wake-word detection fired; capturing the instruction audio that follows. */
    object Listening : PipelineState()

    /** Transcribing, searching, and generating a response for a captured instruction. */
    object Processing : PipelineState()

    /** Speaking a response (or the repeat-request prompt) aloud. */
    object Responding : PipelineState()

    /** A turn failed with an exception; [message] is a short, non-personal description. */
    data class Error(val message: String) : PipelineState()
}
