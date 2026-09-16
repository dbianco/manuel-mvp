package com.manuel.mvp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.manuel.mvp.audio.AudioCaptureManager
import com.manuel.mvp.audio.WakeWordListener
import com.manuel.mvp.metrics.LocalMetricsLogger
import com.manuel.mvp.pipeline.ConversationPipeline
import com.manuel.mvp.pipeline.PipelineState
import com.manuel.mvp.rag.AnswerSearcher
import com.manuel.mvp.rag.ContentDao
import com.manuel.mvp.rag.ContentDatabase
import com.manuel.mvp.rag.FragmentSearcher
import com.manuel.mvp.rag.VocabularyCorrector
import com.manuel.mvp.session.SessionMemory
import com.manuel.mvp.stt.NativeWhisperEngine
import com.manuel.mvp.stt.WhisperTranscriber
import com.manuel.mvp.tts.SpeechSynthesizer
import com.manuel.mvp.ui.AssistantState
import com.manuel.mvp.ui.MainScreen
import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manuel's single entry point (FR-001): hosts [MainScreen] and wires it to a real
 * [ConversationPipeline] built from every collaborator implemented T006-T018.
 *
 * Pipeline construction happens inside a [LaunchedEffect] on [Dispatchers.IO] (several
 * collaborator constructors do blocking I/O or native/JNI init) and is wrapped in a `try`/`catch`
 * so a missing model file surfaces as [AssistantState.Error] rather than crashing the activity --
 * this matters concretely because the whisper GGML model still isn't bundled (too large for the
 * APK; see [WHISPER_MODEL_RELATIVE_PATH]) and needs a first-launch download step. The trained
 * wake-word model (`wakeword/anita.onnx`, see [WakeWordListener.DEFAULT_MODEL_ASSET_PATH]) *is*
 * bundled.
 *
 * `RECORD_AUDIO` (declared in `AndroidManifest.xml` by this task -- it was missing entirely
 * before, correcting an unverified assumption in T016's plan.md) is requested at runtime, before
 * the first [ConversationPipeline.arm] call, via the modern Activity Result API.
 */
class MainActivity : ComponentActivity() {

    // Both pipeline?.arm() call sites below are genuinely permission-checked at runtime --
    // hasRecordAudioPermission() before one, the RequestPermission() launcher's own `granted`
    // callback parameter before the other -- but lint's MissingPermission check only recognizes
    // permission checks written as an inline ContextCompat.checkSelfPermission(...) comparison in
    // the same expression, not delegated through a named helper or a callback parameter.
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var assistantState by remember { mutableStateOf<AssistantState>(AssistantState.Disarmed) }
            var lastHeard by remember { mutableStateOf<String?>(null) }
            var pipeline by remember { mutableStateOf<ConversationPipeline?>(null) }
            var contentDatabase by remember { mutableStateOf<ContentDatabase?>(null) }

            val requestRecordAudioPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    pipeline?.arm()
                }
            }

            LaunchedEffect(Unit) {
                try {
                    val built = withContext(Dispatchers.IO) { buildPipeline() }
                    contentDatabase = built.contentDatabase
                    pipeline = built.pipeline
                    launch {
                        built.pipeline.state.collectLatest { assistantState = it.toAssistantState() }
                    }
                    launch {
                        built.pipeline.lastHeard.collectLatest { lastHeard = it }
                    }
                } catch (error: Exception) {
                    Log.e("MainActivity", "Pipeline init failed", error)
                    assistantState = AssistantState.Error(
                        error.message ?: "No se pudo iniciar el asistente",
                    )
                }
            }

            MaterialTheme {
                MainScreen(
                    state = assistantState,
                    onEscucharClick = {
                        if (hasRecordAudioPermission()) {
                            pipeline?.arm()
                        } else {
                            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onDejarDeEscucharClick = { pipeline?.disarm() },
                    onHablarAhoraClick = { pipeline?.triggerManualTurn() },
                    lastHeard = lastHeard,
                )
            }

            // Ensures every native/OS resource this activity's pipeline holds (wake-word engine,
            // whisper.cpp/llama.cpp native contexts, TTS engine, the FTS5 database connection) is
            // actually released when the activity is torn down, not just disarmed -- previously
            // only disarm() ran here, leaking every one of those on every activity destroy.
            DisposableEffect(Unit) {
                onDispose {
                    pipeline?.release()
                    contentDatabase?.close()
                }
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Bundles [buildPipeline]'s pipeline with the [ContentDatabase] it's backed by, since the
     * database's connection is only needed by [FragmentSearcher] construction, but its lifecycle
     * (closing it) belongs to the activity, not to [ConversationPipeline].
     */
    private class PipelineBundle(val pipeline: ConversationPipeline, val contentDatabase: ContentDatabase)

    /**
     * Constructs every [ConversationPipeline] collaborator and wires them together. Model file
     * paths point at internal-storage locations a human/first-launch download step is expected to
     * populate (per the top-level `plan.md`'s stated packaging approach) -- this function only
     * needs *a* path, not to provision the file itself (matching T013/T015's "packaging is out of
     * scope" stance).
     */
    private fun buildPipeline(): PipelineBundle {
        val contentDatabase = ContentDatabase.create(applicationContext)
        val contentDao = ContentDao(contentDatabase.connection)
        val fragmentSearcher = FragmentSearcher(contentDao)
        val answerSearcher = AnswerSearcher(contentDao)
        val vocabularyCorrector = VocabularyCorrector(VocabularyCorrector.buildVocabulary(contentDao))

        val whisperEngine = NativeWhisperEngine(File(filesDir, WHISPER_MODEL_RELATIVE_PATH).absolutePath)
        val whisperTranscriber = WhisperTranscriber(whisperEngine)

        val speechSynthesizer = SpeechSynthesizer(applicationContext)
        speechSynthesizer.initialize { /* Spanish-voice availability is not yet surfaced in the UI. */ }

        val wakeWordListener = WakeWordListener(applicationContext, lifecycleScope)

        val pipeline = ConversationPipeline(
            wakeWordListener = wakeWordListener,
            audioCaptureManager = AudioCaptureManager(),
            whisperTranscriber = whisperTranscriber,
            fragmentSearcher = fragmentSearcher,
            answerSearcher = answerSearcher,
            sessionMemory = SessionMemory(clock = Clock.systemUTC()),
            speechSynthesizer = speechSynthesizer,
            metricsLogger = LocalMetricsLogger(),
            scope = lifecycleScope,
            vocabularyCorrector = vocabularyCorrector,
        )
        return PipelineBundle(pipeline, contentDatabase)
    }

    private fun PipelineState.toAssistantState(): AssistantState = when (this) {
        is PipelineState.Disarmed -> AssistantState.Disarmed
        is PipelineState.Armed -> AssistantState.Armed
        is PipelineState.Listening -> AssistantState.Listening
        is PipelineState.Processing -> AssistantState.Processing
        is PipelineState.Responding -> AssistantState.Responding
        is PipelineState.Error -> AssistantState.Error(message)
    }

    companion object {
        // Not bundled with the app (too large for the APK) -- this path is where a
        // human/first-launch download step is expected to place it under internal storage before
        // transcription can work. The llama.cpp/Qwen model this used to load is no longer wired
        // up at all: see ConversationPipeline's doc comment for why the MVP now answers from
        // matched lesson content directly instead of LLM generation.
        //
        // Tried base after verified on-device transcription errors (whisper heard "sumar" as
        // "rumar", "su mar", and "fumar" across three consecutive attempts), but on-device timing
        // showed base ran ~3x slower than tiny (28s vs 8.5s transcription alone, blowing the 15s
        // SC-005 target) for an accuracy gain that wasn't reliably worth that cost -- reverted.
        private const val WHISPER_MODEL_RELATIVE_PATH = "models/ggml-tiny.bin"
    }
}
