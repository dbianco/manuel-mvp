package com.manuel.mvp.llm

/**
 * Bridges to the vendored llama.cpp library via the `manuel_native` shared library's JNI glue
 * (`app/src/main/cpp/llama_jni.cpp`, T015), to satisfy FR-007/FR-008: generating a response from a
 * [PromptBuilder]-assembled prompt, fully on-device. Currently loaded with Qwen2.5-0.5B-Instruct
 * (Q4_K_M) -- swapped down from Llama 3.2 3B/1B Instruct after on-device testing showed both were
 * too slow for real-time voice interaction on phone CPU with no GPU delegate; see [generate]'s
 * ChatML wrapping, which is Qwen-specific formatting, not Llama's.
 *
 * [modelPath] is a filesystem path to a llama.cpp-compatible GGUF model file. Packaging that file
 * (bundling it under `assets/models/` vs. downloading it to internal storage on first launch, per
 * the top-level `plan.md`) is a later deployment concern, matching `NativeWhisperEngine`'s (T013)
 * and `WakeWordListener`'s (T010) precedent -- this class only needs an already-resolved path.
 *
 * Not exercised by any automated test in this repo: it requires the real native library built and
 * a real model file loaded, neither of which is available in a JVM unit test. [release] MUST be
 * called when this engine is no longer needed, to free the native model and context.
 */
class LlamaEngine(modelPath: String, private val maxTokens: Int = DEFAULT_MAX_TOKENS) {

    private val contextHandle: Long = nativeInit(modelPath, maxTokens)

    init {
        check(contextHandle != 0L) { "Failed to load llama.cpp model from $modelPath" }
    }

    /**
     * Generates a response for [prompt] via greedy-sampled decoding, stopping at whichever comes
     * first: an end-of-generation token, or this engine's [maxTokens] cap (FR-008). Either way,
     * whatever text was generated so far is returned -- a capped-but-non-empty response is still
     * useful output, so there is no separate "discard and retry" path here.
     *
     * Wraps [prompt] in Qwen's ChatML turn markers (`<|im_start|>`/`<|im_end|>`) before generating
     * -- verified on-device without this: fed raw, the model never emits its own end-of-generation
     * token (it has no learned notion of "this turn is over" outside its chat format), so every
     * single call ran the full [maxTokens] budget and degenerated into repeating fragments of its
     * own instructions after the real answer. [PromptBuilder]'s own output/tests are untouched --
     * this wrapping is model-specific formatting, added at the last point before tokenization.
     */
    fun generate(prompt: String): String {
        val chatMlPrompt = "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        return nativeGenerate(contextHandle, chatMlPrompt).trim()
    }

    /** Frees the native llama.cpp model and context. This engine must not be used again after calling this. */
    fun release() {
        nativeRelease(contextHandle)
    }

    private external fun nativeInit(modelPath: String, maxTokens: Int): Long

    private external fun nativeGenerate(contextHandle: Long, prompt: String): String

    private external fun nativeRelease(contextHandle: Long)

    companion object {
        // Lowered from 256: FR-008 wants 2-4 short sentences, and a smaller cap also bounds
        // worst-case latency/rambling on phone hardware if end-of-generation ever fails to fire.
        const val DEFAULT_MAX_TOKENS = 120

        init {
            System.loadLibrary("manuel_native")
        }
    }
}
