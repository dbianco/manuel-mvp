package com.manuel.mvp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.manuel.mvp.llm.LlamaEngine
import com.manuel.mvp.llm.PromptBuilder
import com.manuel.mvp.metrics.LocalMetricsLogger
import com.manuel.mvp.pipeline.ConversationPipeline
import com.manuel.mvp.pipeline.PipelineState
import com.manuel.mvp.rag.ContentDao
import com.manuel.mvp.rag.ContentDatabase
import com.manuel.mvp.rag.FragmentSearcher
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
 * this matters concretely today, since neither the whisper/llama GGUF models nor the trained
 * `manuel.onnx` wake-word model exist yet in this repository (see T003/T013/T015's standing
 * notes): a fresh checkout is expected to show an error state until those files are provisioned.
 *
 * `RECORD_AUDIO` (declared in `AndroidManifest.xml` by this task -- it was missing entirely
 * before, correcting an unverified assumption in T016's plan.md) is requested at runtime, before
 * the first [ConversationPipeline.arm] call, via the modern Activity Result API.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var assistantState by remember { mutableStateOf<AssistantState>(AssistantState.Disarmed) }
            var pipeline by remember { mutableStateOf<ConversationPipeline?>(null) }

            val requestRecordAudioPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    pipeline?.arm()
                }
            }

            LaunchedEffect(Unit) {
                try {
                    val builtPipeline = withContext(Dispatchers.IO) { buildPipeline() }
                    pipeline = builtPipeline
                    launch {
                        builtPipeline.state.collectLatest { assistantState = it.toAssistantState() }
                    }
                } catch (error: Exception) {
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
                )
            }

            // Ensures the wake-word engine and any in-flight turn are released if the activity is
            // torn down while armed, even though the LaunchedEffect's own cancellation would
            // already stop the detection-collection coroutine.
            DisposableEffect(Unit) {
                onDispose { pipeline?.disarm() }
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Constructs every [ConversationPipeline] collaborator and wires them together. Model file
     * paths point at internal-storage locations a human/first-launch download step is expected to
     * populate (per the top-level `plan.md`'s stated packaging approach) -- this function only
     * needs *a* path, not to provision the file itself (matching T013/T015's "packaging is out of
     * scope" stance).
     */
    private fun buildPipeline(): ConversationPipeline {
        val contentDatabase = ContentDatabase.create(applicationContext)
        val fragmentSearcher = FragmentSearcher(ContentDao(contentDatabase.readableDatabase))

        val whisperEngine = NativeWhisperEngine(File(filesDir, WHISPER_MODEL_RELATIVE_PATH).absolutePath)
        val whisperTranscriber = WhisperTranscriber(whisperEngine)

        val llamaEngine = LlamaEngine(File(filesDir, LLAMA_MODEL_RELATIVE_PATH).absolutePath)

        val speechSynthesizer = SpeechSynthesizer(applicationContext)
        speechSynthesizer.initialize { /* Spanish-voice availability is not yet surfaced in the UI. */ }

        val wakeWordListener = WakeWordListener(applicationContext, lifecycleScope)

        return ConversationPipeline(
            wakeWordListener = wakeWordListener,
            audioCaptureManager = AudioCaptureManager(),
            whisperTranscriber = whisperTranscriber,
            fragmentSearcher = fragmentSearcher,
            sessionMemory = SessionMemory(clock = Clock.systemUTC()),
            promptBuilder = PromptBuilder(),
            llamaEngine = llamaEngine,
            speechSynthesizer = speechSynthesizer,
            metricsLogger = LocalMetricsLogger(),
            scope = lifecycleScope,
        )
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
        // Neither model is bundled with the app (too large for the APK, and not yet trained/
        // provisioned -- see T003's manuel.onnx note and T013/T015's "packaging is out of scope"
        // clarifications). These paths are where a human/first-launch download step is expected to
        // place them under internal storage before the assistant can function.
        private const val WHISPER_MODEL_RELATIVE_PATH = "models/ggml-tiny.bin"
        private const val LLAMA_MODEL_RELATIVE_PATH = "models/llama-3.2-3b-instruct-q4_k_m.gguf"
    }
}
