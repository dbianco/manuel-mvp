package com.manuel.mvp.llm

import com.manuel.mvp.rag.ContentFragment
import com.manuel.mvp.session.Exchange

/**
 * Assembles the current turn's instruction, its RAG fragments ([ContentFragment], T006), and the
 * session's recent history ([Exchange], T008) into a single prompt for [LlamaEngine], using
 * exclusively those three inputs (FR-007) and always leading with [SYSTEM_INSTRUCTIONS] (FR-008).
 * See `PromptBuilderTest` (T014) for the full behavioral contract this satisfies exactly.
 *
 * Section order -- history, then RAG fragments, then the current instruction -- is deliberate: it
 * places the current turn's content last (closest to where the LLM continues generating), the same
 * "recency = higher priority" convention widely used in LLM prompting. This is how FR-007's
 * "prioritize the current turn over history when they conflict" is operationalized here: a real
 * conflict-resolution decision is the LLM's generation-time job, not something this class can
 * itself detect or resolve -- see `t014-prompt-builder-spec.md`'s Clarifications.
 */
class PromptBuilder {

    fun build(
        currentInstruction: String,
        ragFragments: List<ContentFragment>,
        sessionHistory: List<Exchange>,
    ): String {
        val prompt = StringBuilder()
        prompt.append(SYSTEM_INSTRUCTIONS).append("\n\n")

        prompt.append("Historial reciente:\n")
        for (exchange in sessionHistory) {
            prompt.append("Pregunta: ").append(exchange.question).append('\n')
            prompt.append("Respuesta: ").append(exchange.answer).append('\n')
        }
        prompt.append('\n')

        prompt.append("Contenido relevante para esta pregunta:\n")
        for (fragment in ragFragments) {
            prompt.append("- ").append(fragment.texto).append('\n')
        }
        prompt.append('\n')

        prompt.append("Pregunta actual: ").append(currentInstruction)

        return prompt.toString()
    }

    companion object {
        /**
         * Substantively covers FR-008 (Spanish, 2-4 sentences, simple language appropriate for
         * initial/primary school level, explicit about insufficient information instead of
         * inventing an answer) plus a sentence operationalizing FR-007's priority rule for the LLM
         * itself -- placed at the very start of every built prompt (a system-prompt-like
         * position), so the model sees the rules before any content.
         */
        val SYSTEM_INSTRUCTIONS = """
            Sos Manuel, un asistente educativo para chicos y chicas de nivel inicial y primario.
            Respondé siempre en español, con lenguaje simple y apropiado para ese nivel, en 2 a 4
            frases como máximo. Si el contenido relevante y el historial de la conversación se
            contradicen, priorizá siempre el contenido relevante de la pregunta actual por sobre el
            historial. Si no tenés información suficiente para responder con seguridad, decilo
            explícitamente en vez de inventar una respuesta.
        """.trimIndent()
    }
}
