package com.manuel.mvp.rag

/**
 * Full-text search over preloaded lesson content fragments (FR-006/RF-03), backed by the SQLite
 * FTS5 `fragments` virtual table that [ContentDatabase] creates (`tokenize = 'unicode61
 * remove_diacritics 2'`, making search accent-insensitive -- real classroom Whisper.cpp
 * transcriptions may or may not preserve Spanish accents).
 *
 * All query-building and relevance-ranking logic lives here, not in [ContentDao] or
 * [ContentDatabase] -- both of those are thin plumbing around a real
 * `androidx.sqlite.db.SupportSQLiteDatabase`. This class only depends on the [FragmentRowSource]
 * seam, which is what lets `FragmentSearcherTest` (T005) exercise this exact production logic
 * against a plain JDBC SQLite connection instead of needing Android/Robolectric.
 *
 * The query/ranking shape below (`MATCH` for the search predicate, `ORDER BY bm25(fragments)` for
 * relevance) was verified against `org.xerial:sqlite-jdbc` (the same engine
 * `FragmentSearcherTest` uses) prior to landing this file: FTS5's `bm25()` auxiliary function
 * returns lower (more negative) scores for better matches, so plain ascending order -- the SQL
 * default, no `ASC`/`DESC` needed -- already puts the best match first.
 */
class FragmentSearcher(private val source: FragmentRowSource) {

    /**
     * Returns up to [limit] [ContentFragment]s whose content matches [query], best match first, or
     * an empty list if nothing matches.
     */
    fun search(query: String, limit: Int = 5): List<ContentFragment> {
        val rows = source.rawQuery(SEARCH_SQL, listOf(query.toFts5MatchExpression(), limit))
        return rows.map { row -> row.toContentFragment() }
    }

    /**
     * Turns free-form (whisper-transcribed) [query] text into a safe FTS5 `MATCH` operand.
     *
     * [query] is user-controlled: it's whatever the child said, verbatim. FTS5's query syntax
     * treats `-`, `"`, `(`, `)`, and `:` as operators/metacharacters (e.g. a leading `-` negates
     * the next term, `NEAR(...)`/`col:term` change the query shape entirely), so passing it
     * through unescaped lets ordinary spoken input like "no-se" or "hora: la de comer" throw a
     * `SQLITE_ERROR` (or, worse, silently change what's searched) instead of just searching for
     * those words. Splitting into whitespace-separated tokens and quoting each one as an FTS5
     * string literal (doubling embedded `"`) makes every token a literal phrase match, immune to
     * FTS5 syntax -- space-separated quoted phrases keep FTS5's default implicit-AND-of-terms
     * behavior, so plain queries rank the same as before this fix.
     */
    private fun String.toFts5MatchExpression(): String =
        trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { token -> "\"" + token.replace("\"", "\"\"") + "\"" }



    private fun Map<String, String?>.toContentFragment(): ContentFragment =
        ContentFragment(
            id = getValue("id").orEmpty(),
            area = getValue("area").orEmpty(),
            nivel = getValue("nivel").orEmpty(),
            leccion = getValue("leccion").orEmpty(),
            tema = getValue("tema").orEmpty(),
            texto = getValue("texto").orEmpty(),
        )

    private companion object {
        /**
         * `id` is UNINDEXED in the `fragments` FTS5 schema (see [ContentDatabase]), so it is not
         * itself searchable, but it -- and every other column -- can still be selected out of a
         * matching row.
         */
        val SEARCH_SQL =
            """
            SELECT id, area, nivel, leccion, tema, texto
            FROM fragments
            WHERE fragments MATCH ?
            ORDER BY bm25(fragments)
            LIMIT ?
            """
                .trimIndent()
    }
}
