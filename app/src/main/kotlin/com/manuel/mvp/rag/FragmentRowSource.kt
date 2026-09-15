package com.manuel.mvp.rag

/**
 * A thin raw-SQL-execution seam that [FragmentSearcher] runs its queries through, instead of
 * depending directly on an Android SQLite API.
 *
 * This exists so `FragmentSearcher`'s real search/ranking logic can be exercised in a plain JVM
 * unit test (`FragmentSearcherTest`, T005) against a real FTS5-capable SQLite engine
 * (`org.xerial:sqlite-jdbc`) — Robolectric's bundled SQLite engine does not support the FTS5
 * module (see `FragmentSearcherTest`'s KDoc for the full investigation), and — discovered later,
 * on a real device — neither does stock Android's own OS-bundled SQLite. Production code
 * ([ContentDao]) implements this against `androidx.sqlite.SQLiteConnection`
 * (`androidx.sqlite:sqlite-bundled`'s `BundledSQLiteDriver`, which ships its own FTS5-capable
 * SQLite build rather than relying on the OS's); all three are real SQLite engines, so the SQL
 * text [FragmentSearcher] builds is identical across them.
 */
fun interface FragmentRowSource {
    /**
     * Executes [sql] with the given positional [args] bound in order, and returns every result
     * row as a map from column label to its string value (or `null`).
     */
    fun rawQuery(sql: String, args: List<Any?>): List<Map<String, String?>>
}
