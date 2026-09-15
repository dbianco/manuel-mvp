package com.manuel.mvp.rag

/**
 * Full-text search over the curated question/answer pairs in the `respuestas` FTS5 table that
 * [ContentDatabase] seeds from `content/preguntas_respuestas.json` -- the MVP's first-choice
 * source for a spoken answer (see `ConversationPipeline`'s doc comment for the full lookup
 * order). A transcribed question is matched against each entry's `tema`, canonical `pregunta`,
 * and alternate `variantes` phrasings, never against the answer text itself.
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
        return source.rawQuery(SEARCH_SQL, listOf(match, limit)).map { row ->
            CannedAnswer(
                id = row.getValue("id").orEmpty(),
                tema = row.getValue("tema").orEmpty(),
                pregunta = row.getValue("pregunta").orEmpty(),
                respuesta = row.getValue("respuesta").orEmpty(),
            )
        }
    }

    private companion object {
        /**
         * `bm25()`'s trailing arguments are per-column weights in the table's declared column
         * order (`id`, `tema`, `pregunta`, `variantes`, `respuesta`, `fragmentos`). The canonical
         * `pregunta` is weighted 3x so that a question matching an entry's *own* phrasing beats
         * an entry that merely repeats the same words across many `variantes` -- without it,
         * "¿Qué es sumar?" ranked the "sumar con los dedos" entry (four variantes containing
         * "sumar") above the "¿Qué es sumar?" entry itself. Lower bm25 = better match, so plain
         * ascending order already puts the best first.
         */
        val SEARCH_SQL =
            """
            SELECT id, tema, pregunta, respuesta
            FROM respuestas
            WHERE respuestas MATCH ?
            ORDER BY bm25(respuestas, 0.0, 1.0, 3.0, 1.0, 0.0, 0.0)
            LIMIT ?
            """
                .trimIndent()
    }
}
