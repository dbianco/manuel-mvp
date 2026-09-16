package com.manuel.mvp.rag

/**
 * Full-text search over the curated question/answer pairs in the `respuestas` FTS5 table that
 * [ContentDatabase] seeds from `content/preguntas_respuestas.json` -- the MVP's first-choice
 * source for a spoken answer (see `ConversationPipeline`'s doc comment for the full lookup
 * order). A transcribed question is matched against each entry's `tema`, canonical `pregunta`,
 * and alternate `variantes` phrasings, never against the answer text itself.
 *
 * Matching happens in two steps. FTS5 (`bm25`-ranked OR-of-content-words, see [Fts5Query])
 * produces a shortlist of candidates; then, in Kotlin, the query's content words are compared
 * against each candidate's own phrasings:
 *
 * 1. A candidate one of whose phrasings has *exactly* the query's content words wins outright.
 *    This is what lets a short definitional question ("¿Qué es sumar?" -> just "sumar") reach
 *    the "¿Qué es sumar?" entry instead of whichever entry repeats "sumar" across the most
 *    `variantes` -- with 129 entries sharing a small vocabulary, bm25 alone got that wrong.
 * 2. Otherwise candidates keep their bm25 order, but only if they share at least
 *    [MIN_SHARED_WORDS] content words with the query. A single word in common ("hora" in
 *    "¿qué hora es?" vs. the clock-reading entry, "sistema" in "sistema solar" vs. the decimal
 *    system entry) is not evidence of the same question, and FR-008 prefers "no tengo
 *    información" over a confident wrong answer.
 *
 * Reuses the [FragmentRowSource] raw-SQL seam (despite the name, it's just "run this SQL, give
 * me rows") for the same reason [FragmentSearcher] does: it lets `CannedAnswersTest` exercise
 * this exact query/ranking logic against the real JSON asset, loaded into a real FTS5 engine via
 * `org.xerial:sqlite-jdbc`, with no Android involved.
 */
class AnswerSearcher(private val source: FragmentRowSource) {

    /**
     * Returns up to [limit] [CannedAnswer]s matching [query], best match first, or an empty list
     * if nothing matches (including when [query] has no content words at all -- see
     * [Fts5Query.build]).
     */
    fun search(query: String, limit: Int = 1): List<CannedAnswer> {
        val match = Fts5Query.build(query) ?: return emptyList()
        val queryWords = Fts5Query.contentWords(query)

        val candidates = source.rawQuery(SEARCH_SQL, listOf(match, CANDIDATE_LIMIT)).map { row ->
            val tema = row.getValue("tema").orEmpty()
            val pregunta = row.getValue("pregunta").orEmpty()
            val variantes = row.getValue("variantes").orEmpty().split(VARIANTES_SEPARATOR)
            Candidate(
                answer =
                    CannedAnswer(
                        id = row.getValue("id").orEmpty(),
                        tema = tema,
                        pregunta = pregunta,
                        respuesta = row.getValue("respuesta").orEmpty(),
                    ),
                tema = Fts5Query.contentWords(tema),
                pregunta = Fts5Query.contentWords(pregunta),
                variantes = variantes.map(Fts5Query::contentWords),
            )
        }

        // Among exact matches, an entry whose canonical `pregunta` is the match beats one where
        // only a `variante` is: "¿Qué es multiplicar?" is qa-16's own question, while qa-57
        // ("¿Cuándo conviene multiplicar...?") merely lists "para qué sirve multiplicar" as a
        // variante that reduces to the same single word. `sortedBy` is stable, so bm25 order
        // still breaks the remaining ties.
        val exact =
            candidates
                .filter { candidate -> candidate.exactMatchRank(queryWords) != null }
                .sortedBy { candidate -> candidate.exactMatchRank(queryWords) }
        val related =
            candidates.filter { candidate ->
                candidate !in exact && candidate.allWords.intersect(queryWords).size >= MIN_SHARED_WORDS
            }
        return (exact + related).take(limit).map { it.answer }
    }

    private class Candidate(
        val answer: CannedAnswer,
        private val tema: Set<String>,
        private val pregunta: Set<String>,
        private val variantes: List<Set<String>>,
    ) {
        val allWords: Set<String> = (listOf(tema, pregunta) + variantes).flatten().toSet()

        /** 0 if [queryWords] is exactly the `pregunta`, 1 if the `tema`, 2 if a `variante`; null if none. */
        fun exactMatchRank(queryWords: Set<String>): Int? =
            when {
                pregunta == queryWords -> 0
                tema == queryWords -> 1
                variantes.any { it == queryWords } -> 2
                else -> null
            }
    }

    private companion object {
        /** How [ContentDatabase] joins an entry's `variantes` into the single indexed column. */
        const val VARIANTES_SEPARATOR = " | "

        /** How many bm25-ranked rows to re-rank in Kotlin; the exact-phrasing winner is always near the top. */
        const val CANDIDATE_LIMIT = 10

        /** Minimum content words a non-exact candidate must share with the query to count as a match. */
        const val MIN_SHARED_WORDS = 2

        /**
         * `bm25()`'s trailing arguments are per-column weights in the table's declared column
         * order (`id`, `tema`, `pregunta`, `variantes`, `respuesta`, `fragmentos`). The canonical
         * `pregunta` is weighted 3x so that a question matching an entry's *own* phrasing ranks
         * above an entry that merely repeats the same words across many `variantes`. Lower bm25 =
         * better match, so plain ascending order already puts the best first.
         */
        val SEARCH_SQL =
            """
            SELECT id, tema, pregunta, variantes, respuesta
            FROM respuestas
            WHERE respuestas MATCH ?
            ORDER BY bm25(respuestas, 0.0, 1.0, 3.0, 1.0, 0.0, 0.0)
            LIMIT ?
            """
                .trimIndent()
    }
}
