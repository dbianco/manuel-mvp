# Manuel

[![CI](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml/badge.svg)](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml)

**Manuel** is an offline, voice-first educational assistant for an Android phone, built as a proof of concept for rural schools in Córdoba, Argentina, that don't have reliable internet access. A child arms the assistant, says "Manuel, `<question>`," and gets a short, spoken answer grounded in preloaded lesson content — the whole pipeline (wake word, speech-to-text, retrieval, generation, and text-to-speech) runs entirely on-device.

This project was built end to end — spec, plan, tasks, implementation, and verification — using [**SDD Orchestrator**](https://github.com/dbianco/sdd-orchestrator/), a spec-driven-development workflow for AI coding agents.

### Why "Manuel"?

The wake word is named after **Manuel Lucio Lucero** (1814–1878), a lawyer, university teacher, and later rector of the Universidad Nacional de Córdoba — nicknamed **"Manuel Lucero, the conversationalist."** Born in San Javier, Córdoba Province, he taught Latin and philosophy at the University of Córdoba until 1840, and was elected rector of the university in 1874, where he founded its faculties of medicine and exact sciences. A conversationalist from Córdoba felt like the right namesake for an assistant built to hold a spoken conversation with kids in Córdoba's rural schools.

> **Project status: the full pipeline works, verified live on a real phone.** Say "Manuel, ¿cuántos lados tiene un triángulo?" (or tap **Hablar ahora** to skip the wake word) and the assistant transcribes the question, searches its preloaded lesson content, generates an answer with an on-device LLM, and speaks it back — confirmed working end to end on a real Samsung Galaxy S25+, not just a build. Getting here took three real bugs findable only by testing on real hardware; see [Verified on-device](#verified-on-device) below.

## How it works

```
"Manuel" spoken, or "Hablar ahora" tapped
                 →  wake-word detection (openWakeWord, on-device ONNX) — skipped if manually triggered
                 →  microphone capture (bounded window, silence-aware)
                 →  speech-to-text (whisper.cpp, on-device)
                 →  content search (SQLite FTS5 lexical search over preloaded lessons)
                 →  prompt assembly (current question + retrieved fragments + last 5 exchanges)
                 →  answer generation (llama.cpp, Qwen2.5-0.5B-Instruct, Q4_K_M, on-device)
                 →  spoken response (Android TextToSpeech, Spanish voice)
```

Three buttons are the manual controls: **Escuchar** arms background wake-word listening; **Dejar de escuchar** disarms it, stops any capture in progress, and clears the conversation's session memory; **Hablar ahora** (enabled only while armed) starts a turn immediately, without needing a successful acoustic wake-word detection first — added after real-device testing showed the trained wake-word model misses real speech often enough to be worth a manual fallback. Every functional requirement and the reasoning behind these design choices live in [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md).

## Why fully offline, and why these engines

Nothing in this app calls the network — no Wi-Fi, mobile data, or Bluetooth is required at any point, by design, for the classrooms this is meant to serve. That constraint drove every engine choice:

| Concern | Engine | Why |
|---|---|---|
| Wake word | [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) | No account/API key needed (unlike Porcupine, the original choice — see `spec.md`'s Clarifications for why it was swapped out) |
| Speech-to-text | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | Runs a `tiny`/`base` Whisper model on-device via JNI |
| Content retrieval | SQLite FTS5 (lexical search), via `androidx.sqlite:sqlite-bundled` | No vector database needed at this content scale; the bundled driver ships its own FTS5-capable SQLite, since stock Android's own SQLite doesn't have FTS5 compiled in (see [Verified on-device](#verified-on-device)) |
| Answer generation | [llama.cpp](https://github.com/ggml-org/llama.cpp), Qwen2.5-0.5B-Instruct (GGUF, Q4_K_M) | Downsized from Llama 3.2 3B → 1B → Qwen2.5-0.5B after real-device timing showed the bigger models were impractically slow on phone CPU with no GPU delegate; `PromptBuilder`'s plain-text output is wrapped in Qwen's ChatML turn markers before generation |
| Text-to-speech | Android's built-in `TextToSpeech` | Offline once a Spanish voice is installed |

## Verified on-device

Two real-hardware passes so far: first an Android emulator (build/install sanity), then a real phone (the actual conversational loop, end to end, including the wake-word model this repo now bundles).

### Real phone (Samsung Galaxy S25+, Android 16)

- **A full conversation turn works, live, start to finish**: arming (or tapping **Hablar ahora**), wake-word detection, microphone capture, whisper.cpp transcription, FTS5 content search, on-device LLM generation, and spoken Android TTS output — verified with real questions, e.g. "¿Cuántos lados tienen triángulo?" answered correctly and spoken aloud: *"Un triángulo es un tipo de figura geométrica que tiene tres lados."*
- **Found and fixed the real bug blocking every previous attempt**: `WakeWordListener.resolveInstruction()` required the whisper transcript to start with the literal word "manuel", but `AudioCaptureManager` only starts recording *after* the acoustic wake-word detection fires — a fresh `AudioRecord` session that, by construction, can never contain the word "manuel" (that utterance was already consumed by the wake-word engine's own separate recorder). Every correctly-transcribed instruction was being silently discarded no matter how accurate the transcription was — invisible without a live microphone test.
- **Found and fixed a second real packaging bug**: openWakeWord's Android library hardcodes the asset paths of its two shared feature-extraction models (`melspectrogram.onnx`, `embedding_model.onnx`) with no directory prefix, but they'd been placed under `assets/wakeword/` alongside the classifier model. Only the classifier model's path is actually configurable — moved both files to the asset root to fix it.
- **Trained the wake-word model** via openWakeWord's automatic-training pipeline (target phrase "manuel", synthetic English-TTS voices) — see `manuel_model_training.ipynb` at the repo root for the exact, debugged Colab setup. It's real but imperfect (synthetic-voice training data, reduced sample count): detects reliably most of the time at a lowered 0.35 threshold, which is why **Hablar ahora** exists as a manual fallback.
- **Downsized the LLM twice**: Llama 3.2 3B → 1B → **Qwen2.5-0.5B-Instruct**, after on-device timing showed the bigger models were impractically slow on phone CPU with no GPU delegate (multi-minute generations at 500%+ CPU across cores for the 3B model).
- **Found and fixed two LLM-generation bugs, both only visible by listening to real output**: prompts were never wrapped in Qwen's ChatML turn markers, so the model never learned when to stop generating — it ran the full token budget and degenerated into repeating its own instructions after already answering correctly; and greedy decoding alone has no randomness to escape a repeated phrase once it starts one (fixed with a repetition-penalty sampler stage in `llama_jni.cpp`). Also found `llama_context_default_params()` hardcodes 4 threads regardless of hardware — this phone has 8 cores, now actually used.

### Emulator pass (earlier)

- `./gradlew assembleDebug` / `testDebugUnitTest` (52 tests) / `lintDebug` all pass for real via Gradle — the first real build after T001–T022's sandboxed development, which caught a `llama_jni.cpp` const-correctness bug, one real Lint `MissingPermission` finding, and stock Android's own SQLite having no FTS5 module compiled in (migrated `ContentDatabase`/`ContentDao` to `androidx.sqlite:sqlite-bundled`, which ships its own FTS5-capable SQLite).

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
└── ui/         the single Compose screen (state + three buttons)

app/src/main/cpp/            JNI glue for whisper.cpp and llama.cpp
app/src/main/assets/         manuel.onnx + openWakeWord's shared feature-extraction models
app/src/test/kotlin/         JVM unit tests (one file per package above)
app/src/androidTest/         instrumented Compose UI tests
third_party/                 llama.cpp and whisper.cpp, vendored as git submodules
docs/superpowers/specs/      the full spec/plan/tasks trail for every task (see below)
manuel_model_training.ipynb  the debugged Colab notebook used to train manuel.onnx
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

### What's needed before it runs on a fresh checkout

The wake-word model is already committed and bundled into the APK (`app/src/main/assets/wakeword/manuel.onnx`, trained via `manuel_model_training.ipynb`) — nothing to do there. Two much larger models can't ship in git/the APK and need to be pushed into the app's private storage after install:

1. **A whisper.cpp GGML model** at `<app internal storage>/models/ggml-tiny.bin` — freely downloadable, e.g. from `ggerganov/whisper.cpp` on Hugging Face.
2. **A llama.cpp GGUF model** (Qwen2.5-0.5B-Instruct, Q4_K_M) at `<app internal storage>/models/qwen2.5-0.5b-instruct-q4_k_m.gguf` — freely downloadable, e.g. from `Qwen/Qwen2.5-0.5B-Instruct-GGUF` on Hugging Face.

On an emulator (root available via `adb root`):

```sh
adb root
adb push ggml-tiny.bin /data/user/0/com.manuel.mvp/files/models/ggml-tiny.bin
adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /data/user/0/com.manuel.mvp/files/models/qwen2.5-0.5b-instruct-q4_k_m.gguf
```

On a real, non-rooted device, push to a world-readable temp location first and copy in with `run-as` (the app must have been launched at least once already, so Android has created its `files/` directory):

```sh
adb push ggml-tiny.bin /data/local/tmp/ggml-tiny.bin
adb shell run-as com.manuel.mvp mkdir -p files/models
adb shell "run-as com.manuel.mvp sh -c 'cat /data/local/tmp/ggml-tiny.bin > files/models/ggml-tiny.bin'"
# repeat for the .gguf file, then: adb shell rm /data/local/tmp/ggml-tiny.bin
```

`MainActivity` is written so a missing model surfaces as a visible error state rather than crashing the app — see its own doc comment for the exact paths it expects.

## Documentation

- [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md) — the functional requirements and acceptance scenarios (in Spanish, the project's working language)
- [`docs/superpowers/specs/plan.md`](docs/superpowers/specs/plan.md) — the technical plan and architecture decisions
- [`docs/superpowers/specs/tasks.md`](docs/superpowers/specs/tasks.md) — the task list (T001–T022), each with a note on what shipped and how it was verified
- `docs/superpowers/specs/t0NN-*-{spec,plan,tasks}.md` — a per-task spec/plan/tasks trail for every individual task, including the reasoning behind each design decision
- [`docs/superpowers/specs/manuel-mvp-test-protocol.md`](docs/superpowers/specs/manuel-mvp-test-protocol.md) — the manual field-test protocol (30 questions + 5 multi-turn dialogues) to run on a real device
- [`manuel_model_training.ipynb`](manuel_model_training.ipynb) — the customized, debugged copy of openWakeWord's automatic-training Colab notebook used to train `manuel.onnx`; every environment/dependency fix found while actually running it on current Colab is baked in as real cells, so retraining with a different wake phrase doesn't mean rediscovering all of them

## Development environment note

The bulk of this codebase (T001–T022, the full spec-driven task list) was built in a sandboxed environment with no Android SDK or NDK installed. Every JVM-only unit test was run for real there; Android-framework code was compiled against Robolectric's `android-all` jar as a stand-in for `android.jar`; native JNI code was reviewed line-by-line against the real vendored headers but never compiled; and Jetpack Compose UI/test code was reviewed against the stable public API but never compiled at all.

A later pass installed a real Android SDK/NDK/emulator and ran the actual build for the first time (see [Verified on-device](#verified-on-device)) — it found and fixed two real bugs (the `llama_jni.cpp` const-correctness issue and the on-device FTS5 gap) that no amount of careful review could have caught without a real compiler and a real device.

A third pass, once a trained `manuel.onnx` existed, moved to a real phone and ran the actual conversational loop for the first time — and found the bugs that mattered most: a wake-word/capture design mismatch that had been silently discarding every instruction since the pipeline was first wired up, an asset-path bug specific to how the wake-word library resolves its shared models, and two LLM-generation bugs (missing chat-template formatting, no repetition penalty) that only showed up by listening to real output. None of these were visible from code review, from the emulator pass, or from any unit test — they required a live microphone, a real voice, and patience. That's the throughline across all three passes: the sandboxed-development verification tiers documented throughout `docs/superpowers/specs/` were honest about their limits, and each successive real-environment pass is confirmation of exactly where those limits were.
