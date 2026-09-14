package com.manuel.mvp.rag

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONArray

/**
 * SQLite database helper for the preloaded lesson-content `fragments` FTS5 table.
 *
 * Wraps a real `androidx.sqlite.db.SupportSQLiteOpenHelper` (via
 * `FrameworkSQLiteOpenHelperFactory`, the standard framework-backed implementation) rather than
 * `android.database.sqlite.SQLiteOpenHelper` directly, since that is the API [ContentDao] speaks
 * ([androidx.sqlite.db.SupportSQLiteDatabase]).
 *
 * The `fragments` table schema (columns, FTS5 tokenizer) intentionally matches
 * `FragmentSearcherTest`'s (T005) fixture exactly, byte-for-byte, since [FragmentSearcher]'s query
 * shape assumes it.
 */
class ContentDatabase private constructor(private val openHelper: SupportSQLiteOpenHelper) {

    /** A writable handle onto the underlying database, opening/creating/seeding it if needed. */
    val writableDatabase: SupportSQLiteDatabase
        get() = openHelper.writableDatabase

    /** A read-only handle onto the underlying database, opening/creating/seeding it if needed. */
    val readableDatabase: SupportSQLiteDatabase
        get() = openHelper.readableDatabase

    /** Closes the underlying database connection. */
    fun close() = openHelper.close()

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
         * Builds a [ContentDatabase]. [context] is used both by the underlying
         * `SupportSQLiteOpenHelper` (to locate/create the on-disk database file) and, on first
         * creation or downgrade, to read the preloaded content asset via [Context.getAssets].
         */
        fun create(context: Context): ContentDatabase {
            val appContext = context.applicationContext
            val callback =
                object : SupportSQLiteOpenHelper.Callback(DATABASE_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchemaAndSeed(db, appContext)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // No schema upgrades exist yet -- version 1 is the only version defined so
                        // far. Future upgrades should add real migration steps here rather than
                        // falling through to the default (throwing) behavior.
                    }

                    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // This table holds only preloaded, re-derivable lesson content (no user
                        // data), so downgrading is safe to implement as "drop and recreate, then
                        // re-seed from the bundled JSON asset" rather than leaving the default
                        // (throwing) behavior in place.
                        db.execSQL("DROP TABLE IF EXISTS fragments")
                        createSchemaAndSeed(db, appContext)
                    }
                }
            val configuration =
                SupportSQLiteOpenHelper.Configuration.builder(appContext)
                    .name(DATABASE_NAME)
                    .callback(callback)
                    .build()
            val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
            return ContentDatabase(openHelper)
        }

        private fun createSchemaAndSeed(db: SupportSQLiteDatabase, context: Context) {
            db.execSQL(CREATE_FRAGMENTS_TABLE_SQL)
            seedFragments(db, context)
        }

        private fun seedFragments(db: SupportSQLiteDatabase, context: Context) {
            val json =
                context.assets.open(CONTENT_ASSET_PATH).use { input ->
                    input.reader(Charsets.UTF_8).readText()
                }
            val fragments = JSONArray(json)
            for (i in 0 until fragments.length()) {
                val fragment = fragments.getJSONObject(i)
                db.execSQL(
                    INSERT_FRAGMENT_SQL,
                    arrayOf(
                        fragment.getString("id"),
                        fragment.getString("area"),
                        fragment.getString("nivel"),
                        fragment.getString("leccion"),
                        fragment.getString("tema"),
                        fragment.getString("texto"),
                    ),
                )
            }
        }
    }
}
