package com.manuel.mvp.rag

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * [FragmentRowSource] implementation backed by a real, FTS5-capable `androidx.sqlite`
 * [SQLiteConnection] (from [ContentDatabase], via `androidx.sqlite:sqlite-bundled`'s
 * `BundledSQLiteDriver`).
 *
 * Deliberately thin: it just runs whatever SQL text/args it is given and maps the resulting
 * statement's rows to the `List<Map<String, String?>>` shape [FragmentRowSource] returns. It has
 * no knowledge of FTS5, `MATCH`, `bm25`, or any other query-building/ranking concern -- all of that
 * lives in [FragmentSearcher], which is the only thing that calls this class.
 */
class ContentDao(private val connection: SQLiteConnection) : FragmentRowSource {

    override fun rawQuery(sql: String, args: List<Any?>): List<Map<String, String?>> {
        connection.prepare(sql).use { statement ->
            args.forEachIndexed { index, arg -> statement.bindArg(index + 1, arg) }
            return statement.toRowMaps()
        }
    }

    private fun SQLiteStatement.bindArg(index: Int, value: Any?) {
        when (value) {
            null -> bindNull(index)
            is String -> bindText(index, value)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Double -> bindDouble(index, value)
            is Float -> bindDouble(index, value.toDouble())
            is ByteArray -> bindBlob(index, value)
            else -> error("Unsupported bind arg type for ContentDao: ${value::class}")
        }
    }

    private fun SQLiteStatement.toRowMaps(): List<Map<String, String?>> {
        val columnNames = (0 until getColumnCount()).map { getColumnName(it) }
        val rows = mutableListOf<Map<String, String?>>()
        while (step()) {
            rows += columnNames.indices.associate { i ->
                columnNames[i] to (if (isNull(i)) null else getText(i))
            }
        }
        return rows
    }
}
