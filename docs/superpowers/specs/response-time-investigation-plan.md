# Plan: investigating remaining response-time issues

Context: after removing LLM generation from the MVP (see `ConversationPipeline`'s doc
comment) and fixing a hardcoded 4-thread limit in both `llama_jni.cpp` and
`whisper_jni.cpp`, a real on-device measurement (Samsung Galaxy S25+, whisper "tiny",
question "¿Qué es sumar?") gave:

```
capture=3188ms  transcribe=8499ms  search=1ms  total=11689ms
```

That's under the spec's SC-005 target (≤15s), but transcription still dominates the
turn and is worth shrinking further, both for headroom and because whisper "base"
(tried for better accuracy) came in at 28s transcription alone — 3x slower than tiny,
a bigger hit than expected. The items below are what's worth investigating next, in
priority order, with the evidence behind each.

## Result (2026-09-15): items 1+2 done, ~18x faster transcription

Measured on a second, slower phone (HiBreak, Android 14, MediaTek, `/proc/cpuinfo`
features include `asimddp`/`fphp`), same question ("¿Qué es sumar?"), same audio, same
whisper "tiny" model, A/B on the same day:

| Native build config | capture | transcribe | runs |
|---|---|---|---|
| `CMAKE_BUILD_TYPE=Debug` (the default AGP applied) | 3.7s | **26.1s, 27.1s** | 2 |
| `Release` + `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16` | 3.3–4.0s | **1.28s, 1.31s, 1.43s, 1.66s** | 4 |

Total turn with the optimized build: `capture=3653ms transcribe=1664ms search=1ms
total=5345ms`. The capture window (speech + 1s of trailing silence) is now the biggest
component, not transcription. Both flags are set in `app/build.gradle.kts`.

Two things learned along the way that the remaining items should account for:

- The 8.5s Samsung number above came from a much faster SoC than the HiBreak; on the
  HiBreak the *same* Debug build took 26s. Don't compare numbers across phones —
  re-measure on the same device when trying items 3-6.
- The HiBreak drops every `Log.d` from the app (`getprop log.tag` = `I`), so the
  `Timing:` line never appeared until `adb shell setprop log.tag.ConversationPipeline
  D` was run. The pipeline also now logs the two previously silent outcomes (no speech
  captured; transcript rejected for low confidence) with their timings.

Not yet re-measured after the fix: whisper "base" (28s before, on the Samsung Debug
build). With an 18x kernel speedup it could plausibly land at ~2-4s on the HiBreak,
which would make the accuracy tradeoff worth revisiting — see item 6.

## 1. Native libraries are built in Debug mode, not Release (DONE — measured 26s → 1.4s together with #2)

`app/.cxx/Debug/*/arm64-v8a/CMakeCache.txt` shows `CMAKE_BUILD_TYPE:STRING=Debug`.
`ggml`'s own `CMakeLists.txt` defaults to `Release` when no build type is set
(`third_party/llama.cpp/ggml/CMakeLists.txt:46-48`), but AGP explicitly forces `Debug`
for the `assembleDebug` variant, which overrides that fallback. whisper.cpp's and
llama.cpp's hot compute kernels (matmul, attention, quantized dot products) have been
running with **zero `-O2`/`-O3` optimization** this entire session.

- **Test**: add `arguments += "-DCMAKE_BUILD_TYPE=Release"` to the `externalNativeBuild
  { cmake { ... } }` block in `app/build.gradle.kts`, rebuild, re-time the same
  question.
- **Risk**: none functionally. Debug-APK-with-Release-native-libs is a completely
  standard combination. Slightly harder to debug native crashes (fewer symbols in the
  stack trace) — acceptable tradeoff, and can be reverted per-build if a native crash
  needs investigating.
- **Confidence**: high. This is the single most likely largest lever, and it's free.

## 2. No ARM CPU feature targeting (DONE — applied together with #1; not measured in isolation)

`GGML_CPU_ARM_ARCH` is empty and `GGML_NATIVE=OFF` in the same `CMakeCache.txt`.
`GGML_NATIVE=OFF` is *correct* for cross-compilation (it would otherwise try
`-march=native` for the host build machine, not the phone), but leaves ggml building
generic ARMv8-A kernels instead of targeting this phone's actual CPU features —
`dotprod`/`i8mm`/`fp16` arithmetic, all of which specifically speed up the quantized
(Q4_K_M / Q5_1 / Q8_0) matmul kernels this app depends on.

- **Test**: pass `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16` (verify the exact feature
  set against the real SoC in this Galaxy S25+ first) via the same `arguments +=` list,
  rebuild, re-time.
- **Risk**: could fail to build or run if the assumed feature set isn't actually
  supported by this chip — confirm the SoC's real ISA extensions before committing to a
  specific string. ARMv8-A baseline (today's behavior) is always a safe fallback.
- **Confidence**: high, but slightly less certain than #1 since the real speedup
  depends on how much of the quantized inference path actually hits these kernels.

## 3. Thread-count sweep (no evidence yet)

We forced `n_threads`/`n_threads_batch` to `std::thread::hardware_concurrency()` (8 on
this phone), assuming max-cores = fastest. Big.LITTLE ARM SoCs mix performance and
efficiency cores; pushing work onto all 8 (including the slow cores) can sometimes be
*slower* than using only the 4-6 performance cores, due to scheduling overhead and
thermal throttling under sustained load.

- **Test**: sweep `n_threads` across `{2, 4, 6, 8}` for whisper transcription
  specifically (it's the dominant cost) and measure which is actually fastest on this
  device, rather than assuming higher is always better.

## 4. Audio capture length / silence padding (no evidence yet)

Captured audio (~3-4s) includes silence governed by `CaptureWindowPolicy`'s
`noSpeechTimeoutMs`/`endOfSpeechSilenceMs`. Whisper has to process every sample it's
given; if there's meaningful trailing silence in the buffer, trimming it more
aggressively before transcription shaves real time off for no UX cost.

- **Test**: log the actual sample count fed to whisper vs. how long the speech itself
  actually was; tune `endOfSpeechSilenceMs` down if there's consistently a lot of
  trailing silence.

## 5. `whisper_full_params.audio_ctx` truncation (no evidence yet)

whisper.cpp exposes `audio_ctx` to cap how much encoder context gets processed,
trading a hard ceiling on max utterance length for faster encoding of short ones —
which matches this app's use case exactly (every instruction is one short sentence).
Not currently set (defaults to full context).

- **Test**: set `audio_ctx` to a value matching our real max expected utterance length
  and confirm no truncation on real questions during testing.

## 6. Quantized whisper models (no evidence yet, do after #1-#2)

Only f16 "tiny" and f16 "base" were tried. A quantized tiny (`ggml-tiny-q5_1.bin` /
`ggml-tiny-q8_0.bin`) might beat plain f16 tiny on memory-bandwidth-bound mobile
hardware even before the Release-build fix; a quantized *base* might land in a useful
middle ground between tiny's speed and base's accuracy once #1/#2 land.

- **Test**: re-run the tiny-vs-base speed/accuracy comparison with quantized variants
  of each, but only after #1 and #2 are in, since those change the baseline for every
  model.

## Not worth pursuing right now

- FTS5 content search: already ~1-3ms, negligible.
- LLM generation: removed entirely for the MVP (see `ConversationPipeline`'s doc
  comment); out of scope for this investigation.

## Suggested order

Items 1 and 2 are done and measured (see "Result" above): transcription went from the
dominant cost to ~1.4s. With the turn now at ~5.3s, ~3.7s of it is the capture window,
so if anything else is worth doing it's **item 4** (trailing-silence padding — the 1s
`endOfSpeechSilenceMs` is now most of the non-speech time in a turn), then **item 6**
(re-time whisper "base" on the optimized build, for accuracy — the "tiny" model
mishears often enough that several of the curated answers' `variantes` exist only to
absorb its errors). Items 3 and 5 are now second-order: at 1.4s there's little left to
win from thread tuning or `audio_ctx` truncation.
