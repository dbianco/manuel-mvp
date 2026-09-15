package com.manuel.mvp.tts

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpanishVoiceChecker] (T016): a pure check over `java.util.Locale` (a JDK type,
 * not Android-only) for whether a Spanish-language voice is available, per FR-009's "verificando
 * en el primer inicio que exista una voz descargada". [SpeechSynthesizer] uses this at TextToSpeech
 * init time; it has no automated test of its own since it needs a real TTS engine to exercise for
 * real -- this file covers the one genuinely pure decision extracted out of it.
 */
class SpanishVoiceCheckerTest {

    /** Scenario 1a: a locale whose language is exactly "es" counts as Spanish. */
    @Test
    fun `returns true when a Spanish-only locale is present`() {
        assertTrue(SpanishVoiceChecker.hasSpanishVoice(setOf(Locale("es"))))
    }

    /** Scenario 1b: a Spanish locale with a country/variant still counts (language code alone matters). */
    @Test
    fun `returns true when Spanish is present among other locales`() {
        val locales = setOf(Locale("en", "US"), Locale("es", "AR"), Locale("fr", "FR"))

        assertTrue(SpanishVoiceChecker.hasSpanishVoice(locales))
    }

    /** Scenario: no Spanish locale anywhere in the set returns false. */
    @Test
    fun `returns false when no Spanish locale is present`() {
        val locales = setOf(Locale("en", "US"), Locale("fr", "FR"))

        assertFalse(SpanishVoiceChecker.hasSpanishVoice(locales))
    }

    /** Scenario 2: an empty set returns false without throwing. */
    @Test
    fun `returns false for an empty set without throwing`() {
        assertFalse(SpanishVoiceChecker.hasSpanishVoice(emptySet()))
    }
}
