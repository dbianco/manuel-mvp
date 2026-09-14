package com.manuel.mvp.llm

/**
 * Bridges to the vendored llama.cpp library (Llama 3.2 3B Instruct, GGUF Q4_K_M) via the
 * `manuel_native` shared library's JNI glue (`app/src/main/cpp/llama_jni.cpp`, T015), to satisfy
 * FR-007/FR-008: generating a response from a [PromptBuilder]-assembled prompt, fully on-device.
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
     */
    fun generate(prompt: String): String = nativeGenerate(contextHandle, prompt)

    /** Frees the native llama.cpp model and context. This engine must not be used again after calling this. */
    fun release() {
        nativeRelease(contextHandle)
    }

    private external fun nativeInit(modelPath: String, maxTokens: Int): Long

    private external fun nativeGenerate(contextHandle: Long, prompt: String): String

    private external fun nativeRelease(contextHandle: Long)

    companion object {
        const val DEFAULT_MAX_TOKENS = 256

        init {
            System.loadLibrary("manuel_native")
        }
    }
}
