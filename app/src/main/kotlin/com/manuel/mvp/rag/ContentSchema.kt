package com.manuel.mvp.rag

/**
 * The FTS5 schema for both preloaded-content tables, kept as plain string constants with no
 * Android imports so that [ContentDatabase] (on-device) and the JVM unit tests (`sqlite-jdbc`)
 * create byte-for-byte identical tables and the searchers' query shapes hold in both.
 */
internal object ContentSchema {

    /**
     * Lesson content fragments (`content/matematica_lecciones.json`). Intentionally matches
     * `FragmentSearcherTest`'s (T005) fixture exactly, since [FragmentSearcher]'s query shape
     * assumes it.
     */
    const val CREATE_FRAGMENTS_TABLE_SQL =
        """
        CREATE VIRTUAL TABLE fragments USING fts5(
            id UNINDEXED,
            area,
            nivel,
            leccion,
            tema,
            texto,
            tokenize = 'unicode61 remove_diacritics 2'
        )
        """

    /**
     * Curated question/answer pairs (`content/preguntas_respuestas.json`). Only the columns a
     * spoken question is matched against are indexed -- `tema`, `pregunta` (the canonical
     * phrasing), and `variantes` (alternate phrasings, joined into one text) -- while `respuesta`
     * is deliberately UNINDEXED so the answer's own wording never attracts a match.
     */
    const val CREATE_RESPUESTAS_TABLE_SQL =
        """
        CREATE VIRTUAL TABLE respuestas USING fts5(
            id UNINDEXED,
            tema,
            pregunta,
            variantes,
            respuesta UNINDEXED,
            fragmentos UNINDEXED,
            tokenize = 'unicode61 remove_diacritics 2'
        )
        """
}
