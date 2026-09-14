# Native build harness for Manuel's on-device inference stack.
#
# This file wires the vendored llama.cpp and whisper.cpp submodules
# (../third_party/llama.cpp, ../third_party/whisper.cpp) into the Android
# Gradle Plugin's externalNativeBuild (see app/build.gradle.kts), and defines
# the `manuel_native` JNI bridge target:
#   - T013 added the whisper.cpp JNI bridge (src/main/cpp/whisper_jni.cpp).
#   - T015 added the llama.cpp JNI bridge (src/main/cpp/llama_jni.cpp).
#
# Scope note (T002): this task only set up the build mechanism; it added no
# JNI glue code itself — see app/src/main/cpp/manuel_native.cpp.
#
# Scope note (T013/T015): whisper_jni.cpp and llama_jni.cpp were each written
# and reviewed line-by-line against the real third_party/whisper.cpp and
# third_party/llama.cpp headers (and, for llama_jni.cpp, against llama.cpp's
# own examples/simple/simple.cpp reference), but — like T002 before them —
# have not actually been compiled or linked: no NDK/CMake toolchain is
# installed in this sandbox (see t013-whisper-transcriber-spec.md's and
# t015-prompt-builder-llama-engine-spec.md's Clarifications).

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
# manuel_native: JNI bridge target
# ---------------------------------------------------------------------------
# manuel_native.cpp remains a placeholder (see its own header comment); the
# real `extern "C" JNIEXPORT` functions calling into the `whisper` and `llama`
# library targets linked below live in whisper_jni.cpp (T013) and
# llama_jni.cpp (T015), respectively.
add_library(manuel_native SHARED
    src/main/cpp/manuel_native.cpp
    src/main/cpp/whisper_jni.cpp
    src/main/cpp/llama_jni.cpp
)

target_compile_features(manuel_native PRIVATE cxx_std_17)

target_link_libraries(manuel_native
    PRIVATE
        llama
        whisper
)
