package com.manuel.mvp.pipeline

import com.manuel.mvp.audio.AudioCaptureManager
import com.manuel.mvp.audio.WakeWordListener
import com.manuel.mvp.llm.LlamaEngine
import com.manuel.mvp.llm.PromptBuilder
import com.manuel.mvp.metrics.LocalMetricsLogger
import com.manuel.mvp.metrics.TurnMetrics
import com.manuel.mvp.metrics.WakeWordActivationOutcome
import com.manuel.mvp.rag.FragmentSearcher
import com.manuel.mvp.session.SessionMemory
import com.manuel.mvp.stt.TranscriptionOutcome
import com.manuel.mvp.stt.WhisperTranscriber
import com.manuel.mvp.tts.SpeechSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full conversational loop -- wake word -> capture -> STT -> keyword-prefix
 * validation -> RAG -> LLM -> TTS -- with session memory (FR-011) and metrics logging (FR-013),
 * fully offline (FR-012), behind the two-button manual control described by FR-001.
 *
 * Every collaborator (and the [scope] it runs detection handling on) is constructor-injected
 * rather than owned internally, matching [WakeWordListener]'s convention (T010) of keeping
 * lifecycle ownership with the caller (T020's `MainActivity`/`MainScreen`).
 *
 * Not covered by an automated test: every collaborator except [WhisperTranscriber] needs real
 * Android hardware, a real native engine, or a real TTS voice to exercise for real -- see
 * `t018-conversation-pipeline-spec.md`'s Clarifications. Verified instead by a full compile
 * against every real collaborator class.
 */
class ConversationPipeline(
    private val wakeWordListener: WakeWordListener,
    private val audioCaptureManager: AudioCaptureManager,
    private val whisperTranscriber: WhisperTranscriber,
    private val fragmentSearcher: FragmentSearcher,
    private val sessionMemory: SessionMemory,
    private val promptBuilder: PromptBuilder,
    private val llamaEngine: LlamaEngine,
    private val speechSynthesizer: SpeechSynthesizer,
    private val metricsLogger: LocalMetricsLogger,
    private val scope: CoroutineScope,
    private val repeatPrompt: String = DEFAULT_REPEAT_PROMPT,
) {
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Disarmed)

    /** The assistant's current visible state (FR-001), for the UI layer to observe. */
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private var detectionCollectionJob: Job? = null

    /** Arms background wake-word listening ("Escuchar"). */
    fun arm() {
        wakeWordListener.arm()
        _state.value = PipelineState.Armed
        detectionCollectionJob = scope.launch {
            wakeWordListener.armedDetections.collect {
                handleDetection()
            }
        }
    }

    /**
     * Disarms listening ("Dejar de escuchar"): stops the wake-word engine, cancels any in-flight
     * detection handling, and clears session memory (FR-001, FR-010).
     */
    fun disarm() {
        wakeWordListener.disarm()
        detectionCollectionJob?.cancel()
        detectionCollectionJob = null
        sessionMemory.clear()
        _state.value = PipelineState.Disarmed
    }

    private suspend fun handleDetection() {
        val turnStartMs = System.currentTimeMillis()

        try {
            withContext(Dispatchers.Default) {
                _state.value = PipelineState.Listening

                val captureStartMs = System.currentTimeMillis()
                val audio = audioCaptureManager.captureInstruction()
                val captureDurationMs = System.currentTimeMillis() - captureStartMs

                if (audio == null) {
                    // FR-004: silence/noise/cutoff -- no clear instruction, silently return to waiting.
                    metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)
                    _state.value = PipelineState.Armed
                    return@withContext
                }

                _state.value = PipelineState.Processing

                val transcriptionStartMs = System.currentTimeMillis()
                val transcriptionOutcome = whisperTranscriber.transcribe(audio)
                val transcriptionDurationMs = System.currentTimeMillis() - transcriptionStartMs

                val transcribedText = when (transcriptionOutcome) {
                    is TranscriptionOutcome.RepeatRequested -> {
                        // FR-005: low confidence -- ask the user to repeat, not a silent discard.
                        metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)
                        _state.value = PipelineState.Responding
                        speechSynthesizer.speak(repeatPrompt)
                        _state.value = PipelineState.Armed
                        return@withContext
                    }
                    is TranscriptionOutcome.Transcribed -> transcriptionOutcome.text
                }

                val instruction = wakeWordListener.resolveInstruction(transcribedText)
                if (instruction == null) {
                    // FR-003/FR-004: no keyword prefix / no clear instruction -- silently discard.
                    metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)
                    _state.value = PipelineState.Armed
                    return@withContext
                }

                val searchStartMs = System.currentTimeMillis()
                val ragFragments = fragmentSearcher.search(instruction)
                val searchDurationMs = System.currentTimeMillis() - searchStartMs

                val sessionHistory = sessionMemory.currentExchanges()
                val prompt = promptBuilder.build(instruction, ragFragments, sessionHistory)

                val generationStartMs = System.currentTimeMillis()
                val response = llamaEngine.generate(prompt)
                val generationDurationMs = System.currentTimeMillis() - generationStartMs

                _state.value = PipelineState.Responding
                speechSynthesizer.speak(response)

                sessionMemory.record(instruction, response)
                metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.VALID_TURN)
                metricsLogger.recordTurn(
                    TurnMetrics(
                        captureDurationMs = captureDurationMs,
                        transcriptionDurationMs = transcriptionDurationMs,
                        searchDurationMs = searchDurationMs,
                        generationDurationMs = generationDurationMs,
                        totalDurationMs = System.currentTimeMillis() - turnStartMs,
                    ),
                )
                _state.value = PipelineState.Armed
            }
        } catch (error: Exception) {
            // FR-009: a turn-level failure is recoverable -- log it, surface it, keep listening.
            metricsLogger.recordTurn(
                TurnMetrics(
                    captureDurationMs = 0,
                    transcriptionDurationMs = 0,
                    searchDurationMs = 0,
                    generationDurationMs = 0,
                    totalDurationMs = System.currentTimeMillis() - turnStartMs,
                    error = error.message ?: error::class.simpleName ?: "unknown error",
                ),
            )
            _state.value = PipelineState.Error(error.message ?: "unknown error")
        }
    }

    companion object {
        const val DEFAULT_REPEAT_PROMPT = "¿Podés repetir la pregunta? No te escuché bien."
    }
}
