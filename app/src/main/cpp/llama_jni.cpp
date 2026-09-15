// Native JNI bridge from com.manuel.mvp.llm.LlamaEngine to llama.cpp (T015).
//
// Wraps the vendored llama.cpp library (../../../../third_party/llama.cpp, linked as the `llama`
// CMake target by ../../../CMakeLists.txt) to satisfy FR-007/FR-008: given a PromptBuilder
// (Kotlin side, T015)-assembled prompt, generate a response fully on-device.
//
// Deliberately common-library-free: T002's CMakeLists.txt disables LLAMA_BUILD_COMMON (only
// needed by llama.cpp's own tools/examples), so this file cannot use `common_sampler` /
// `common_chat_templates` (from common.h/chat.h/sampling.h). Instead it follows the same minimal,
// common-library-free API sequence as the vendored third_party/llama.cpp/examples/simple/simple.cpp
// at this exact pinned commit: load model -> tokenize -> init context+sampler -> decode/sample
// loop -> detokenize. examples/llama.android (the fancier Android JNI reference in this same
// submodule) was deliberately NOT used as a model here because it depends on common_sampler and
// common_chat_templates, which this build doesn't link.
//
// No chat-template formatting: Llama 3.2 3B Instruct is fine-tuned against a specific chat
// template, normally applied via the (here, unavailable) common_chat_templates helper.
// PromptBuilder's plain-text output is tokenized and generated on as-is. This is a deliberate MVP
// simplification -- see t015-prompt-builder-llama-engine-spec.md's Clarifications.
//
// NOT built or run in this sandbox: no NDK/CMake toolchain is installed here (the same limitation
// T002 and T013 already documented). This file is written and reviewed line-by-line against the
// real third_party/llama.cpp/include/llama.h API and examples/simple/simple.cpp (fetched via
// `git submodule update --init` as part of this task, since it wasn't previously checked out), but
// it has not been compiled or linked.

#include <jni.h>

#include <string>
#include <vector>

#include "llama.h"

namespace {

// Frees ctx (if non-null) and model (if non-null) -- shared cleanup for both the "context init
// failed after a successful model load" path (FR-007) and nativeRelease.
void FreeLlamaResources(llama_context *ctx, llama_model *model) {
    if (ctx != nullptr) {
        llama_free(ctx);
    }
    if (model != nullptr) {
        llama_model_free(model);
    }
}

// Bundles everything nativeInit allocates (model, context, sampler, vocab handle, the configured
// max-tokens cap) behind the single opaque jlong handle nativeInit hands back to Kotlin.
struct LlamaSession {
    llama_model *model;
    llama_context *ctx;
    llama_sampler *sampler;
    const llama_vocab *vocab;
    int max_tokens;
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_manuel_mvp_llm_LlamaEngine_nativeInit(
    JNIEnv *env, jobject /* thiz */, jstring model_path, jint max_tokens) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        // FR-007: fail fast -- LlamaEngine's Kotlin-side init{} check() turns a 0 handle into an
        // exception, same convention as NativeWhisperEngine (T013).
        return 0;
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);

    // Context is sized as one generous fixed budget covering both the prompt and up to
    // max_tokens generated tokens, unlike examples/simple/simple.cpp's exact
    // `n_ctx = n_prompt + n_predict - 1` sizing -- the exact prompt length isn't known yet at this
    // point (tokenization happens per-generate call, not at init). If a prompt ever exceeds this
    // budget, llama_decode in nativeGenerate's loop below returns non-zero and the loop breaks
    // immediately, returning whatever (possibly empty) text was generated up to that point rather
    // than crashing.
    constexpr int kContextBudget = 4096;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = kContextBudget;
    ctx_params.n_batch = kContextBudget;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        // Context init failed after a successful model load -- free the model rather than leaking
        // it (FR-007).
        llama_model_free(model);
        return 0;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto *session = new LlamaSession{model, ctx, sampler, vocab, static_cast<int>(max_tokens)};
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_manuel_mvp_llm_LlamaEngine_nativeGenerate(
    JNIEnv *env, jobject /* thiz */, jlong context_handle, jstring prompt) {
    auto *session = reinterpret_cast<LlamaSession *>(context_handle);

    // Sessions are reused across conversational turns (T018's ConversationPipeline calls
    // nativeGenerate once per turn on the same handle), but this file never tracks a running
    // sequence position across calls -- every call starts a fresh llama_batch_get_one at
    // position 0 via llama_decode. Without clearing the KV cache first, the second and later
    // turns would decode this new prompt's tokens on top of stale cache entries left at those
    // same positions from the previous turn, corrupting attention over stale context.
    llama_memory_clear(llama_get_memory(session->ctx), /* data */ true);

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    const std::string prompt_str(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    // Two-call tokenize pattern from examples/simple/simple.cpp: first call with tokens=nullptr,
    // max=0 to get the required token count back as a negative number, then allocate and tokenize
    // for real.
    const int n_prompt = -llama_tokenize(
        session->vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
        nullptr, 0, /* add_special */ true, /* parse_special */ true);

    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(
            session->vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
            prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
            /* add_special */ true, /* parse_special */ true) < 0) {
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));

    std::string generated;
    int n_generated = 0;

    while (n_generated < session->max_tokens) {
        if (llama_decode(session->ctx, batch) != 0) {
            break;
        }

        llama_token new_token_id = llama_sampler_sample(session->sampler, session->ctx, -1);

        if (llama_vocab_is_eog(session->vocab, new_token_id)) {
            break;
        }

        char piece_buf[128];
        const int n = llama_token_to_piece(session->vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, true);
        if (n < 0) {
            break;
        }
        generated.append(piece_buf, n);

        batch = llama_batch_get_one(&new_token_id, 1);
        ++n_generated;
    }

    return env->NewStringUTF(generated.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_manuel_mvp_llm_LlamaEngine_nativeRelease(
    JNIEnv * /* env */, jobject /* thiz */, jlong context_handle) {
    auto *session = reinterpret_cast<LlamaSession *>(context_handle);
    if (session == nullptr) {
        return;
    }

    llama_sampler_free(session->sampler);
    FreeLlamaResources(session->ctx, session->model);
    delete session;
}
