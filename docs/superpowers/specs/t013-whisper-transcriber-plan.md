# Plan: WhisperTranscriber implementation (T013)

## Technical Context

- Language: Kotlin (`app/src/main/kotlin/com/manuel/mvp/stt/`) + C++17 (`app/src/main/cpp/whisper_jni.cpp`), matching `manuel_native`'s existing `target_compile_features(manuel_native PRIVATE cxx_std_17)`.
- Primary dependencies: the vendored `third_party/whisper.cpp` submodule (registered by T002, already linked into the `manuel_native` CMake target as the `whisper` library); no new Gradle or CMake dependency. JNI (`<jni.h>`, provided by the Android NDK) for the native bridge.
- Storage: none — model loading reads a caller-supplied filesystem path; no database, no new asset packaging (see spec.md's Clarifications).
- Testing tools: `WhisperTranscriber`/`WhisperEngine`/`TranscriptionResult`/`TranscriptionOutcome` are verified against T012's existing `WhisperTranscriberTest.kt` (JVM, standalone `kotlinc`+`JUnitCore`, no Android SDK). `NativeWhisperEngine.kt` and `whisper_jni.cpp` have no automated test — the Kotlin side is verified by standalone compile (same precedent as `WakeWordListener`, T010), and the C++ side by careful manual review against the real `whisper.h` header, since no NDK/CMake toolchain exists in this sandbox to actually build it (same limitation T002 already documented).
- Target platform: Android app module `com.manuel.mvp`, `minSdk = 29`, native libs built for `arm64-v8a`/`x86_64` (per T002's `abiFilters`).
- Performance goals and constraints: not specified beyond the top-level spec's SC-004 (≥80% transcription accuracy) and general response-time budget (SC-005, ≤15s end-to-end) — both are field-test criteria (T022), out of scope for this unit of work; `n_threads = 4` is a reasonable default for the target "gama media/alta" hardware, not independently benchmarked here.

## Constitution Check

- No personal data in logs or error messages: `WhisperTranscriber`/`NativeWhisperEngine` do no logging of their own; transcribed text is only ever returned to the caller, never logged. Satisfied.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable — whisper.cpp runs fully on-device, no network calls.
- Database schema changes ship as reversible migrations: not applicable — no schema involved.
- Public API changes are additive within a major version: not applicable — `WhisperTranscriber`'s shape is already pinned by T012's test and is not changed here; `NativeWhisperEngine` is a brand-new internal type.
- Secrets come from the environment or the secret manager: not applicable — no secrets involved; whisper.cpp needs no account or API key.
- Tests run in CI before merge and a red build blocks the merge: `WhisperTranscriberTest.kt` becomes green with this feature's `WhisperTranscriber.kt`, satisfying the T021 CI gate for that file. `whisper_jni.cpp`/`NativeWhisperEngine.kt` have no automated test to gate on (see Testing tools above); their correctness is field-validated once a real NDK/CMake build is possible, and ultimately via T022's manual protocol.
- Accessibility: not applicable — no UI surface.

## Project Structure

```
app/src/main/kotlin/com/manuel/mvp/stt/
├── WhisperTranscriber.kt     # new: TranscriptionResult/TranscriptionOutcome/WhisperEngine/WhisperTranscriber (T012's contract)
└── NativeWhisperEngine.kt    # new: WhisperEngine impl, JNI external fun declarations

app/src/main/cpp/
└── whisper_jni.cpp           # new: JNI glue calling into the vendored whisper.cpp library

app/CMakeLists.txt            # modified: adds whisper_jni.cpp as a manuel_native source
```

No other files are created or modified except this plan/spec/tasks documentation and `docs/superpowers/specs/tasks.md` (marking T013 done). `app/src/test/kotlin/com/manuel/mvp/stt/WhisperTranscriberTest.kt` (T012) is read-only input to this feature and is not touched.

## Research

- **Real whisper.cpp API surface used** (confirmed by reading the vendored `third_party/whisper.cpp/include/whisper.h`, checked out via `git submodule update --init --depth 1 third_party/whisper.cpp` since it wasn't previously present in this working tree — T002 had registered the submodule but not fetched it): `whisper_context_default_params()`, `whisper_init_from_file_with_params(path, cparams)`, `whisper_full_default_params(WHISPER_SAMPLING_GREEDY)`, `whisper_full(ctx, params, pcmf32_data, n_samples)` (takes `const float *` samples, confirming the PCM16→float conversion requirement), `whisper_full_n_segments(ctx)`, `whisper_full_get_segment_text(ctx, i)`, `whisper_full_n_tokens(ctx, i)`, `whisper_full_get_token_p(ctx, i, j)`, `whisper_free(ctx)`.
- **`whisper_full_params` fields set explicitly**: `language = "es"`, `translate = false` (FR-005), `no_timestamps = true`/`print_timestamps = false`/`print_progress = false`/`print_realtime = false` (this bridge only needs concatenated segment text, not console output or per-token timestamps), `n_threads = 4` (a reasonable default, not benchmarked). All other fields are left at `whisper_full_default_params(WHISPER_SAMPLING_GREEDY)`'s defaults.
- **Confidence formula**: average `whisper_full_get_token_p` across every token of every segment — a single native-side loop (`Summarize()` helper in `whisper_jni.cpp`) accumulating a running sum and count, dividing at the end; `0.0f` when there are zero tokens (covers both "whisper_full failed" and "silence/empty transcription" cases uniformly, both correctly triggering `WhisperTranscriber`'s repeat-request path via the already-tested `>=` threshold comparison).
- **JNI result construction**: rather than two separate native calls (one for text, one for confidence — which would be racy/awkward, needing the C++ side to cache state between calls), `whisper_jni.cpp`'s `nativeTranscribe` constructs and returns a `com.manuel.mvp.stt.TranscriptionResult` object directly (`env->FindClass` + `env->GetMethodID(..., "<init>", "(Ljava/lang/String;F)V")` + `env->NewObject(...)`), keeping the native call atomic and stateless between invocations.
- **PCM16 → float conversion**: `sample / 32768.0f`, the standard full-scale PCM16 normalization whisper.cpp's own examples use (confirmed against `whisper.h`'s documented "RAW audio data in 32-bit floating point format" usage note) — matches [[manuel-mvp decision — 16kHz mono PCM16 end to end]]'s format convention from T011, so `AudioCaptureManager`'s `ShortArray` output needs no other transformation before this conversion.
- **Fail-fast on load failure (FR-006)**: `whisper_init_from_file_with_params` returns `NULL` on failure (per its doc comment); the JNI `nativeInit` returns that as a `0` `jlong` handle, and `NativeWhisperEngine`'s Kotlin `init { check(contextHandle != 0L) { ... } }` throws immediately rather than allowing a half-usable engine to be constructed — mirroring `WakeWordListener`'s general pattern of surfacing setup failures immediately rather than deferring them to first use.
- **No NDK/CMake toolchain in this sandbox**: confirmed (`cmake`/`ninja` both "command not found"), same limitation T002 already documented in its own `CMakeLists.txt` comments. `whisper_jni.cpp` is written and reviewed against the real header (SC-003) but not compiled; this is the C++-side equivalent of the Kotlin-side "standalone `kotlinc` compile" verification used elsewhere in this project when the full toolchain isn't available, just without an equivalent lightweight stand-in compiler for C++/JNI.
- **Not reusing `WhisperEngine` interface as `NativeWhisperEngine`'s parent name confusion**: `NativeWhisperEngine implements WhisperEngine` (the T012-pinned interface) — its own name is distinguishing it as *a* implementation, not *the* contract, consistent with how `KeywordPrefixParser` (a concrete pure function) is distinct from any interface it might satisfy.
