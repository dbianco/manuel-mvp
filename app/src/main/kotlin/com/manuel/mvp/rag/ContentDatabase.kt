package com.manuel.mvp.rag

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.json.JSONArray

/**
 * SQLite database helper for the two preloaded-content FTS5 tables: `fragments` (lesson
 * content, searched by [FragmentSearcher]) and `respuestas` (curated question/answer pairs,
 * searched by [AnswerSearcher]). Both schemas live in [ContentSchema].
 *
 * Uses `androidx.sqlite:sqlite-bundled`'s [BundledSQLiteDriver] rather than
 * `androidx.sqlite:sqlite-framework`'s `FrameworkSQLiteOpenHelperFactory` (the original T006
 * choice): the first real on-device run of this app (an Android 37 emulator, `google_apis` image)
 * failed with "no such module: fts5" -- the OS's own bundled SQLite doesn't have FTS5 compiled in,
 * confirming on real hardware the same limitation already known for Robolectric's SQLite (see the
 * related constraint memory). `sqlite-bundled` ships its own recent SQLite build with FTS5
 * compiled in, independent of the OS version.
 *
 * This driver API has no built-in open-helper/version-callback lifecycle (unlike
 * `SupportSQLiteOpenHelper`), so schema creation/versioning is done by hand here via
 * `PRAGMA user_version`. Every table holds only preloaded, re-derivable content (no user data),
 * so *any* version mismatch -- upgrade or downgrade -- is handled the same simple way: drop
 * everything and re-seed from the bundled JSON assets.
 */
class ContentDatabase private constructor(val connection: SQLiteConnection) : AutoCloseable {

    /** Closes the underlying database connection. */
    override fun close() = connection.close()

    companion object {
        private const val DATABASE_NAME = "manuel_content.db"

        // Version history: 1 = `fragments` only; 2 = added `respuestas`.
        private const val DATABASE_VERSION = 2

        private const val FRAGMENTS_ASSET_PATH = "content/matematica_lecciones.json"
        private const val ANSWERS_ASSET_PATH = "content/preguntas_respuestas.json"

        private const val INSERT_FRAGMENT_SQL =
            "INSERT INTO fragments (id, area, nivel, leccion, tema, texto) VALUES (?, ?, ?, ?, ?, ?)"

        private const val INSERT_ANSWER_SQL =
            "INSERT INTO respuestas (id, tema, pregunta, variantes, respuesta, fragmentos) VALUES (?, ?, ?, ?, ?, ?)"

        /** Separator used to fold a JSON entry's `variantes` list into one indexed text column. */
        private const val VARIANTES_SEPARATOR = " | "

        /**
         * Opens (creating and seeding on first run, or dropping and re-seeding on any schema
         * version mismatch) a [ContentDatabase] backed by the bundled, FTS5-capable SQLite.
         * [context] is used both to resolve the on-disk database file path
         * ([Context.getDatabasePath]) and, when seeding, to read the content assets via
         * [Context.getAssets].
         */
        fun create(context: Context): ContentDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            dbFile.parentFile?.mkdirs()

            val connection = BundledSQLiteDriver().open(dbFile.path)
            if (readUserVersion(connection) != DATABASE_VERSION) {
                connection.execSQL("DROP TABLE IF EXISTS fragments")
                connection.execSQL("DROP TABLE IF EXISTS respuestas")
                createSchemaAndSeed(connection, context)
                writeUserVersion(connection, DATABASE_VERSION)
            }

            return ContentDatabase(connection)
        }

        private fun readUserVersion(connection: SQLiteConnection): Int =
            connection.prepare("PRAGMA user_version").use { statement ->
                if (statement.step()) statement.getLong(0).toInt() else 0
            }

        private fun writeUserVersion(connection: SQLiteConnection, version: Int) {
            connection.execSQL("PRAGMA user_version = $version")
        }

        private fun createSchemaAndSeed(connection: SQLiteConnection, context: Context) {
            connection.execSQL(ContentSchema.CREATE_FRAGMENTS_TABLE_SQL)
            connection.execSQL(ContentSchema.CREATE_RESPUESTAS_TABLE_SQL)
            seedFragments(connection, readAssetArray(context, FRAGMENTS_ASSET_PATH))
            seedAnswers(connection, readAssetArray(context, ANSWERS_ASSET_PATH))
        }

        private fun readAssetArray(context: Context, assetPath: String): JSONArray =
            JSONArray(context.assets.open(assetPath).use { it.reader(Charsets.UTF_8).readText() })

        private fun seedFragments(connection: SQLiteConnection, fragments: JSONArray) {
            connection.prepare(INSERT_FRAGMENT_SQL).use { statement ->
                for (i in 0 until fragments.length()) {
                    val fragment = fragments.getJSONObject(i)
                    statement.bindText(1, fragment.getString("id"))
                    statement.bindText(2, fragment.getString("area"))
                    statement.bindText(3, fragment.getString("nivel"))
                    statement.bindText(4, fragment.getString("leccion"))
                    statement.bindText(5, fragment.getString("tema"))
                    statement.bindText(6, fragment.getString("texto"))
                    statement.step()
                    statement.reset()
                }
            }
        }

        private fun seedAnswers(connection: SQLiteConnection, answers: JSONArray) {
            connection.prepare(INSERT_ANSWER_SQL).use { statement ->
                for (i in 0 until answers.length()) {
                    val answer = answers.getJSONObject(i)
                    statement.bindText(1, answer.getString("id"))
                    statement.bindText(2, answer.getString("tema"))
                    statement.bindText(3, answer.getString("pregunta"))
                    statement.bindText(4, answer.getJSONArray("variantes").joinStrings(VARIANTES_SEPARATOR))
                    statement.bindText(5, answer.getString("respuesta"))
                    statement.bindText(6, answer.getJSONArray("fragmentos").joinStrings(","))
                    statement.step()
                    statement.reset()
                }
            }
        }

        private fun JSONArray.joinStrings(separator: String): String =
            (0 until length()).joinToString(separator) { getString(it) }
    }
}
