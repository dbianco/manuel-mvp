# Tasks: PromptBuilder + LlamaEngine implementation (T015)

## Tasks

- [ ] T001 Check out the `third_party/llama.cpp` submodule (`git submodule update --init --depth 1 third_party/llama.cpp`) so `include/llama.h` and `examples/simple/simple.cpp` can be read for the real API
- [ ] T002 Implement `PromptBuilder.kt` (`build(currentInstruction, ragFragments, sessionHistory): String`, `SYSTEM_INSTRUCTIONS` constant, history-then-fragments-then-instruction ordering) in `app/src/main/kotlin/com/manuel/mvp/llm/PromptBuilder.kt`, matching T014's pinned contract exactly
- [ ] T003 Run `PromptBuilderTest.kt` (T014, unmodified) against T002's implementation and confirm all 7 test cases pass, depends on T002
- [ ] T004 Implement `LlamaEngine.kt` (`generate(prompt: String): String`, `external fun nativeInit/nativeGenerate/nativeRelease`, fail-fast on load/init failure) in `app/src/main/kotlin/com/manuel/mvp/llm/LlamaEngine.kt`, depends on T001
- [ ] T005 Implement the JNI glue in `app/src/main/cpp/llama_jni.cpp` (model load, tokenize, context+sampler init, greedy decode loop with max-token cap and EOG stop, detokenize), reviewed line-by-line against `third_party/llama.cpp/include/llama.h` and `examples/simple/simple.cpp`, depends on T001, T004
- [ ] T006 Update `app/CMakeLists.txt` to add `llama_jni.cpp` as a source of the `manuel_native` target, depends on T005
