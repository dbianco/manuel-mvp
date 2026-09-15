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
     * FTS5 syntax.
     *
     * Tokens are OR-ed together, not AND-ed (space-separated quoted phrases would default to AND)
     * -- verified on-device: with AND, "¿Cuántos lados tienen triángulo?" failed to match content
     * that says the triángulo "tiene" (not "tienen") 3 lados, because *every* query word has to
     * appear somewhere in the row for AND to match at all, and natural question phrasing rarely
     * shares 100% of its words with the more declarative lesson text. OR ranks fragments by how
     * many/how prominently query words appear (via `bm25`) without requiring all of them, and
     * [STOPWORDS] keeps grammatical scaffolding words ("qué", "cuántos", "tiene") from diluting
     * that ranking or accidentally matching unrelated content that happens to share only those.
     */
    private fun String.toFts5MatchExpression(): String {
        val tokens = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Stopword comparison strips leading/trailing punctuation ("¿cuántos" -> "cuántos") since
        // that's not part of the word itself; the original token (punctuation and all) is still
        // what gets quoted below -- FTS5's own unicode61 tokenizer re-tokenizes text inside a
        // quoted phrase too, so it strips that punctuation on its own before matching.
        val contentTokens = tokens
            .filter { token -> token.trim { !it.isLetter() }.lowercase() !in STOPWORDS }
            .ifEmpty { tokens }
        return contentTokens.joinToString(" OR ") { token -> "\"" + token.replace("\"", "\"\"") + "\"" }
    }



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

        /**
         * Spanish question words, articles, prepositions, and common verb forms that carry little
         * topic-specific meaning on their own -- filtered out of the OR-matched query terms above
         * so they don't dilute ranking or cause spurious matches (e.g. "es" alone appears in almost
         * every fragment's `texto`, since most are phrased "X es Y"). Not exhaustive by design:
         * covers what actually shows up in `manuel-mvp-test-protocol.md`'s 30 test questions plus
         * the most common Spanish function words, not a full stopword list for the language.
         */
        val STOPWORDS = setOf(
            "manuel", "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al",
            "a", "en", "y", "o", "u", "que", "qué", "como", "cómo", "cual", "cuál", "cuales",
            "cuáles", "cuando", "cuándo", "donde", "dónde", "quien", "quién", "quienes", "quiénes",
            "cuanto", "cuánto", "cuanta", "cuánta", "cuantos", "cuántos", "cuantas", "cuántas",
            "es", "son", "esta", "está", "estan", "están", "ser", "hay", "tiene", "tienen",
            "tener", "hace", "hacen", "hacer", "sirve", "sirven", "servir", "puedo", "podes",
            "podés", "puede", "pueden", "se", "me", "te", "le", "les", "su", "sus", "mi", "mis",
            "tu", "tus", "yo", "vos", "nos", "con", "sin", "por", "para", "si", "sí", "no", "ni",
            "pero", "eso", "esto", "esa", "ese", "esas", "esos", "ahora", "explicame", "explícame",
            "dime", "decime", "contame", "cuéntame", "ayudame", "ayúdame", "dame",
        )
    }
}
