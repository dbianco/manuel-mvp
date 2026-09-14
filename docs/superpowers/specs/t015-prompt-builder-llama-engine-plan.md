# Plan: PromptBuilder + LlamaEngine implementation (T015)

## Technical Context

- Language: Kotlin (`app/src/main/kotlin/com/manuel/mvp/llm/`) + C++17 (`app/src/main/cpp/llama_jni.cpp`), matching `manuel_native`'s `target_compile_features(manuel_native PRIVATE cxx_std_17)`.
- Primary dependencies: the vendored `third_party/llama.cpp` submodule (registered by T002, already linked into the `manuel_native` CMake target as the `llama` library, with `LLAMA_BUILD_COMMON`/`_TESTS`/`_TOOLS`/`_EXAMPLES`/`_SERVER`/`_APP` all explicitly disabled); no new Gradle or CMake dependency. JNI (`<jni.h>`) for the native bridge, same as T013's `whisper_jni.cpp`.
- Storage: none — `PromptBuilder` does pure string assembly; `LlamaEngine` reads a caller-supplied model path, no new asset packaging (see spec.md's Clarifications).
- Testing tools: `PromptBuilder` is verified against T014's existing `PromptBuilderTest.kt` (JVM, standalone `kotlinc`+`JUnitCore`, no Android SDK). `LlamaEngine.kt`/`llama_jni.cpp` have no automated test — the Kotlin side is verified by standalone compile (same precedent as `WakeWordListener`/`NativeWhisperEngine`), and the C++ side by careful manual review against the real `llama.h` header AND llama.cpp's own `examples/simple/simple.cpp` reference, since no NDK/CMake toolchain exists in this sandbox (same limitation T002/T013 already documented).
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`, native libs built for `arm64-v8a`/`x86_64` (per T002's `abiFilters`).
- Performance goals and constraints: not specified beyond the top-level spec's SC-005 (≤15s end-to-end response) — a field-test criterion (T022), out of scope for this unit of work. `n_threads`/context-size defaults are reasonable choices, not independently benchmarked here.

## Constitution Check

- No personal data in logs or error messages: `PromptBuilder`/`LlamaEngine` do no logging of their own; the assembled prompt and generated text are only ever returned to the caller. Satisfied.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — llama.cpp runs fully on-device, no network calls.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — `PromptBuilder`'s shape is already pinned by T014's test and is not changed here; `LlamaEngine` is a brand-new internal type.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved; llama.cpp needs no account or API key.
- Tests run in CI before merge and a red build blocks the merge: `PromptBuilderTest.kt` becomes green with this feature's `PromptBuilder.kt`, satisfying the T021 CI gate for that file. `llama_jni.cpp`/`LlamaEngine.kt` have no automated test to gate on (see Testing tools above); field-validated once a real NDK/CMake build is possible, and ultimately via T022's manual protocol.
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/llm/
├── PromptBuilder.kt   # new: build(currentInstruction, ragFragments, sessionHistory) + SYSTEM_INSTRUCTIONS (T014's contract)
└── LlamaEngine.kt     # new: loads a GGUF model, generate(prompt: String): String, JNI external fun declarations

app/src/main/cpp/
└── llama_jni.cpp      # new: JNI glue calling into the vendored llama.cpp library

app/CMakeLists.txt     # modified: adds llama_jni.cpp as a manuel_native source
```

No other files are created or modified except this plan/spec/tasks documentation and `docs/superpowers/specs/tasks.md` (marking T015 done). `app/src/test/kotlin/com/manuel/mvp/llm/PromptBuilderTest.kt` (T014) is read-only input to this feature and is not touched.

## Research

- **`PromptBuilder`'s assembly order**: history section, then a RAG-fragments section, then the current instruction, each under a plain-text label — satisfies T014's ordering assertion (history index < fragment index < instruction index) directly. Empty `sessionHistory`/`ragFragments` lists simply produce an empty section body (no placeholder text needed to satisfy T014's tests, which only assert presence/absence of real content, not section-header text).
- **`SYSTEM_INSTRUCTIONS` wording**: written in Spanish, explicitly covering FR-008's four rules (responder en español; 2 a 4 frases; lenguaje simple para nivel inicial/primario; decir explícitamente cuando no hay información suficiente en vez de inventar) plus a sentence operationalizing FR-007's priority rule for the LLM itself ("si el fragmento del turno actual contradice el historial, priorizá el fragmento actual"), placed at the very start of the built prompt (a system-prompt-like position) so the model sees the rules before any content.
- **Real llama.cpp API surface used** (confirmed by reading the vendored `third_party/llama.cpp/include/llama.h`, checked out via `git submodule update --init --depth 1 third_party/llama.cpp` since it wasn't previously present, and cross-checked against `third_party/llama.cpp/examples/simple/simple.cpp` — the canonical minimal-usage reference at this exact pinned commit, chosen specifically because it uses no `common`/`chat.h` helpers, unlike the fancier `examples/llama.android` reference app, which does depend on those and would require re-enabling `LLAMA_BUILD_COMMON` against T002's deliberate choice): `llama_model_default_params()`, `llama_model_load_from_file(path, params)`, `llama_model_get_vocab(model)`, `llama_tokenize(vocab, text, len, tokens, max, add_special, parse_special)` (two-call pattern: first with `tokens=NULL, max=0` to get the required count as a negative return, then again with an allocated buffer), `llama_context_default_params()`, `llama_init_from_model(model, ctx_params)`, `llama_sampler_chain_default_params()`, `llama_sampler_chain_init(params)`, `llama_sampler_chain_add(chain, llama_sampler_init_greedy())`, `llama_batch_get_one(tokens, n_tokens)`, `llama_decode(ctx, batch)`, `llama_sampler_sample(sampler, ctx, -1)`, `llama_vocab_is_eog(vocab, token)`, `llama_token_to_piece(vocab, token, buf, buf_size, lstrip=0, special=true)`, `llama_sampler_free`, `llama_free`, `llama_model_free`.
- **JNI result shape**: `nativeGenerate` returns a plain `jstring` (unlike `whisper_jni.cpp`'s `nativeTranscribe`, which constructs a `TranscriptionResult` object) — `LlamaEngine.generate(prompt): String` has only one return value, so there's no multi-field result to construct via `NewObject`; the [[JNI NewObject result-construction decision]] from T013 doesn't need to apply here, it only matters when a native call needs to hand back more than one field atomically.
- **Fail-fast on load/init failure (FR-007)**: `llama_model_load_from_file` returns `NULL` on failure, and `llama_init_from_model` also returns `NULL` on failure — the native `nativeInit` function frees the model (`llama_model_free`) before returning a `0` handle if context creation fails, so a partially-successful load never leaks the model; `LlamaEngine`'s Kotlin-side `init { check(...) }` throws immediately, mirroring `NativeWhisperEngine`'s convention exactly.
- **Max-token cap and stopping (FR-008)**: the decode loop (mirroring `examples/simple/simple.cpp`'s structure) runs until either `llama_vocab_is_eog` fires or a configured `maxTokens` count of sampled tokens is reached, appending each `llama_token_to_piece` result to an accumulating `std::string` and returning it either way — no exception on hitting the cap, since a capped-but-non-empty response is still useful output (unlike whisper.cpp's confidence-driven repeat-request path, there is no equivalent "discard and retry" concept here; FR-008's "2 a 4 frases" length control is a `SYSTEM_INSTRUCTIONS` prompt instruction plus this cap as a hard backstop, not a separate validation layer in `LlamaEngine`).
- **Context size**: `ctx_params.n_ctx` set to the prompt's token count plus `maxTokens` (mirroring `examples/simple/simple.cpp`'s `n_ctx = n_prompt + n_predict - 1` sizing), so the context is exactly large enough for one full generation, no wasted memory allocation and no separate configurable context-size knob to get wrong.
- **No NDK/CMake toolchain in this sandbox**: confirmed already by T002/T013 (`cmake`/`ninja` both "command not found"). `llama_jni.cpp` is written and reviewed against the real header and reference example (SC-003) but not compiled — the same limitation, not re-verified again here.
