package com.manuel.mvp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps `android.speech.tts.TextToSpeech` to speak the LLM's response aloud in Spanish, offline
 * (FR-009). On successful initialization, checks the engine's reported available languages via
 * [SpanishVoiceChecker] to determine whether a Spanish voice is actually installed on this
 * device -- the "verificando en el primer inicio" requirement.
 *
 * Not exercised by any automated test in this repo: it requires a real TTS engine to exercise for
 * real (see [SpanishVoiceCheckerTest] for the one genuinely pure decision extracted out of it).
 * [shutdown] MUST be called when this synthesizer is no longer needed, to release the engine.
 */
class SpeechSynthesizer(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null

    /**
     * Whether a Spanish voice was found available after initialization. `null` until
     * [initialize]'s callback has run.
     */
    var hasSpanishVoice: Boolean? = null
        private set

    /**
     * Starts the TTS engine and sets it to Spanish. [onReady] is invoked once initialization
     * finishes, with whether a Spanish voice is available -- `false` both when initialization
     * failed and when it succeeded but no Spanish voice was found, since either way speaking in
     * Spanish isn't reliably possible.
     */
    fun initialize(onReady: (hasSpanishVoice: Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            val spanishAvailable = if (status == TextToSpeech.SUCCESS) {
                val engine = textToSpeech
                val availableLocales = engine?.availableLanguages ?: emptySet()
                val spanishVoiceFound = SpanishVoiceChecker.hasSpanishVoice(availableLocales)
                if (spanishVoiceFound) {
                    engine?.language = SPANISH
                }
                spanishVoiceFound
            } else {
                false
            }
            hasSpanishVoice = spanishAvailable
            onReady(spanishAvailable)
        }
    }

    /** Speaks [text] aloud, replacing any speech currently in progress. */
    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Stops any in-progress speech and releases the underlying engine. */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    companion object {
        private val SPANISH = Locale("es")
        private const val UTTERANCE_ID = "manuel_response"
    }
}
