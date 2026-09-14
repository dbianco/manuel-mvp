# Tasks: WakeWordListener + KeywordPrefixParser implementation (T010)

## Tasks

- [ ] T001 Implement `KeywordPrefixParser.kt` (`parse(transcript: String): String?`, keyword anchored at trimmed start, case-insensitive, optional comma separator, `null` on missing keyword or blank/absent instruction) in `app/src/main/kotlin/com/manuel/mvp/audio/KeywordPrefixParser.kt`
- [ ] T002 Run `KeywordPrefixParserTest.kt` (T009, unmodified) against T001's implementation and confirm all 7 test cases pass, depends on T001
- [ ] T003 Implement `WakeWordListener.kt` (wraps `WakeWordEngine` with a single "Manuel" `WakeWordModel`; `arm()`/`disarm()`; exposes `armedDetections: Flow<WakeWordDetection>`; `resolveInstruction(String): String?` delegating to `KeywordPrefixParser.parse`) in `app/src/main/kotlin/com/manuel/mvp/audio/WakeWordListener.kt`
- [ ] T004 Compile `WakeWordListener.kt` standalone against the real openwakeword/onnxruntime/kotlinx-coroutines-core jars and a JVM-loadable `android.content.Context` (Robolectric's `android-all` jar), confirming no API mismatch against the actual library, depends on T003
