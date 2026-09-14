package com.manuel.mvp.rag

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * [FragmentRowSource] implementation backed by a real Android `SupportSQLiteDatabase` (obtained
 * from [ContentDatabase]).
 *
 * Deliberately thin: it just runs whatever SQL text/args it is given and maps the resulting
 * `Cursor` to the `List<Map<String, String?>>` shape [FragmentRowSource] returns. It has no
 * knowledge of FTS5, `MATCH`, `bm25`, or any other query-building/ranking concern -- all of that
 * lives in [FragmentSearcher], which is the only thing that calls this class.
 */
class ContentDao(private val database: SupportSQLiteDatabase) : FragmentRowSource {

    override fun rawQuery(sql: String, args: List<Any?>): List<Map<String, String?>> {
        val query = SimpleSQLiteQuery(sql, args.toTypedArray())
        database.query(query).use { cursor -> return cursor.toRowMaps() }
    }

    private fun Cursor.toRowMaps(): List<Map<String, String?>> {
        val columnNames = columnNames.toList()
        val rows = mutableListOf<Map<String, String?>>()
        while (moveToNext()) {
            rows += columnNames.associateWith { name -> getString(getColumnIndexOrThrow(name)) }
        }
        return rows
    }
}
