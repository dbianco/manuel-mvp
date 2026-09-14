package com.manuel.mvp.rag

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests defining the exact behavioral contract that T006's real `FragmentSearcher` /
 * `ContentDatabase` / `ContentDao` implementation must satisfy: full-text search over lesson
 * content fragments via SQLite FTS5, ranked by relevance, accent-insensitive (FR-006/RF-03 --
 * real classroom Whisper.cpp transcriptions may or may not preserve Spanish accents).
 *
 * `FragmentSearcher` and `ContentFragment` (see the companion object's fixture below, and the
 * calls in each `@Test`) do not exist in the main source set yet -- they are T006's job, not this
 * task's. This file will not compile until T006 adds them; that is expected for this test-first
 * split (T005 defines the contract via tests, T006 implements it).
 *
 * Test setup: rather than depending on T006's not-yet-existing `ContentDatabase`/`ContentDao`,
 * each test builds its own real, in-memory FTS5-backed SQLite database directly via
 * [FrameworkSQLiteOpenHelperFactory] running under Robolectric, seeds a small hand-crafted fixture
 * of `ContentFragment`-shaped rows (not the real `matematica_lecciones.json` content -- see the
 * task brief), and constructs a real `FragmentSearcher` against that database.
 *
 * IMPORTANT -- known environment limitation found while writing this test (full detail in
 * task-5-report.md): sandbox verification (no real Android SDK/emulator available in this
 * environment; see the T005 task brief) found that Robolectric 4.17's default NATIVE SQLite
 * engine does NOT compile in the FTS5 extension module. `CREATE VIRTUAL TABLE ... USING fts5(...)`
 * fails at runtime with `SQLiteException: no such module: fts5`, reproduced directly against the
 * framework `android.database.sqlite.SQLiteDatabase` class (which `SupportSQLiteDatabase` /
 * `FrameworkSQLiteOpenHelperFactory` is a thin pass-through wrapper around, so the same engine is
 * used either way) at `@Config(sdk = 33)`, `34`, and `35` alike, on this host. Robolectric's
 * LEGACY SQLite mode is not a viable fallback either: its bundled native library ships no aarch64
 * build at all (`UnsupportedOperationException: Architecture 'aarch64' is not supported`), so it
 * cannot run on Apple Silicon regardless of FTS5 support. See
 * https://github.com/robolectric/robolectric/issues/8495 for a related, closed-as-not-planned
 * Robolectric FTS bug report. This looks like a real gap in Robolectric's own native SQLite build,
 * not a mistake in this test's SQL or setup -- but it means this suite, though written against the
 * real, verified androidx.sqlite/Robolectric API surface, may still fail at runtime under
 * Robolectric today even once T006 lands a correct `FragmentSearcher`. T006's implementer should
 * re-verify this against whichever exact Robolectric version is resolved when this actually runs
 * (Robolectric may fix it in a later release); if the gap persists, the practical fallback is
 * exercising this class via an instrumented test on a real device/emulator running API 30+ (where
 * Android's bundled SQLite does support FTS5), rather than relying on this Robolectric suite alone
 * as a CI gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FragmentSearcherTest {

    private lateinit var db: SupportSQLiteDatabase
    private lateinit var searcher: FragmentSearcher

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        val configuration =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory database, fresh per test
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_FRAGMENTS_TABLE_SQL)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            // No-op: every test starts from a fresh in-memory database at version 1.
                        }
                    }
                )
                .build()
        db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        FIXTURE.forEach(::insertFragment)
        searcher = FragmentSearcher(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertFragment(fragment: ContentFragment) {
        db.execSQL(
            "INSERT INTO fragments (id, area, nivel, leccion, tema, texto) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(
                fragment.id,
                fragment.area,
                fragment.nivel,
                fragment.leccion,
                fragment.tema,
                fragment.texto,
            ),
        )
    }

    @Test
    fun `search returns the fragment whose content clearly matches the query`() {
        val results = searcher.search("multiplicación")

        assertEquals(1, results.size)
        assertEquals("mult-01", results[0].id)
    }

    @Test
    fun `search is accent-insensitive, unaccented query matches accented content`() {
        val results = searcher.search("multiplicacion")

        assertEquals(1, results.size)
        assertEquals("mult-01", results[0].id)
        assertTrue(results[0].texto.contains("multiplicación"))
    }

    @Test
    fun `search orders multiple matches by FTS5 relevance, best match first`() {
        val results = searcher.search("división")

        assertEquals(2, results.size)
        // div-02 repeats "división" four times in a short fragment; div-01 mentions it once in a
        // longer sentence. FTS5's bm25-based rank must put the more relevant fragment first.
        assertEquals("div-02", results[0].id)
        assertEquals("div-01", results[1].id)
    }

    @Test
    fun `search respects an explicit limit lower than the number of matches`() {
        // Fixture seeds exactly 6 fragments containing "numero".
        val results = searcher.search("numero", limit = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun `search defaults to a limit of 5 when none is given`() {
        // Same 6 "numero" fragments as above, called without an explicit limit. If the real
        // default were anything other than 5 this assertion would fail (6 if uncapped, some
        // other N if misconfigured).
        val results = searcher.search("numero")

        assertEquals(5, results.size)
    }

    @Test
    fun `search with no matching content returns an empty list`() {
        val results = searcher.search("algebra")

        assertTrue(results.isEmpty())
    }

    private companion object {
        val CREATE_FRAGMENTS_TABLE_SQL =
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
                .trimIndent()

        // Hand-crafted fixture (not the real matematica_lecciones.json content, per the T005
        // brief) covering a few distinct leccion/tema values. "mult-01" is the only fragment
        // containing the accented word "multiplicación", used by both the relevant-match and
        // accent-insensitive-match tests. Exactly six "num-*" fragments contain "numero", used by
        // the limit and default-limit tests.
        val FIXTURE =
            listOf(
                ContentFragment(
                    id = "mult-01",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Multiplicación",
                    tema = "Concepto",
                    texto = "La multiplicación es una suma repetida de una misma cantidad.",
                ),
                ContentFragment(
                    id = "suma-01",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Suma",
                    tema = "Concepto",
                    texto = "La suma junta dos cantidades para formar un total.",
                ),
                ContentFragment(
                    id = "resta-01",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Resta",
                    tema = "Concepto",
                    texto = "La resta quita una cantidad de otra cantidad mayor.",
                ),
                ContentFragment(
                    id = "div-01",
                    area = "matematica",
                    nivel = "intermedio",
                    leccion = "División",
                    tema = "Concepto",
                    texto =
                        "La división ayuda a repartir cantidades de forma distinta entre varios grupos.",
                ),
                ContentFragment(
                    id = "div-02",
                    area = "matematica",
                    nivel = "intermedio",
                    leccion = "División",
                    tema = "Repaso",
                    // Deliberately short and repetitive so its FTS5 relevance for "división" is
                    // unambiguously higher than div-01's -- see the ranking test above.
                    texto = "División división división división.",
                ),
                ContentFragment(
                    id = "geo-01",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Geometría",
                    tema = "Figuras",
                    texto = "El triángulo tiene tres lados y tres vértices.",
                ),
                ContentFragment(
                    id = "num-01",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero uno y su lugar en la fila.",
                ),
                ContentFragment(
                    id = "num-02",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero dos y su lugar en la fila.",
                ),
                ContentFragment(
                    id = "num-03",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero tres y su lugar en la fila.",
                ),
                ContentFragment(
                    id = "num-04",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero cuatro y su lugar en la fila.",
                ),
                ContentFragment(
                    id = "num-05",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero cinco y su lugar en la fila.",
                ),
                ContentFragment(
                    id = "num-06",
                    area = "matematica",
                    nivel = "basico",
                    leccion = "Números",
                    tema = "Repaso",
                    texto = "Repasamos el numero seis y su lugar en la fila.",
                ),
            )
    }
}
