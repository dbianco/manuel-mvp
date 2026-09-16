# Manuel

[![CI](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml/badge.svg)](https://github.com/dbianco/manuel-mvp/actions/workflows/ci.yml)

**Manuel** is an offline, voice-first educational assistant for an Android phone, built as a proof of concept for rural schools in Córdoba, Argentina, that don't have reliable internet access. A child arms the assistant, says "Anita, `<question>`," and gets a short, spoken answer grounded in preloaded lesson content — the whole pipeline (wake word, speech-to-text, content retrieval, and text-to-speech) runs entirely on-device.

This project was built end to end — spec, plan, tasks, implementation, and verification — using [**SDD Orchestrator**](https://github.com/dbianco/sdd-orchestrator/), a spec-driven-development workflow for AI coding agents.

### Why "Manuel"?

The wake word is named after **Manuel Lucio Lucero** (1814–1878), a lawyer, university teacher, and later rector of the Universidad Nacional de Córdoba — nicknamed **"Manuel Lucero, the conversationalist."** Born in San Javier, Córdoba Province, he taught Latin and philosophy at the University of Córdoba until 1840, and was elected rector of the university in 1874, where he founded its faculties of medicine and exact sciences. A conversationalist from Córdoba felt like the right namesake for an assistant built to hold a spoken conversation with kids in Córdoba's rural schools.

The *spoken* wake word, though, is **"Anita"**, not "Manuel". openWakeWord's training pipeline generates its examples with an English TTS voice, so the model learns the English rendering of the phrase — "Manuel" came out as "man-WELL", not what a Spanish-speaking child says, and the first model missed real speech often enough to need a manual button. "Anita" is pronounced the same in both languages (ah-NEE-tah), has three syllables (openWakeWord detects longer phrases more reliably than short ones), and isn't a word that comes up in classroom speech, so it won't trigger by accident.

> **Project status: the full pipeline works, verified live on two real phones, at about 5 seconds per turn.** Say "Anita, ¿qué es sumar?" (or tap **Hablar ahora** to skip the wake word) and the assistant transcribes the question, looks it up in a curated set of 137 spoken-friendly answers (falling back to the raw lesson content, then to a fixed "no tengo información" line), and speaks the answer back. The lookup takes ~1 ms; transcription, which used to dominate the turn, dropped from 26 s to 1.4 s on the same phone once the native libraries were built with optimization on — see [Verified on-device](#verified-on-device). An earlier version generated answers with an on-device LLM (Llama 3.2 3B → 1B → Qwen2.5-0.5B, each downsized after real-device timing); even the smallest was 45-160+ seconds per turn and prone to rambling, so the MVP now speaks matched lesson content directly instead — see [Verified on-device](#verified-on-device) below for the real bugs (four of them) that testing on real hardware found along the way.

## How it works

```
"Anita" spoken, or "Hablar ahora" tapped
                 →  wake-word detection (openWakeWord, on-device ONNX) — skipped if manually triggered
                 →  microphone capture (bounded window, silence-aware)
                 →  speech-to-text (whisper.cpp, on-device)
                 →  answer lookup (SQLite FTS5, OR-matched content words, best match wins):
                      1. curated Q&A  (content/preguntas_respuestas.json — 137 questions, each with
                         several phrasings, answered in short spoken-friendly Spanish)
                      2. lesson fragment (content/matematica_lecciones.json — the raw lesson text)
                      3. fixed "No tengo información suficiente..." line if nothing matches
                 →  spoken response (Android TextToSpeech)
```

Measured on a real phone (HiBreak, Android 14, whisper "tiny", "¿Qué es sumar?"): `capture≈3.7s` (the speech itself plus 1 s of trailing silence) + `transcribe≈1.3–1.7s` + `search≈1ms` ≈ **5.3 s per turn**, well under the spec's 15 s target (SC-005).

An on-device LLM (llama.cpp) generated freeform answers in an earlier version instead of the last two steps — see [Verified on-device](#verified-on-device) for why that was dropped for the MVP. `llm/PromptBuilder`/`LlamaEngine` and `app/src/main/cpp/llama_jni.cpp` are still in the codebase, just not wired into `ConversationPipeline` anymore.

Three buttons are the manual controls: **Escuchar** arms background wake-word listening; **Dejar de escuchar** disarms it, stops any capture in progress, and clears the conversation's session memory; **Hablar ahora** (enabled only while armed) starts a turn immediately, without needing a successful acoustic wake-word detection first — added after real-device testing showed the trained wake-word model misses real speech often enough to be worth a manual fallback. Every functional requirement and the reasoning behind these design choices live in [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md).

## Why fully offline, and why these engines

Nothing in this app calls the network — no Wi-Fi, mobile data, or Bluetooth is required at any point, by design, for the classrooms this is meant to serve. That constraint drove every engine choice:

| Concern | Engine | Why |
|---|---|---|
| Wake word | [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) | No account/API key needed (unlike Porcupine, the original choice — see `spec.md`'s Clarifications for why it was swapped out) |
| Speech-to-text | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | Runs a `tiny`/`base` Whisper model on-device via JNI |
| Content retrieval + answer | SQLite FTS5 (lexical search), via `androidx.sqlite:sqlite-bundled` | No vector database needed at this content scale; the bundled driver ships its own FTS5-capable SQLite, since stock Android's own SQLite doesn't have FTS5 compiled in (see [Verified on-device](#verified-on-device)). The spoken answer is a curated entry from `preguntas_respuestas.json` (137 questions × several phrasings each, including the digit forms and b/v confusions whisper actually produces, answered in voseo with operations spelled out in words so TTS reads them well) or, failing that, the best-matching lesson fragment's own text — no generation step. 30 entries come from the inicial/primario lessons; 99 from [`docs/content/preguntas-matematica-3-4-grado.md`](docs/content/preguntas-matematica-3-4-grado.md), a 110-question 3rd/4th-grade guide (numeration, the four operations, fractions, decimals, plane figures, solids, measurement) whose answers were rewritten for speech; 8 more are natural follow-up questions (ángulos, aristas, poliedros, cuadrilátero...) grounded in terms the guide's own answers already used — each entry cites its source question. FTS5's bm25 shortlist is then re-ranked in Kotlin (`AnswerSearcher`): an entry whose own phrasing has exactly the question's content words wins (so "¿qué es sumar?" reaches the definition, not whichever entry repeats "sumar" most), and anything else needs at least two content words in common — one shared word ("hora", "sistema") is not the same question. `CannedAnswersTest` runs every entry's own question, the test protocol's questions, and paraphrases of the new set through the real FTS5 engine and checks each lands on the intended answer, and that out-of-scope/ambiguous ones land on nothing |
| Text-to-speech | Android's built-in `TextToSpeech` | Offline once a Spanish voice is installed |

[llama.cpp](https://github.com/ggml-org/llama.cpp) generated freeform answers in an earlier version (Llama 3.2 3B → 1B → Qwen2.5-0.5B-Instruct, Q4_K_M, each downsized after real-device timing showed the bigger ones were impractically slow on phone CPU with no GPU delegate) — dropped for the MVP in favor of the row above; the code (`llm/`, `app/src/main/cpp/llama_jni.cpp`) is still in the repo, just unused.

## Verified on-device

Three real-hardware passes so far: first an Android emulator (build/install sanity), then a Samsung phone (the actual conversational loop, end to end, including the wake-word model this repo now bundles), then a second, slower phone for response-time work.

### Second phone (HiBreak, Android 14, MediaTek) — response time

- **The native libraries had been built with no optimization at all, the whole time.** AGP maps the Gradle `debug` variant to `CMAKE_BUILD_TYPE=Debug`, which overrides ggml's own Release-by-default fallback, so whisper.cpp's compute kernels ran at `-O0`. Measured on this phone with the same question and the same audio: **transcription 26–27 s with the Debug native build vs. 1.3–1.7 s with `-DCMAKE_BUILD_TYPE=Release` plus `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`** (both now set in `app/build.gradle.kts`; the ISA string was checked against this SoC's `/proc/cpuinfo` features first). A debug APK with release-optimized native libs is a normal combination. Full write-up and the remaining ideas in [`docs/superpowers/specs/response-time-investigation-plan.md`](docs/superpowers/specs/response-time-investigation-plan.md).
- **This device silently drops every `Log.d` from the app** (`getprop log.tag` is `I` system-wide, so liblog discards debug-level lines before they reach logcat — while the wake-word library's `Log.d` lines happen to get through). Symptoms looked like "the turn ran but logged nothing". Fix, per tag, until reboot: `adb shell setprop log.tag.ConversationPipeline D` (same for `MainActivity`).
- The two paths that used to be invisible on-device — no speech captured, and a transcript rejected for low confidence — now log a line each with their timings, so a turn that "does nothing" can be told apart from a crash.

### Real phone (Samsung Galaxy S25+, Android 16)

- **A full conversation turn works, live, start to finish, and instantly**: arming (or tapping **Hablar ahora**), wake-word detection, microphone capture, whisper.cpp transcription, FTS5 content search, and spoken Android TTS output — verified with real questions, e.g. "¿Cuántos lados tienen un triángulo?" answered correctly and spoken aloud: *"El triángulo es una figura con 3 lados y 3 esquinas (vértices). El techo de una casa a veces tiene forma de triángulo."*, with transcription and response landing in the same millisecond.
- **Found and fixed the real bug blocking every previous attempt**: `WakeWordListener.resolveInstruction()` required the whisper transcript to start with the literal word "manuel", but `AudioCaptureManager` only starts recording *after* the acoustic wake-word detection fires — a fresh `AudioRecord` session that, by construction, can never contain the word "manuel" (that utterance was already consumed by the wake-word engine's own separate recorder). Every correctly-transcribed instruction was being silently discarded no matter how accurate the transcription was — invisible without a live microphone test.
- **Found and fixed a second real packaging bug**: openWakeWord's Android library hardcodes the asset paths of its two shared feature-extraction models (`melspectrogram.onnx`, `embedding_model.onnx`) with no directory prefix, but they'd been placed under `assets/wakeword/` alongside the classifier model. Only the classifier model's path is actually configurable — moved both files to the asset root to fix it.
- **Trained the wake-word model** via openWakeWord's automatic-training pipeline (target phrase "manuel" at the time, synthetic English-TTS voices) — see `anita_model_training.ipynb` at the repo root for the exact, debugged Colab setup. It was real but imperfect (synthetic *English* voices saying "man-WELL", reduced sample count): detected reliably most of the time at a lowered 0.35 threshold, which is why **Hablar ahora** exists as a manual fallback. That mismatch is why the wake word was changed to "Anita" (see [Why "Manuel"?](#why-manuel)) — the notebook is already set up to train it.
- **Downsized the LLM twice, then dropped it entirely for the MVP**: Llama 3.2 3B → 1B → Qwen2.5-0.5B-Instruct, after on-device timing showed the bigger models were impractically slow on phone CPU with no GPU delegate (multi-minute generations at 500%+ CPU across cores for the 3B model) — even the smallest was still 45-160+ seconds per turn. Along the way, found and fixed two real LLM-generation bugs only visible by listening to real output: prompts were never wrapped in Qwen's ChatML turn markers, so the model never learned when to stop generating and degenerated into repeating its own instructions after already answering correctly; and greedy decoding alone has no randomness to escape a repeated phrase once it starts one (added a repetition-penalty sampler stage). Also found `llama_context_default_params()` hardcodes 4 threads regardless of hardware. None of it was fast enough, so `ConversationPipeline` now speaks the matched lesson fragment directly instead — see the row above.
- **Found and fixed a query-matching bug that broke correctly-transcribed, on-topic questions**: `FragmentSearcher` ANDed every query word together, so "¿Cuántos lados **tienen** un triángulo?" failed to match content phrased "el triángulo **tiene** 3 lados" — a single verb-conjugation mismatch was enough to fail the whole match. Switched to OR-of-content-words (bm25-ranked) with a Spanish stopword filter, which fixed it without needing stemming.
- **Found and fixed a native crash in whisper.cpp on the second transcription of a session**: reusing the same implicit `whisper_context` state across two calls with different audio lengths triggered a `ggml_abort` inside `ggml_backend_sched_alloc_graph`, killing the app. Fixed by giving each transcription its own `whisper_state` (`whisper_init_state`/`whisper_full_with_state`/`whisper_free_state`) instead of the shared one `whisper_full()` uses implicitly.

### Emulator pass (earlier)

- `./gradlew assembleDebug` / `testDebugUnitTest` (64 tests today) / `lintDebug` all pass for real via Gradle — the first real build after T001–T022's sandboxed development, which caught a `llama_jni.cpp` const-correctness bug, one real Lint `MissingPermission` finding, and stock Android's own SQLite having no FTS5 module compiled in (migrated `ContentDatabase`/`ContentDao` to `androidx.sqlite:sqlite-bundled`, which ships its own FTS5-capable SQLite).

## Project structure

```
app/src/main/kotlin/com/manuel/mvp/
├── audio/      wake-word listening, mic capture, keyword-prefix parsing
├── stt/        whisper.cpp bridge + confidence-threshold decision
├── rag/        SQLite/FTS5 content database and search
├── session/    rolling 5-exchange conversation memory
├── llm/        prompt assembly + llama.cpp bridge (unused for the MVP, see "How it works")
├── tts/        Android TextToSpeech wrapper
├── metrics/    local, privacy-preserving per-turn metrics
├── pipeline/   orchestrates all of the above into one conversational loop
└── ui/         the single Compose screen (state + three buttons)

app/src/main/cpp/            JNI glue for whisper.cpp and llama.cpp
app/src/main/assets/         wakeword/anita.onnx + openWakeWord's shared feature-extraction models
app/src/test/kotlin/         JVM unit tests (one file per package above)
app/src/androidTest/         instrumented Compose UI tests
third_party/                 llama.cpp and whisper.cpp, vendored as git submodules
docs/superpowers/specs/      the full spec/plan/tasks trail for every task (see below)
anita_model_training.ipynb  the debugged Colab notebook that trains the wake-word model (anita.onnx)
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

The wake-word model is committed and bundled into the APK (`app/src/main/assets/wakeword/anita.onnx`, trained via `anita_model_training.ipynb`) — nothing to do there. To retrain it (or change the wake word): open the notebook in Google Colab (GPU runtime), edit `config["target_phrase"]` in the "Define Training Configuration" cell, run every cell top to bottom (~1 h on a free T4), download `my_custom_model/<phrase>.onnx`, drop it at `app/src/main/assets/wakeword/`, and point `WakeWordListener.DEFAULT_MODEL_ASSET_PATH`/`KEYWORD_MODEL_NAME` at it. Since the LLM was dropped for the MVP (see [Verified on-device](#verified-on-device)), the only model that can't ship in git/the APK and needs to be pushed into the app's private storage after install is:

- **A whisper.cpp GGML model** at `<app internal storage>/models/ggml-tiny.bin` — freely downloadable, e.g. from `ggerganov/whisper.cpp` on Hugging Face.

On an emulator (root available via `adb root`):

```sh
adb root
adb push ggml-tiny.bin /data/user/0/com.manuel.mvp/files/models/ggml-tiny.bin
```

On a real, non-rooted device, push to a world-readable temp location first and copy in with `run-as` (the app must have been launched at least once already, so Android has created its `files/` directory):

```sh
adb push ggml-tiny.bin /data/local/tmp/ggml-tiny.bin
adb shell run-as com.manuel.mvp mkdir -p files/models
adb shell "run-as com.manuel.mvp sh -c 'cat /data/local/tmp/ggml-tiny.bin > files/models/ggml-tiny.bin'"
adb shell rm /data/local/tmp/ggml-tiny.bin
```

`MainActivity` is written so a missing model surfaces as a visible error state rather than crashing the app — see its own doc comment for the exact paths it expects.

## Documentation

- [`docs/superpowers/specs/spec.md`](docs/superpowers/specs/spec.md) — the functional requirements and acceptance scenarios (in Spanish, the project's working language)
- [`docs/superpowers/specs/plan.md`](docs/superpowers/specs/plan.md) — the technical plan and architecture decisions
- [`docs/superpowers/specs/tasks.md`](docs/superpowers/specs/tasks.md) — the task list (T001–T022), each with a note on what shipped and how it was verified
- `docs/superpowers/specs/t0NN-*-{spec,plan,tasks}.md` — a per-task spec/plan/tasks trail for every individual task, including the reasoning behind each design decision
- [`docs/superpowers/specs/manuel-mvp-test-protocol.md`](docs/superpowers/specs/manuel-mvp-test-protocol.md) — the manual field-test protocol (30 questions + 5 multi-turn dialogues) to run on a real device
- [`anita_model_training.ipynb`](anita_model_training.ipynb) — the customized, debugged copy of openWakeWord's automatic-training Colab notebook used to train the wake-word model (first `manuel.onnx`, now `anita.onnx`); every environment/dependency fix found while actually running it on current Colab is baked in as real cells, so retraining with a different wake phrase doesn't mean rediscovering all of them

## Development environment note

The bulk of this codebase (T001–T022, the full spec-driven task list) was built in a sandboxed environment with no Android SDK or NDK installed. Every JVM-only unit test was run for real there; Android-framework code was compiled against Robolectric's `android-all` jar as a stand-in for `android.jar`; native JNI code was reviewed line-by-line against the real vendored headers but never compiled; and Jetpack Compose UI/test code was reviewed against the stable public API but never compiled at all.

A later pass installed a real Android SDK/NDK/emulator and ran the actual build for the first time (see [Verified on-device](#verified-on-device)) — it found and fixed two real bugs (the `llama_jni.cpp` const-correctness issue and the on-device FTS5 gap) that no amount of careful review could have caught without a real compiler and a real device.

A third pass, once a trained `manuel.onnx` existed, moved to a real phone and ran the actual conversational loop for the first time — and found the bugs that mattered most: a wake-word/capture design mismatch that had been silently discarding every instruction since the pipeline was first wired up, an asset-path bug specific to how the wake-word library resolves its shared models, and two LLM-generation bugs (missing chat-template formatting, no repetition penalty) that only showed up by listening to real output.

A fourth pass, once turns were finally completing end to end, found that "completing" wasn't the same as "usable": even the smallest LLM tried took 45-160+ seconds per turn, and a query-matching bug (strict AND-of-all-words) was silently failing correctly-transcribed, on-topic questions over a single verb-conjugation mismatch. Neither was a crash or a build failure — both required actually listening to real answers and timing real turns to notice. That pass dropped LLM generation for the MVP entirely (see [Verified on-device](#verified-on-device)) and fixed the matching logic, plus a separate native whisper.cpp crash found only by running several turns in a row.

None of these were visible from code review, from the emulator pass, or from any unit test — they required a live microphone, a real voice, and patience. That's the throughline across all four passes: the sandboxed-development verification tiers documented throughout `docs/superpowers/specs/` were honest about their limits, and each successive real-environment pass is confirmation of exactly where those limits were.
