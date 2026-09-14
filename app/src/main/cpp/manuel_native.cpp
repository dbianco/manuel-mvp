// Placeholder native source for the `manuel_native` JNI bridge target.
//
// T002 (this task) only wired up the CMake/NDK build harness. Real JNI glue
// lives in sibling .cpp files added by later tasks:
//   - T013 added whisper_jni.cpp (WhisperTranscriber.kt <-> whisper.cpp bridge).
//   - T015 added llama_jni.cpp (LlamaEngine.kt <-> llama.cpp bridge).
//
// This file intentionally contains no calls into llama.cpp's or
// whisper.cpp's APIs and declares no JNI (`extern "C" JNIEXPORT ...`)
// functions itself — it exists so the `manuel_native` CMake target has (at
// least) one translation unit to compile, proving the native build mechanism
// (Gradle externalNativeBuild -> CMake -> vendored llama.cpp/whisper.cpp
// submodules) is wired correctly end to end.
