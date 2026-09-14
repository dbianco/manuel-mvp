# Native build harness for Manuel's on-device inference stack.
#
# This file wires the vendored llama.cpp and whisper.cpp submodules
# (../third_party/llama.cpp, ../third_party/whisper.cpp) into the Android
# Gradle Plugin's externalNativeBuild (see app/build.gradle.kts), and defines
# a placeholder `manuel_native` JNI bridge target that later tasks will fill
# in with real code:
#   - T013 (WhisperTranscriber.kt) will add the whisper.cpp JNI bridge.
#   - T015 (LlamaEngine.kt) will add the llama.cpp JNI bridge.
#
# Scope note (T002): this task only sets up the build mechanism. It does not
# add any JNI glue code that actually calls into llama.cpp's or whisper.cpp's
# APIs — see app/src/main/cpp/manuel_native.cpp.

cmake_minimum_required(VERSION 3.22.1)

project("manuel_native" LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# llama.cpp (vendored as a git submodule at third_party/llama.cpp)
# ---------------------------------------------------------------------------
# Configure it as a library-only dependency before add_subdirectory(): we only
# need the `llama` library target, not llama.cpp's own tests, tools, examples,
# server example, or unified CLI app binary. These cache variables mirror the
# real option() flags declared in third_party/llama.cpp/CMakeLists.txt; they
# would already default to OFF here (llama.cpp auto-detects
# LLAMA_STANDALONE = OFF when consumed via add_subdirectory), but are set
# explicitly for clarity and to not depend on that auto-detection.
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "Disable llama.cpp common utils (only needed by its own tools/examples)" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable llama.cpp tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Disable llama.cpp CLI tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable llama.cpp examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable llama.cpp server example" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "Disable llama.cpp unified CLI app binary" FORCE)

add_subdirectory(
    "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/llama.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/third_party/llama.cpp"
)

# ---------------------------------------------------------------------------
# whisper.cpp (vendored as a git submodule at third_party/whisper.cpp)
# ---------------------------------------------------------------------------
# Same idea: library target only, no tests/examples/server example.
#
# Note: both llama.cpp's and whisper.cpp's own top-level CMakeLists.txt guard
# their internal `add_subdirectory(ggml)` with `if (NOT TARGET ggml)`. Because
# llama.cpp is added first above, its vendored copy of ggml wins and
# whisper.cpp reuses that already-defined `ggml` target instead of building
# its own copy. This is the standard, upstream-supported way to combine these
# two libraries in one CMake project; it is called out here because if the
# two vendored ggml snapshots ever diverge significantly, that could surface
# as a build/link error in a later task once a real NDK build is possible in
# a full dev environment (not reproducible in this sandbox — no NDK/cmake are
# installed here, see task-2-report.md).
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "Disable whisper.cpp tests" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "Disable whisper.cpp examples" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "Disable whisper.cpp server example" FORCE)

add_subdirectory(
    "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/whisper.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/third_party/whisper.cpp"
)

# ---------------------------------------------------------------------------
# manuel_native: placeholder JNI bridge target
# ---------------------------------------------------------------------------
# T013/T015 will add real `extern "C" JNIEXPORT` functions to
# manuel_native.cpp (or additional .cpp files added to this target) that call
# into the `llama` and `whisper` library targets linked below. No such calls
# exist yet on purpose — see app/src/main/cpp/manuel_native.cpp.
add_library(manuel_native SHARED
    src/main/cpp/manuel_native.cpp
)

target_compile_features(manuel_native PRIVATE cxx_std_17)

target_link_libraries(manuel_native
    PRIVATE
        llama
        whisper
)
