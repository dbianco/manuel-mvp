package com.manuel.mvp.rag

/**
 * A thin raw-SQL-execution seam that [FragmentSearcher] runs its queries through, instead of
 * depending directly on Android's `SupportSQLiteDatabase`.
 *
 * This exists so `FragmentSearcher`'s real search/ranking logic can be exercised in a plain JVM
 * unit test (`FragmentSearcherTest`, T005) against a real FTS5-capable SQLite engine
 * (`org.xerial:sqlite-jdbc`) — Robolectric's bundled SQLite engine does not support the FTS5
 * module (see `FragmentSearcherTest`'s KDoc for the full investigation). Production code
 * ([ContentDao]) implements this against `androidx.sqlite.db.SupportSQLiteDatabase`; both are real
 * SQLite, so the SQL text [FragmentSearcher] builds is identical either way.
 */
fun interface FragmentRowSource {
    /**
     * Executes [sql] with the given positional [args] bound in order, and returns every result
     * row as a map from column label to its string value (or `null`).
     */
    fun rawQuery(sql: String, args: List<Any?>): List<Map<String, String?>>
}
