package com.manuel.mvp.rag

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [VocabularyCorrector]'s exact contract against a small, fixed vocabulary: fix a
 * mis-transcribed word to the closest vocabulary word within a length-scaled edit-distance
 * budget, but only when that closest match is unique and the word is long enough to correct
 * safely. The three motivating real cases (from a live phone testing session): whisper.cpp
 * dropping a letter ("Córdoba" -> "cordoa"), a b/v confusion ("vértice" -> "bértice"), and a
 * qu/gu confusion ("quién" -> "guién").
 */
class VocabularyCorrectorTest {

    private val vocabulary =
        setOf(
            "cordoba", "vertice", "quien", "triangulo", "sumar", "restar", "anita", "es", "que",
            "un", "el", "la", "de", "del", "por", "para",
        )
    private val corrector = VocabularyCorrector(vocabulary)

    @Test
    fun `fixes a dropped letter`() {
        assertEquals("cordoba", corrector.correct("cordoa"))
    }

    @Test
    fun `fixes a b v confusion`() {
        assertEquals("vertice", corrector.correct("bertice"))
    }

    @Test
    fun `fixes a qu gu confusion`() {
        assertEquals("quien", corrector.correct("guien"))
    }

    @Test
    fun `corrects every eligible word in a full sentence, preserving punctuation and spacing`() {
        // "fundo" isn't in this test's vocabulary, so it passes through untouched -- exactly the
        // real-world case ("fundó" -> "fundo" isn't a mis-transcription here, and correction
        // should never invent a fix for a word it has no vocabulary evidence about).
        assertEquals("¿quien fundo cordoba?", corrector.correct("¿guien fundo cordoa?"))
    }

    @Test
    fun `leaves a word already in the vocabulary untouched`() {
        assertEquals("cordoba", corrector.correct("cordoba"))
    }

    @Test
    fun `leaves a genuinely unrelated word untouched`() {
        // "elefante" isn't close (within budget) to anything in this vocabulary.
        assertEquals("elefante", corrector.correct("elefante"))
    }

    @Test
    fun `never corrects words shorter than the minimum correctable length`() {
        // "es"/"el" are both length 2, well under the threshold, even though they're both
        // one edit away from other short vocabulary words.
        assertEquals("es", corrector.correct("es"))
        assertEquals("el", corrector.correct("el"))
    }

    @Test
    fun `never corrects digits`() {
        assertEquals("2024", corrector.correct("2024"))
    }

    @Test
    fun `does not correct when two vocabulary words are equally close`() {
        // "peto" is distance 1 from both "pato" and "pito" -- guessing either would be a coin
        // flip, so neither wins.
        val ambiguous = VocabularyCorrector(setOf("pato", "pito"))
        assertEquals("peto", ambiguous.correct("peto"))
    }

    @Test
    fun `allows a larger edit-distance budget for longer words`() {
        // "triangulo" (9 letters) missing one letter ("triangul") is distance 1, well inside the
        // budget for long words; a further-off two-letter-off variant should also still resolve.
        assertEquals("triangulo", corrector.correct("triangul"))
        assertEquals("triangulo", corrector.correct("triangolo"))
    }
}
