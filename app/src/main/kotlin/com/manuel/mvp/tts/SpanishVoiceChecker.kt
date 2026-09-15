package com.manuel.mvp.tts

import java.util.Locale

/**
 * Pure check for whether a Spanish-language voice is available, per FR-009. `java.util.Locale`
 * (the type `TextToSpeech.getAvailableLanguages()` returns) is a JDK class, not Android-only, so
 * this check is fully unit-testable without any Android dependency -- see [SpeechSynthesizer] for
 * where it's used against a real TTS engine's reported languages.
 */
object SpanishVoiceChecker {

    fun hasSpanishVoice(availableLocales: Set<Locale>): Boolean =
        availableLocales.any { it.language.equals("es", ignoreCase = true) }
}
