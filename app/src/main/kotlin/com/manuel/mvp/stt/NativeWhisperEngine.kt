package com.manuel.mvp.stt

/**
 * Production [WhisperEngine] implementation, bridging to the vendored whisper.cpp library via the
 * `manuel_native` shared library's JNI glue (`app/src/main/cpp/whisper_jni.cpp`, T013).
 *
 * [modelPath] is a filesystem path to a whisper.cpp GGML/GGUF model file (the "tiny" or "base"
 * model per this task's brief). Packaging that file (bundling it under `assets/models/` vs.
 * downloading it to internal storage on first launch, per the top-level `plan.md`) is a later
 * deployment concern -- this class only needs an already-resolved path.
 *
 * Not exercised by any automated test in this repo: it requires the real native library built and
 * a real model file loaded, neither of which is available in a JVM unit test (see
 * `WhisperTranscriberTest`'s use of a fake `WhisperEngine` instead, T012). [release] MUST be
 * called when this engine is no longer needed, to free the native `whisper_context`.
 */
class NativeWhisperEngine(modelPath: String) : WhisperEngine {

    private val contextHandle: Long = nativeInit(modelPath)

    init {
        check(contextHandle != 0L) { "Failed to load whisper.cpp model from $modelPath" }
    }

    override fun transcribe(audioPcm16: ShortArray): TranscriptionResult =
        nativeTranscribe(contextHandle, audioPcm16)

    /** Frees the native whisper.cpp context. This engine must not be used again after calling this. */
    override fun release() {
        nativeRelease(contextHandle)
    }

    private external fun nativeInit(modelPath: String): Long

    private external fun nativeTranscribe(contextHandle: Long, audioPcm16: ShortArray): TranscriptionResult

    private external fun nativeRelease(contextHandle: Long)

    companion object {
        init {
            System.loadLibrary("manuel_native")
        }
    }
}
