// Placeholder native source for the `manuel_native` JNI bridge target.
//
// T002 (this task) only wires up the CMake/NDK build harness so that later
// tasks can add real JNI glue code here:
//   - T013 will add the WhisperTranscriber.kt <-> whisper.cpp bridge.
//   - T015 will add the LlamaEngine.kt <-> llama.cpp bridge.
//
// This file intentionally contains no calls into llama.cpp's or
// whisper.cpp's APIs and declares no JNI (`extern "C" JNIEXPORT ...`)
// functions yet — it exists only so the `manuel_native` CMake target has a
// translation unit to compile, proving the native build mechanism (Gradle
// externalNativeBuild -> CMake -> vendored llama.cpp/whisper.cpp submodules)
// is wired correctly end to end.
