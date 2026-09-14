package com.manuel.mvp.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract that T010's real `KeywordPrefixParser`
 * implementation must satisfy: given a full STT transcript, extract the instruction that follows
 * the wake word ("Manuel, <instrucción>") when — and only when — the transcript is clearly
 * prefixed by it, or return `null` (silent discard) otherwise.
 *
 * This is a purely textual contract, separate from the acoustic wake-word detector
 * (`WakeWordListener`/openWakeWord, also T010): it operates on the already-transcribed text of the
 * captured audio, not on the acoustic detection event itself (see `t009-keyword-prefix-parser-plan.md`).
 * Splitting it out this way is what makes the FR-003/FR-004 keyword-handling logic testable here
 * without mocking any audio hardware.
 *
 * `KeywordPrefixParser` does not exist in the main source set yet — it is T010's job, not this
 * task's (T009). This file will not compile until T010 adds it; that is expected for this
 * test-first split (T009 defines the contract via tests, T010 implements it), same pattern as
 * T005 -> T006 and T007 -> T008.
 *
 * ### Contract under test
 *
 * `KeywordPrefixParser.parse(transcript: String): String?`:
 * - Matches the keyword ("Manuel") case-insensitively, anchored at the (trimmed) start of the
 *   transcript — the keyword must open the utterance, not merely appear somewhere in it.
 * - An optional comma and/or whitespace may separate the keyword from the instruction; neither is
 *   required.
 * - Returns the trimmed instruction text when one follows the keyword.
 * - Returns `null` when the keyword isn't found at the start (FR-003: ignore audio without the
 *   keyword), or when it is found but nothing meaningful follows it (FR-004: keyword detected, no
 *   clear instruction — silent discard, not an empty-string instruction).
 */
class KeywordPrefixParserTest {

    /** Scenario 1: keyword + comma + instruction extracts the instruction, trimmed. */
    @Test
    fun `extracts instruction after keyword and comma`() {
        val result = KeywordPrefixParser.parse("Manuel, ¿cuánto es tres por cuatro?")

        assertEquals("¿cuánto es tres por cuatro?", result)
    }

    /** Scenario 2: keyword matching is case-insensitive. */
    @Test
    fun `matches keyword case-insensitively`() {
        val result = KeywordPrefixParser.parse("manuel, contame un cuento")

        assertEquals("contame un cuento", result)
    }

    /** Scenario 3: the comma is optional — whitespace alone separates keyword and instruction. */
    @Test
    fun `extracts instruction when comma separator is absent`() {
        val result = KeywordPrefixParser.parse("MANUEL dime la tabla del 5")

        assertEquals("dime la tabla del 5", result)
    }

    /** Scenario 4: no keyword anywhere — silently discarded (FR-003). */
    @Test
    fun `returns null when transcript has no keyword`() {
        val result = KeywordPrefixParser.parse("qué lindo día para jugar")

        assertNull(result)
    }

    /** Scenario 5: keyword present but nothing (or only punctuation/whitespace) follows it (FR-004). */
    @Test
    fun `returns null when keyword is not followed by a clear instruction`() {
        assertNull(KeywordPrefixParser.parse("Manuel"))
        assertNull(KeywordPrefixParser.parse("Manuel,"))
        assertNull(KeywordPrefixParser.parse("Manuel   "))
    }

    /** Scenario 6: keyword present but not at the start — must not activate (FR-003 anchoring). */
    @Test
    fun `returns null when keyword does not anchor the start of the transcript`() {
        val result = KeywordPrefixParser.parse("che Manuel qué hora es")

        assertNull(result)
    }

    /** Scenario 7: incidental leading whitespace before the keyword is tolerated. */
    @Test
    fun `tolerates leading whitespace before the keyword`() {
        val result = KeywordPrefixParser.parse("  Manuel, hola")

        assertEquals("hola", result)
    }
}
