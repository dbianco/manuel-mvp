package com.manuel.mvp.llm

import com.manuel.mvp.rag.ContentFragment
import com.manuel.mvp.session.Exchange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract that T015's real `PromptBuilder`
 * implementation must satisfy: assemble the current turn's instruction, the current turn's RAG
 * fragments ([ContentFragment], T006), and the session's recent history ([Exchange], T008) into a
 * single prompt string for the local LLM, using exclusively those three inputs (FR-007) and always
 * embedding the app's response rules (FR-008), with the current turn's content structurally
 * favored over history (see the Clarifications below on how "priority" is operationalized here).
 *
 * `PromptBuilder` does not exist in the main source set yet -- it is T015's job, not this task's
 * (T014). This file will not compile until T015 adds it; that is expected for this test-first
 * split (T014 defines the contract via tests, T015 implements it), same pattern as T005 -> T006,
 * T007 -> T008, T009 -> T010, and T012 -> T013.
 *
 * ### Priority is operationalized as position, not runtime conflict resolution
 *
 * FR-007's "prioritize the current turn's fragment over history when they conflict" describes a
 * *generation-time* LLM behavior that `PromptBuilder` cannot itself enforce -- it has no way to
 * detect or resolve a semantic conflict between two pieces of text. What `PromptBuilder` does
 * control is *where* each input sits in the assembled prompt: placing history first and
 * current-turn content (fragments, then the instruction) last is the same "recency = higher
 * priority" convention widely used in LLM prompting. [`current turn content is positioned after
 * session history`][currentTurnContentIsPositionedAfterSessionHistory] tests that ordering, not
 * the LLM's eventual behavior (a T022 field-test concern).
 *
 * ### `SYSTEM_INSTRUCTIONS` as a named constant, not literal prose here
 *
 * Pinning an exact Spanish sentence in this test would couple it to wording that's really an
 * implementation/prompt-engineering choice. Instead, these tests assert that a non-blank, named
 * `PromptBuilder.SYSTEM_INSTRUCTIONS` constant exists and is embedded in every built prompt --
 * enforcing "there is a real rules section, always included" without dictating its exact phrasing.
 */
class PromptBuilderTest {

    private val builder = PromptBuilder()

    /** Scenario 1: the current instruction appears verbatim in the built prompt. */
    @Test
    fun `includes the current instruction verbatim`() {
        val prompt = builder.build(
            currentInstruction = "¿Cuánto es tres por cuatro?",
            ragFragments = emptyList(),
            sessionHistory = emptyList(),
        )

        assertTrue(prompt.contains("¿Cuánto es tres por cuatro?"))
    }

    /** Scenario 2: every RAG fragment's texto appears in the built prompt. */
    @Test
    fun `includes every RAG fragment's texto`() {
        val fragments = listOf(
            fragment(id = "f1", texto = "Multiplicar es sumar un número varias veces."),
            fragment(id = "f2", texto = "3 x 4 es lo mismo que sumar 3 cuatro veces."),
        )

        val prompt = builder.build(
            currentInstruction = "explicame la multiplicación",
            ragFragments = fragments,
            sessionHistory = emptyList(),
        )

        assertTrue(prompt.contains("Multiplicar es sumar un número varias veces."))
        assertTrue(prompt.contains("3 x 4 es lo mismo que sumar 3 cuatro veces."))
    }

    /** Scenario 3: every session history exchange's question and answer appear in the built prompt. */
    @Test
    fun `includes every session history exchange`() {
        val history = listOf(
            Exchange(question = "¿qué es una fracción?", answer = "Es una parte de un todo."),
            Exchange(question = "dame un ejemplo", answer = "Media pizza es una fracción: 1 sobre 2."),
        )

        val prompt = builder.build(
            currentInstruction = "otro ejemplo por favor",
            ragFragments = emptyList(),
            sessionHistory = history,
        )

        assertTrue(prompt.contains("¿qué es una fracción?"))
        assertTrue(prompt.contains("Es una parte de un todo."))
        assertTrue(prompt.contains("dame un ejemplo"))
        assertTrue(prompt.contains("Media pizza es una fracción: 1 sobre 2."))
    }

    /** Scenario 4: an empty RAG fragment list doesn't fail and doesn't fabricate fragment content. */
    @Test
    fun `builds successfully with no RAG fragments`() {
        val prompt = builder.build(
            currentInstruction = "hola Manuel",
            ragFragments = emptyList(),
            sessionHistory = listOf(Exchange(question = "q", answer = "a")),
        )

        assertTrue(prompt.contains("hola Manuel"))
        assertTrue(prompt.contains("q"))
        assertTrue(prompt.contains("a"))
    }

    /** Scenario 5: an empty session history (first turn) doesn't fail and doesn't fabricate history content. */
    @Test
    fun `builds successfully with no session history`() {
        val prompt = builder.build(
            currentInstruction = "hola Manuel",
            ragFragments = listOf(fragment(id = "f1", texto = "contenido relevante")),
            sessionHistory = emptyList(),
        )

        assertTrue(prompt.contains("hola Manuel"))
        assertTrue(prompt.contains("contenido relevante"))
    }

    /**
     * Scenario 6: current-turn content (a fragment's texto and the current instruction) is
     * positioned after session history in the assembled prompt -- the structural proxy for
     * FR-007's priority rule (see this file's class doc).
     */
    @Test
    fun `current turn content is positioned after session history`() {
        val historyAnswer = "respuesta anterior del historial"
        val currentFragmentText = "fragmento del turno actual"
        val currentInstruction = "instrucción del turno actual"

        val prompt = builder.build(
            currentInstruction = currentInstruction,
            ragFragments = listOf(fragment(id = "f1", texto = currentFragmentText)),
            sessionHistory = listOf(Exchange(question = "pregunta anterior", answer = historyAnswer)),
        )

        val historyIndex = prompt.indexOf(historyAnswer)
        val fragmentIndex = prompt.indexOf(currentFragmentText)
        val instructionIndex = prompt.indexOf(currentInstruction)

        assertTrue(historyIndex >= 0 && fragmentIndex >= 0 && instructionIndex >= 0)
        assertTrue(historyIndex < fragmentIndex)
        assertTrue(fragmentIndex < instructionIndex)
    }

    /** Scenario 7: PromptBuilder.SYSTEM_INSTRUCTIONS is non-blank and embedded in every built prompt. */
    @Test
    fun `embeds the non-blank SYSTEM_INSTRUCTIONS constant`() {
        assertFalse(PromptBuilder.SYSTEM_INSTRUCTIONS.isBlank())

        val prompt = builder.build(
            currentInstruction = "cualquier instrucción",
            ragFragments = emptyList(),
            sessionHistory = emptyList(),
        )

        assertTrue(prompt.contains(PromptBuilder.SYSTEM_INSTRUCTIONS))
    }

    private fun fragment(id: String, texto: String): ContentFragment =
        ContentFragment(id = id, area = "Matemática", nivel = "Primario", leccion = "Lección 1", tema = "tema", texto = texto)
}
