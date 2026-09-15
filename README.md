# Manuel

[![CI](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml/badge.svg)](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml)

**Manuel** is an offline, voice-first educational assistant for an Android phone, built as a proof of concept for rural schools in Córdoba, Argentina, that don't have reliable internet access. A child arms the assistant, says "Manuel, `<question>`," and gets a short, spoken answer grounded in preloaded lesson content — the whole pipeline (wake word, speech-to-text, retrieval, generation, and text-to-speech) runs entirely on-device.

This project was built end to end — spec, plan, tasks, implementation, and verification — using [**SDD Orchestrator**](https://github.com/dbianco/sdd-orchestrator/), a spec-driven-development workflow for AI coding agents.

### Why "Manuel"?

The wake word is named after **Manuel Lucio Lucero** (1814–1878), a lawyer, university teacher, and later rector of the Universidad Nacional de Córdoba — nicknamed **"Manuel Lucero, the conversationalist."** Born in San Javier, Córdoba Province, he taught Latin and philosophy at the University of Córdoba until 1840, and was elected rector of the university in 1874, where he founded its faculties of medicine and exact sciences. A conversationalist from Córdoba felt like the right namesake for an assistant built to hold a spoken conversation with kids in Córdoba's rural schools.

> **Project status: builds, installs, and runs — one missing file away from a full working demo.** A real Android SDK/NDK build (`./gradlew assembleDebug`) succeeds, all 52 JVM unit tests and Android Lint pass for real, and the app has been installed and run on an emulator: it initializes its content database, loads a real whisper.cpp model and a real 3B-parameter llama.cpp model, and only stops at the one piece nobody can provision without custom training — the wake-word model. See [Verified on-device](#verified-on-device) below for exactly what was checked and how.

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
| Content retrieval | SQLite FTS5 (lexical search), via `androidx.sqlite:sqlite-bundled` | No vector database needed at this content scale; the bundled driver ships its own FTS5-capable SQLite, since stock Android's own SQLite doesn't have FTS5 compiled in (see [Verified on-device](#verified-on-device)) |
| Answer generation | [llama.cpp](https://github.com/ggml-org/llama.cpp), Llama 3.2 3B Instruct (GGUF, Q4_K_M) | Small enough to run on a mid/high-end phone, via JNI |
| Text-to-speech | Android's built-in `TextToSpeech` | Offline once a Spanish voice is installed |

## Verified on-device

A local Android SDK/NDK/emulator setup was used to actually build, install, and run this app for the first time (previously it had only been reviewed against real headers/APIs in a sandbox with no SDK — see [Development environment note](#development-environment-note)). Results:

- **`./gradlew assembleDebug` succeeds**, compiling `llama.cpp` and `whisper.cpp` natively for both `arm64-v8a` and `x86_64`. The very first attempt caught a real bug — a const-correctness mismatch in `llama_jni.cpp`'s call to `llama_batch_get_one` — fixed in one line; `whisper_jni.cpp` compiled clean on the first try.
- **`./gradlew testDebugUnitTest` passes all 52 tests**, and **`./gradlew lintDebug` is clean**, both for real via Gradle (not the standalone-compiler workaround used during initial development). Lint did catch one real `MissingPermission` finding — `AudioCaptureManager`'s microphone access is permission-checked several calls away, through an async `Flow`, in a shape lint's static analysis can't trace — documented with `@RequiresPermission` and suppressed at the two points that are genuinely unreachable without it.
- **The app installs and launches on a real Android emulator** (API 37, `google_apis` arm64 image) and renders its one screen correctly, with the two buttons in the right enabled state.
- **A real, on-device bug was found and fixed**: stock Android's own bundled SQLite has no FTS5 module compiled in (confirmed on-device, not just the already-known Robolectric test limitation) — `ContentDatabase`/`ContentDao` were migrated from `androidx.sqlite:sqlite-framework` (wraps the OS's SQLite) to `androidx.sqlite:sqlite-bundled` (ships its own FTS5-capable SQLite), Google's own official answer to this exact problem.
- **whisper.cpp and llama.cpp both loaded real models successfully**: a `ggml-tiny.bin` whisper model and the full Llama 3.2 3B Instruct Q4_K_M GGUF (~2GB) were pushed into the app's private storage; `dumpsys meminfo` showed ~1.9GB memory-mapped, matching the Llama file size almost exactly, confirming `llama.cpp` actually mapped the whole model into memory.
- **The only remaining blocker is the one already documented**: `wakeword/manuel.onnx` doesn't exist (it needs custom training, see below), so arming the assistant surfaces `AssistantState.Error("Failed to load model: wakeword/manuel.onnx")` — exactly the graceful-failure behavior `MainActivity` was designed for, not a crash.

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

- Android SDK with `compileSdk = 37`, `minSdk = 29`, plus the NDK and CMake `3.22.1` (for the `llama.cpp`/`whisper.cpp` native build)
- JDK 17+

A minimal, tested way to get all of this via Homebrew on macOS:

```sh
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;37.0.0" \
  "ndk;27.2.12479018" "cmake;3.22.1" "emulator" \
  "system-images;android-37.0;google_apis;arm64-v8a"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Clone

This repo vendors `llama.cpp` and `whisper.cpp` as git submodules — clone with `--recursive`, or run `git submodule update --init` after a normal clone:

```sh
git clone --recursive git@github.com:dbianco/manuel-mvp.git
```

### Build, test, and run

```sh
./gradlew testDebugUnitTest lintDebug   # JVM unit tests + Android Lint — both pass
./gradlew assembleDebug                 # full build (slow the first time: compiles llama.cpp/whisper.cpp)
```

To actually run it, create and boot an emulator, then install:

```sh
avdmanager create avd -n manuel_test -k "system-images;android-37.0;google_apis;arm64-v8a" -d pixel_6
"$ANDROID_HOME/emulator/emulator" -avd manuel_test &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.manuel.mvp/.MainActivity
```

CI (`.github/workflows/ci.yml`) runs the unit tests and lint on every PR against `main` and on every push to `main`.

### What's missing before it fully works

One file is the real blocker; two others are needed for anything past the content database to run, but were verified working during development (see [Verified on-device](#verified-on-device)) and are easy to obtain:

1. **`app/src/main/assets/wakeword/manuel.onnx`** — the trained "Manuel" wake-word model. This is the actual blocker: train it with openWakeWord's own Colab notebook/training pipeline; the two generic models it depends on (`melspectrogram.onnx`, `embedding_model.onnx`) are already vendored.
2. **A whisper.cpp GGML model** at `<app internal storage>/models/ggml-tiny.bin` — freely downloadable, e.g. from `ggerganov/whisper.cpp` on Hugging Face.
3. **A llama.cpp GGUF model** (Llama 3.2 3B Instruct, Q4_K_M) at `<app internal storage>/models/llama-3.2-3b-instruct-q4_k_m.gguf` — freely downloadable community re-uploads exist (e.g. `unsloth/Llama-3.2-3B-Instruct-GGUF` on Hugging Face).

To push models into the app's private storage on an emulator (no root needed beyond `adb root`, which emulator builds allow):

```sh
adb root
adb push ggml-tiny.bin /data/user/0/com.manuel.mvp/files/models/ggml-tiny.bin
adb push llama-3.2-3b-instruct-q4_k_m.gguf /data/user/0/com.manuel.mvp/files/models/llama-3.2-3b-instruct-q4_k_m.gguf
```

(The app must have been launched at least once already, so Android has created its `files/` directory.)

`MainActivity` is written so a missing model surfaces as a visible error state rather than crashing the app — see its own doc comment for the exact paths it expects.

## Documentation

- [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md) — the functional requirements and acceptance scenarios (in Spanish, the project's working language)
- [`docs/superpowers/specs/plan.md`](docs/superpowers/specs/plan.md) — the technical plan and architecture decisions
- [`docs/superpowers/specs/tasks.md`](docs/superpowers/specs/tasks.md) — the task list (T001–T022), each with a note on what shipped and how it was verified
- `docs/superpowers/specs/t0NN-*-{spec,plan,tasks}.md` — a per-task spec/plan/tasks trail for every individual task, including the reasoning behind each design decision
- [`docs/superpowers/specs/manuel-mvp-test-protocol.md`](docs/superpowers/specs/manuel-mvp-test-protocol.md) — the manual field-test protocol (30 questions + 5 multi-turn dialogues) to run once a device and the `manuel.onnx` wake-word model are available

## Development environment note

The bulk of this codebase (T001–T022, the full spec-driven task list) was built in a sandboxed environment with no Android SDK or NDK installed. Every JVM-only unit test was run for real there; Android-framework code was compiled against Robolectric's `android-all` jar as a stand-in for `android.jar`; native JNI code was reviewed line-by-line against the real vendored headers but never compiled; and Jetpack Compose UI/test code was reviewed against the stable public API but never compiled at all.

A later pass installed a real Android SDK/NDK/emulator and ran the actual build for the first time (see [Verified on-device](#verified-on-device)) — it found and fixed two real bugs (the `llama_jni.cpp` const-correctness issue and the on-device FTS5 gap) that no amount of careful review could have caught without a real compiler and a real device. That's the intended takeaway: the sandboxed-development verification tiers documented throughout `docs/superpowers/specs/` were honest about their limits, and this pass is the confirmation that following up with a real environment is exactly when those limits matter.
