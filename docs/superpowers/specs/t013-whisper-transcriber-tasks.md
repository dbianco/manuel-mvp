# Tasks: WhisperTranscriber implementation (T013)

## Tasks

- [ ] T001 Check out the `third_party/whisper.cpp` submodule (`git submodule update --init --depth 1 third_party/whisper.cpp`) so `include/whisper.h` can be read for its real API
- [ ] T002 Implement `WhisperTranscriber.kt` (`TranscriptionResult`, `TranscriptionOutcome`, `WhisperEngine`, `WhisperTranscriber`) in `app/src/main/kotlin/com/manuel/mvp/stt/WhisperTranscriber.kt`, matching T012's pinned contract exactly, depends on T001
- [ ] T003 Run `WhisperTranscriberTest.kt` (T012, unmodified) against T002's implementation and confirm all 6 test cases pass, depends on T002
- [ ] T004 Implement `NativeWhisperEngine.kt` (`WhisperEngine` impl, `external fun nativeInit/nativeTranscribe/nativeRelease`, fail-fast on load failure) in `app/src/main/kotlin/com/manuel/mvp/stt/NativeWhisperEngine.kt`, depends on T002
- [ ] T005 Implement the JNI glue in `app/src/main/cpp/whisper_jni.cpp` (PCM16->float conversion, `whisper_full` call with `language="es"`, confidence via averaged `whisper_full_get_token_p`, `TranscriptionResult` object construction via JNI), reviewed line-by-line against `third_party/whisper.cpp/include/whisper.h`, depends on T001, T004
- [ ] T006 Update `app/CMakeLists.txt` to add `whisper_jni.cpp` as a source of the `manuel_native` target, depends on T005
