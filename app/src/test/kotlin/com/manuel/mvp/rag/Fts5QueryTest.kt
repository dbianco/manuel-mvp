package com.manuel.mvp.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the contract of [Fts5Query.build], the query builder shared by [AnswerSearcher] and
 * [FragmentSearcher]: content words are quoted and OR-ed, Spanish filler words are dropped, and a
 * question with no content words at all yields `null` (= "no match") instead of a query that
 * would match whatever row happens to contain "eso".
 */
class Fts5QueryTest {

    @Test
    fun `content words are quoted and OR-joined, stopwords dropped`() {
        // "¿Cuántos", "tienen", "un" are stopwords once their punctuation is stripped for the
        // comparison; the surviving tokens are quoted exactly as spoken (punctuation included --
        // FTS5's own tokenizer strips it inside the phrase).
        assertEquals("\"lados\" OR \"triángulo?\"", Fts5Query.build("¿Cuántos lados tienen un triángulo?"))
    }

    @Test
    fun `wake word and leading filler are dropped`() {
        assertEquals("\"sumar?\"", Fts5Query.build("Anita, ¿qué es sumar?"))
        // The original wake word is still a stopword: the test protocol's questions use it.
        assertEquals("\"sumar?\"", Fts5Query.build("Manuel, ¿qué es sumar?"))
    }

    @Test
    fun `questions made only of stopwords yield null`() {
        assertNull(Fts5Query.build("¿y eso?"))
        assertNull(Fts5Query.build("Manuel, explicame"))
        assertNull(Fts5Query.build("¿cuánto es?"))
        assertNull(Fts5Query.build("¿está bien lo que hice?"))
        assertNull(Fts5Query.build("   "))
    }

    @Test
    fun `digits are content words`() {
        // Whisper often transcribes numbers as digits ("del 1 al 10"), and the curated answers'
        // variantes include digit forms for exactly that reason.
        assertEquals("\"tabla\" OR \"5\"", Fts5Query.build("la tabla del 5"))
    }

    @Test
    fun `punctuation-only tokens are dropped`() {
        assertEquals("\"sumar\"", Fts5Query.build("sumar ?"))
    }

    @Test
    fun `embedded double quotes are doubled so they cannot break out of the phrase`() {
        assertEquals("\"dos\" OR \"\"\"más\"\"\" OR \"tres\"", Fts5Query.build("dos \"más\" tres"))
    }

    @Test
    fun `FTS5 metacharacters stay inert inside the quoted phrase`() {
        // A leading "-" would otherwise negate the term, and ":" would become a column filter.
        assertEquals("\"-lados\" OR \"hora:\"", Fts5Query.build("-lados hora:"))
    }
}
