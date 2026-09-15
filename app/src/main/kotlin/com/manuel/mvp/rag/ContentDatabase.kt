package com.manuel.mvp.rag

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.json.JSONArray

/**
 * SQLite database helper for the preloaded lesson-content `fragments` FTS5 table.
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
 * `PRAGMA user_version`, replicating what `SupportSQLiteOpenHelper.Callback`'s
 * `onCreate`/`onUpgrade`/`onDowngrade` did before.
 *
 * The `fragments` table schema (columns, FTS5 tokenizer) intentionally matches
 * `FragmentSearcherTest`'s (T005) fixture exactly, byte-for-byte, since [FragmentSearcher]'s query
 * shape assumes it.
 */
class ContentDatabase private constructor(val connection: SQLiteConnection) : AutoCloseable {

    /** Closes the underlying database connection. */
    override fun close() = connection.close()

    companion object {
        private const val DATABASE_NAME = "manuel_content.db"
        private const val DATABASE_VERSION = 1
        private const val CONTENT_ASSET_PATH = "content/matematica_lecciones.json"

        private const val CREATE_FRAGMENTS_TABLE_SQL =
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

        private const val INSERT_FRAGMENT_SQL =
            "INSERT INTO fragments (id, area, nivel, leccion, tema, texto) VALUES (?, ?, ?, ?, ?, ?)"

        /**
         * Opens (creating and seeding on first run, or re-seeding on a schema downgrade) a
         * [ContentDatabase] backed by the bundled, FTS5-capable SQLite. [context] is used both to
         * resolve the on-disk database file path ([Context.getDatabasePath]) and, on first
         * creation or downgrade, to read the preloaded content asset via [Context.getAssets].
         */
        fun create(context: Context): ContentDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            dbFile.parentFile?.mkdirs()

            val connection = BundledSQLiteDriver().open(dbFile.path)
            val currentVersion = readUserVersion(connection)

            when {
                currentVersion == 0 -> {
                    createSchemaAndSeed(connection, context)
                    writeUserVersion(connection, DATABASE_VERSION)
                }
                currentVersion > DATABASE_VERSION -> {
                    // This table holds only preloaded, re-derivable lesson content (no user
                    // data), so downgrading is safe to implement as "drop and recreate, then
                    // re-seed from the bundled JSON asset" rather than failing.
                    connection.execSQL("DROP TABLE IF EXISTS fragments")
                    createSchemaAndSeed(connection, context)
                    writeUserVersion(connection, DATABASE_VERSION)
                }
                currentVersion < DATABASE_VERSION -> {
                    // No schema upgrades exist yet -- version 1 is the only version defined so
                    // far. Future upgrades should add real migration steps here.
                }
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
            connection.execSQL(CREATE_FRAGMENTS_TABLE_SQL)
            seedFragments(connection, context)
        }

        private fun seedFragments(connection: SQLiteConnection, context: Context) {
            val json =
                context.assets.open(CONTENT_ASSET_PATH).use { input ->
                    input.reader(Charsets.UTF_8).readText()
                }
            val fragments = JSONArray(json)
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
    }
}
