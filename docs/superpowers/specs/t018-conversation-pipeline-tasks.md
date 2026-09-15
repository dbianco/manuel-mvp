# Tasks: ConversationPipeline (T018)

## Tasks

- [ ] T001 Implement `PipelineState.kt` (`Disarmed`/`Armed`/`Listening`/`Processing`/`Responding`/`Error(message)`) in `app/src/main/kotlin/com/manuel/mvp/pipeline/PipelineState.kt`
- [ ] T002 Implement `ConversationPipeline.kt` (constructor-injected collaborators, `arm()`/`disarm()`, `handleDetection()` per FR-005 through FR-009) in `app/src/main/kotlin/com/manuel/mvp/pipeline/ConversationPipeline.kt`, depends on T001
- [ ] T003 Compile `ConversationPipeline.kt` standalone against every real collaborator class (`WakeWordListener`, `AudioCaptureManager`, `WhisperTranscriber`, `FragmentSearcher`, `SessionMemory`, `PromptBuilder`, `LlamaEngine`, `SpeechSynthesizer`, `LocalMetricsLogger`) plus `kotlinx-coroutines-core` and the Android/openWakeWord/onnxruntime classes those need, confirming no API mismatch, depends on T002
- [ ] T004 grep-confirm no HTTP/socket/URL API is referenced in the new files (FR-010), depends on T002
