// Native JNI bridge from com.manuel.mvp.stt.NativeWhisperEngine to whisper.cpp (T013).
//
// Wraps the vendored whisper.cpp library (../../../../third_party/whisper.cpp, linked as the
// `whisper` CMake target by ../../../CMakeLists.txt) to satisfy T012's WhisperEngine contract:
// given a whisper.cpp context handle and raw 16kHz mono PCM16 audio (see
// t011-audio-capture-manager-spec.md / the "16kHz mono PCM16 end to end" app memory decision),
// produce transcribed text and an overall confidence score.
//
// Confidence is the average per-token probability (whisper_full_get_token_p) across every token
// of every segment produced by whisper_full() -- a simple, direct proxy for "how sure was the
// model", consistent with this project's dependency-light approach elsewhere (no separate
// calibration model). WhisperTranscriber (Kotlin side, T012) compares this against a configured
// threshold to decide whether to accept the transcription or ask the user to repeat (FR-005).
//
// NOT built or run in this sandbox: no NDK/CMake toolchain is installed here (the same limitation
// T002 already documented for the overall native build harness -- see app/CMakeLists.txt's own
// comments). This file is written and reviewed line-by-line against the real
// third_party/whisper.cpp/include/whisper.h API (fetched via `git submodule update --init` as
// part of this task, since it wasn't previously checked out), but it has not been compiled or
// linked. See t013-whisper-transcriber-spec.md's Clarifications.

#include <jni.h>

#include <string>
#include <vector>

#include "whisper.h"

namespace {

// Converts 16-bit signed PCM samples (this app's audio format end to end, see
// AudioCaptureManager) to whisper.cpp's expected 32-bit float format, normalized to [-1.0, 1.0]
// via the standard PCM16 full-scale divisor (32768.0f) -- matching whisper.cpp's own documented
// "RAW audio data in 32-bit floating point format" usage convention.
std::vector<float> ToFloatPcm(JNIEnv *env, jshortArray audio_pcm16) {
    const jsize length = env->GetArrayLength(audio_pcm16);
    std::vector<float> pcmf32(static_cast<size_t>(length));

    jshort *samples = env->GetShortArrayElements(audio_pcm16, nullptr);
    for (jsize i = 0; i < length; ++i) {
        pcmf32[static_cast<size_t>(i)] = static_cast<float>(samples[i]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(audio_pcm16, samples, JNI_ABORT);

    return pcmf32;
}

// Concatenation of every segment's text, and the average per-token probability across the whole
// transcription -- this bridge's confidence proxy (see file header).
struct TranscriptionSummary {
    std::string text;
    float confidence;
};

TranscriptionSummary Summarize(whisper_state *state) {
    std::string text;
    double sum_p = 0.0;
    int n_total_tokens = 0;

    const int n_segments = whisper_full_n_segments_from_state(state);
    for (int i = 0; i < n_segments; ++i) {
        text += whisper_full_get_segment_text_from_state(state, i);

        const int n_tokens = whisper_full_n_tokens_from_state(state, i);
        for (int j = 0; j < n_tokens; ++j) {
            sum_p += whisper_full_get_token_p_from_state(state, i, j);
            ++n_total_tokens;
        }
    }

    // Zero tokens covers both "whisper_full failed" and "genuinely silent/empty audio" -- either
    // way, zero confidence correctly drives WhisperTranscriber's repeat-request path (T012),
    // with no separate error signal needed.
    const float confidence = n_total_tokens > 0
        ? static_cast<float>(sum_p / n_total_tokens)
        : 0.0f;

    return TranscriptionSummary{text, confidence};
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_manuel_mvp_stt_NativeWhisperEngine_nativeInit(
    JNIEnv *env, jobject /* thiz */, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(model_path, path);

    // whisper_init_from_file_with_params returns NULL on failure (per whisper.h's doc comment);
    // NativeWhisperEngine's Kotlin-side init{} check() turns a 0 handle into a fail-fast exception
    // (FR-006), rather than leaving a half-usable engine around.
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_manuel_mvp_stt_NativeWhisperEngine_nativeTranscribe(
    JNIEnv *env, jobject /* thiz */, jlong context_handle, jshortArray audio_pcm16) {
    auto *ctx = reinterpret_cast<whisper_context *>(context_handle);

    const std::vector<float> pcmf32 = ToFloatPcm(env, audio_pcm16);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language = "es";          // FR-005: target users are Spanish-speaking.
    wparams.translate = false;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.no_timestamps = true;
    wparams.n_threads = 4;

    // A fresh whisper_state per call, instead of reusing ctx's implicit shared state (what plain
    // whisper_full() does) across every transcription -- verified on-device: reusing the same
    // state/compute-graph-scheduler across two calls with different audio lengths crashed the
    // whole app with a native ggml_abort inside ggml_backend_sched_alloc_graph. A fresh state gets
    // its own graph sized exactly for this call's input, sidestepping whatever the shared
    // scheduler's reallocation path hit.
    TranscriptionSummary summary{"", 0.0f};
    whisper_state *state = whisper_init_state(ctx);
    if (state != nullptr) {
        if (whisper_full_with_state(ctx, state, wparams, pcmf32.data(), static_cast<int>(pcmf32.size())) == 0) {
            summary = Summarize(state);
        }
        // A non-zero whisper_full_with_state return, or a null state, falls through with the
        // zero-confidence, empty-text default above (see Summarize()'s comment) instead of
        // throwing across the JNI boundary.
        whisper_free_state(state);
    }

    jclass result_class = env->FindClass("com/manuel/mvp/stt/TranscriptionResult");
    jmethodID ctor = env->GetMethodID(result_class, "<init>", "(Ljava/lang/String;F)V");
    jstring text = env->NewStringUTF(summary.text.c_str());

    return env->NewObject(result_class, ctor, text, summary.confidence);
}

extern "C" JNIEXPORT void JNICALL
Java_com_manuel_mvp_stt_NativeWhisperEngine_nativeRelease(
    JNIEnv * /* env */, jobject /* thiz */, jlong context_handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(context_handle);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}
