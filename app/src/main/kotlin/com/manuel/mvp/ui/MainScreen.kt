package com.manuel.mvp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Manuel's single screen (FR-001): two explicit control buttons -- "Escuchar" (arms) and "Dejar de
 * escuchar" (disarms) -- and a visible status reflecting [state].
 *
 * Deliberately stateless: takes [state] and callbacks rather than owning a
 * `ConversationPipeline`/`ViewModel` reference itself, which is what lets `MainScreenTest` (T019)
 * render it directly with a fixed state, with no real hardware, native engine, or TTS voice
 * involved. `MainActivity` is responsible for wiring the real pipeline to these callbacks and this
 * state.
 *
 * Both "Escuchar"/"Dejar de escuchar" buttons always stay on-screen; only their enabled state
 * toggles (exactly one is ever actionable at a time) -- stable positions are easier to predict for
 * a screen-reader user or a young child than a button that appears/disappears. A third,
 * optional "Hablar ahora" button (enabled only while [state] is [AssistantState.Armed]) manually
 * starts a turn without needing a successful acoustic wake-word detection first -- added after
 * real-device testing showed the trained wake-word model misses real speech often enough to
 * frustrate normal use; [onHablarAhoraClick] defaults to a no-op so existing callers/tests that
 * don't care about this fallback don't need to change.
 */
@Composable
fun MainScreen(
    state: AssistantState,
    onEscucharClick: () -> Unit,
    onDejarDeEscucharClick: () -> Unit,
    onHablarAhoraClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = statusText(state), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onEscucharClick,
                    enabled = state is AssistantState.Disarmed,
                    modifier = Modifier.semantics { contentDescription = ESCUCHAR_LABEL },
                ) {
                    Text(ESCUCHAR_LABEL)
                }
                Button(
                    onClick = onDejarDeEscucharClick,
                    enabled = state !is AssistantState.Disarmed,
                    modifier = Modifier.semantics { contentDescription = DEJAR_DE_ESCUCHAR_LABEL },
                ) {
                    Text(DEJAR_DE_ESCUCHAR_LABEL)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onHablarAhoraClick,
                enabled = state is AssistantState.Armed,
                modifier = Modifier.semantics { contentDescription = HABLAR_AHORA_LABEL },
            ) {
                Text(HABLAR_AHORA_LABEL)
            }
        }
    }
}

private fun statusText(state: AssistantState): String = when (state) {
    is AssistantState.Disarmed -> "Desarmado"
    is AssistantState.Armed -> "Armado, esperando la palabra clave \"Anita\""
    is AssistantState.Listening -> "Escuchando tu pregunta..."
    is AssistantState.Processing -> "Procesando tu pregunta..."
    is AssistantState.Responding -> "Respondiendo..."
    is AssistantState.Error -> "Hubo un error: ${state.message}"
}

const val ESCUCHAR_LABEL = "Escuchar"
const val DEJAR_DE_ESCUCHAR_LABEL = "Dejar de escuchar"
const val HABLAR_AHORA_LABEL = "Hablar ahora"
