package com.manuel.mvp.pipeline

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.RequiresPermission
import com.manuel.mvp.audio.AudioCaptureManager
import com.manuel.mvp.audio.WakeWordListener
import com.manuel.mvp.metrics.LocalMetricsLogger
import com.manuel.mvp.metrics.TurnMetrics
import com.manuel.mvp.metrics.WakeWordActivationOutcome
import com.manuel.mvp.rag.AnswerSearcher
import com.manuel.mvp.rag.FragmentSearcher
import com.manuel.mvp.session.SessionMemory
import com.manuel.mvp.stt.TranscriptionOutcome
import com.manuel.mvp.stt.WhisperTranscriber
import com.manuel.mvp.tts.SpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full conversational loop -- wake word -> capture -> STT -> content search ->
 * TTS -- with session memory (FR-011) and metrics logging (FR-013), fully offline (FR-012),
 * behind the three-button manual control described by FR-001.
 *
 * MVP simplification (verified on-device): the LLM generation step ([com.manuel.mvp.llm.LlamaEngine]
 * / [com.manuel.mvp.llm.PromptBuilder], still present in the codebase but no longer wired up here)
 * was too slow (multi-minute on phone CPU with no GPU delegate) and too unreliable (rambling,
 * repetition) for real classroom use. Answers are looked up instead, in three tiers, and spoken
 * verbatim: first a curated question/answer pair ([AnswerSearcher], matched on the question's
 * phrasing -- `content/preguntas_respuestas.json`, 30 entries written for speech, grounded in
 * the lesson content); failing that, the best-matching lesson fragment's own text
 * ([FragmentSearcher] -- `matematica_lecciones.json`'s `texto` fields are already short, direct,
 * spoken-friendly sentences); failing that, [NO_ANSWER_RESPONSE]. Both searchers OR the
 * question's content words together and return nothing for an off-topic question (no shared
 * vocabulary) or an all-filler one ("¿y eso?"), so FR-008's "don't invent an answer" rule is
 * enforced structurally, with no separate out-of-scope detection needed.
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
    private val answerSearcher: AnswerSearcher,
    private val sessionMemory: SessionMemory,
    private val speechSynthesizer: SpeechSynthesizer,
    private val metricsLogger: LocalMetricsLogger,
    private val scope: CoroutineScope,
    private val repeatPrompt: String = DEFAULT_REPEAT_PROMPT,
    private val noAnswerResponse: String = NO_ANSWER_RESPONSE,
) {
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Disarmed)

    /** The assistant's current visible state (FR-001), for the UI layer to observe. */
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private var detectionCollectionJob: Job? = null

    /**
     * Arms background wake-word listening ("Escuchar").
     *
     * Requires `android.permission.RECORD_AUDIO` to already be granted -- every detection this
     * triggers eventually calls [AudioCaptureManager.captureInstruction], which needs it. The
     * caller (`MainActivity`) checks/requests this permission before calling [arm]; lint cannot
     * trace that check through the asynchronous [WakeWordListener.armedDetections] collection down
     * to [handleDetection]'s eventual call, so it's suppressed there instead of re-checked (see
     * [handleDetection]).
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun arm() {
        // Idempotent: MainActivity's two arm() call sites (the direct click handler and the
        // permission-grant callback) can race or double-fire from user double-taps. Without this
        // guard, a second call would leak the first detectionCollectionJob (never cancelled) and
        // leave two collectors racing to handle the same wake-word detections.
        if (detectionCollectionJob != null) return

        wakeWordListener.arm()
        _state.value = PipelineState.Armed
        detectionCollectionJob = scope.launch {
            wakeWordListener.armedDetections.collect {
                handleDetection()
            }
        }
    }

    /**
     * Manually starts a conversation turn without requiring a successful acoustic wake-word
     * detection first -- a fallback for when the trained wake-word model's real-world accuracy is
     * unreliable (verified on-device: it misses real speech often enough at moderate confidence
     * thresholds to frustrate normal use). The caller (`MainScreen`'s "Hablar ahora" button) is
     * responsible for only offering this while [state] is [PipelineState.Armed], so RECORD_AUDIO
     * is guaranteed granted by the same contract [arm] already relies on.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun triggerManualTurn() {
        scope.launch { handleDetection() }
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

    /**
     * Disarms, then releases every native/OS resource this pipeline's collaborators hold
     * ([WakeWordListener], [WhisperTranscriber]'s engine, [SpeechSynthesizer]). Callers
     * (`MainActivity`'s `DisposableEffect`) must call this exactly once, when the pipeline itself
     * is being torn down for good -- unlike [disarm], this pipeline must not be used again
     * afterwards.
     */
    fun release() {
        disarm()
        wakeWordListener.release()
        whisperTranscriber.release()
        speechSynthesizer.shutdown()
    }

    // RECORD_AUDIO is guaranteed by arm()'s own @RequiresPermission contract -- this is only
    // ever reached after a successful arm() call, so re-checking here would be redundant, but
    // lint cannot trace the permission check across the intervening coroutine/Flow collection.
    @SuppressLint("MissingPermission")
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
                Log.d("ConversationPipeline", "Transcribed: \"$transcribedText\"")

                val instruction = wakeWordListener.resolveInstruction(transcribedText)
                Log.d("ConversationPipeline", "Resolved instruction: ${instruction?.let { "\"$it\"" }}")
                if (instruction == null) {
                    // FR-003/FR-004: no keyword prefix / no clear instruction -- silently discard.
                    metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.POSSIBLE_FALSE_POSITIVE)
                    _state.value = PipelineState.Armed
                    return@withContext
                }

                val searchStartMs = System.currentTimeMillis()
                val cannedAnswer = answerSearcher.search(instruction).firstOrNull()
                val fragment =
                    if (cannedAnswer == null) fragmentSearcher.search(instruction, limit = 1).firstOrNull() else null
                val searchDurationMs = System.currentTimeMillis() - searchStartMs

                // MVP simplification: speak a curated answer (or, failing that, the best-matching
                // lesson fragment's own text) verbatim instead of generating a new sentence -- see
                // this class's doc comment for why.
                val response = cannedAnswer?.respuesta ?: fragment?.texto ?: noAnswerResponse
                val responseSource = when {
                    cannedAnswer != null -> "respuestas/${cannedAnswer.id}"
                    fragment != null -> "fragments/${fragment.id}"
                    else -> "none"
                }
                Log.d("ConversationPipeline", "Response ($responseSource): \"$response\"")
                Log.d(
                    "ConversationPipeline",
                    "Timing: capture=${captureDurationMs}ms transcribe=${transcriptionDurationMs}ms " +
                        "search=${searchDurationMs}ms total=${System.currentTimeMillis() - turnStartMs}ms",
                )

                _state.value = PipelineState.Responding
                speechSynthesizer.speak(response)

                sessionMemory.record(instruction, response)
                metricsLogger.recordWakeWordActivation(WakeWordActivationOutcome.VALID_TURN)
                metricsLogger.recordTurn(
                    TurnMetrics(
                        captureDurationMs = captureDurationMs,
                        transcriptionDurationMs = transcriptionDurationMs,
                        searchDurationMs = searchDurationMs,
                        generationDurationMs = 0,
                        totalDurationMs = System.currentTimeMillis() - turnStartMs,
                    ),
                )
                _state.value = PipelineState.Armed
            }
        } catch (cancellation: CancellationException) {
            // disarm()/scope teardown cancels this coroutine deliberately (e.g. the activity is
            // torn down mid-turn) -- that is not a turn failure, and must propagate so structured
            // concurrency actually stops the coroutine instead of being reported as an error state.
            throw cancellation
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

        // FR-008's "no inventes una respuesta" rule, as a fixed spoken line instead of an
        // LLM-generated one -- reached whenever FragmentSearcher finds no matching lesson content.
        const val NO_ANSWER_RESPONSE = "No tengo información suficiente para responder esa pregunta con seguridad."
    }
}
