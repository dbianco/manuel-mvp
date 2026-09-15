# Manuel

[![CI](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml/badge.svg)](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml)

**Manuel** is an offline, voice-first educational assistant for an Android phone, built as a proof of concept for rural schools in Córdoba, Argentina, that don't have reliable internet access. A child arms the assistant, says "Manuel, `<question>`," and gets a short, spoken answer grounded in preloaded lesson content — the whole pipeline (wake word, speech-to-text, retrieval, generation, and text-to-speech) runs entirely on-device.

> **Project status: MVP implementation complete, not yet run on a device.** Every task in [`docs/superpowers/specs/tasks.md`](docs/superpowers/specs/tasks.md) is implemented and committed, but this codebase has never had a real Android SDK/NDK build attempted against it, and it's missing three files it needs to actually run (see [What's missing before this runs](#whats-missing-before-this-runs) below).

## How it works

```
"Manuel" spoken  →  wake-word detection (openWakeWord, on-device ONNX)
                 →  microphone capture (bounded window, silence-aware)
                 →  speech-to-text (whisper.cpp, on-device)
                 →  keyword-prefix check ("Manuel, ..." required, else silently ignored)
                 →  content search (SQLite FTS5 lexical search over preloaded lessons)
                 →  prompt assembly (current question + retrieved fragments + last 5 exchanges)
                 →  answer generation (llama.cpp, Llama 3.2 3B Instruct, Q4_K_M, on-device)
                 →  spoken response (Android TextToSpeech, Spanish voice)
```

Two buttons are the only manual controls: **Escuchar** arms background wake-word listening; **Dejar de escuchar** disarms it, stops any capture in progress, and clears the conversation's session memory. Every functional requirement and the reasoning behind these design choices live in [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md).

## Why fully offline, and why these engines

Nothing in this app calls the network — no Wi-Fi, mobile data, or Bluetooth is required at any point, by design, for the classrooms this is meant to serve. That constraint drove every engine choice:

| Concern | Engine | Why |
|---|---|---|
| Wake word | [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) | No account/API key needed (unlike Porcupine, the original choice — see `spec.md`'s Clarifications for why it was swapped out) |
| Speech-to-text | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | Runs a `tiny`/`base` Whisper model on-device via JNI |
| Content retrieval | SQLite FTS5 (lexical search) | No vector database needed at this content scale |
| Answer generation | [llama.cpp](https://github.com/ggml-org/llama.cpp), Llama 3.2 3B Instruct (GGUF, Q4_K_M) | Small enough to run on a mid/high-end phone, via JNI |
| Text-to-speech | Android's built-in `TextToSpeech` | Offline once a Spanish voice is installed |

## Project structure

```
app/src/main/kotlin/com/manuel/mvp/
├── audio/      wake-word listening, mic capture, keyword-prefix parsing
├── stt/        whisper.cpp bridge + confidence-threshold decision
├── rag/        SQLite/FTS5 content database and search
├── session/    rolling 5-exchange conversation memory
├── llm/        prompt assembly + llama.cpp bridge
├── tts/        Android TextToSpeech wrapper
├── metrics/    local, privacy-preserving per-turn metrics
├── pipeline/   orchestrates all of the above into one conversational loop
└── ui/         the single Compose screen (state + two buttons)

app/src/main/cpp/        JNI glue for whisper.cpp and llama.cpp
app/src/test/kotlin/     JVM unit tests (one file per package above)
app/src/androidTest/     instrumented Compose UI tests
third_party/             llama.cpp and whisper.cpp, vendored as git submodules
docs/superpowers/specs/  the full spec/plan/tasks trail for every task (see below)
```

## Getting started

### Prerequisites

- Android Studio (or a standalone Android SDK + NDK) with `compileSdk = 37`, `minSdk = 29`
- JDK 17
- The NDK and CMake components (for the `llama.cpp`/`whisper.cpp` native build)

### Clone

This repo vendors `llama.cpp` and `whisper.cpp` as git submodules — clone with `--recursive`, or run `git submodule update --init` after a normal clone:

```sh
git clone --recursive git@github.com:dbianco/manuel-mvp.git
```

### What's missing before this runs

Three files aren't in this repository yet and must be provisioned on the target device before the app can function past compiling:

1. **`app/src/main/assets/wakeword/manuel.onnx`** — the trained "Manuel" wake-word model. Train it with openWakeWord's own Colab notebook/training pipeline; the two generic models it depends on (`melspectrogram.onnx`, `embedding_model.onnx`) are already vendored.
2. **A whisper.cpp GGML/GGUF model** at `<app internal storage>/models/ggml-tiny.bin`.
3. **A llama.cpp GGUF model** (Llama 3.2 3B Instruct, Q4_K_M) at `<app internal storage>/models/llama-3.2-3b-instruct-q4_k_m.gguf`.

`MainActivity` is written so a missing model surfaces as a visible error state rather than crashing the app — see its own doc comment for the exact paths it expects.

### Build and test

```sh
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android Lint
./gradlew assembleDebug       # full build (needs the NDK; slow the first time, builds llama.cpp/whisper.cpp)
```

CI (`.github/workflows/ci.yml`) runs the first two on every PR against `main` and on every push to `main`.

## Documentation

- [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md) — the functional requirements and acceptance scenarios (in Spanish, the project's working language)
- [`docs/superpowers/specs/plan.md`](docs/superpowers/specs/plan.md) — the technical plan and architecture decisions
- [`docs/superpowers/specs/tasks.md`](docs/superpowers/specs/tasks.md) — the task list (T001–T022), each with a note on what shipped and how it was verified
- `docs/superpowers/specs/t0NN-*-{spec,plan,tasks}.md` — a per-task spec/plan/tasks trail for every individual task, including the reasoning behind each design decision
- [`docs/superpowers/specs/manuel-mvp-test-protocol.md`](docs/superpowers/specs/manuel-mvp-test-protocol.md) — the manual field-test protocol (30 questions + 5 multi-turn dialogues) to run once a device and the model files above are available

## Development environment note

This codebase was built in a sandboxed environment with no Android SDK or NDK installed. Every JVM-only unit test was run for real; Android-framework code was compiled against Robolectric's `android-all` jar as a stand-in for `android.jar`; native JNI code was reviewed line-by-line against the real vendored headers but never compiled; and Jetpack Compose UI/test code was reviewed against the stable public API but never compiled at all (Compose artifacts need a real Gradle sync to resolve). In practice, this means: **the first real `./gradlew assembleDebug` in a proper Android dev environment should be treated as this project's first real build**, and any compiler errors it surfaces are expected findings, not a sign anything was done carelessly — see the individual task docs under `docs/superpowers/specs/` for exactly what was and wasn't verified at each step.
