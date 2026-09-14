package com.manuel.mvp.rag

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract that T006's real `FragmentSearcher` /
 * `ContentDatabase` / `ContentDao` implementation must satisfy: full-text search over lesson
 * content fragments via SQLite FTS5, ranked by relevance, accent-insensitive (FR-006/RF-03 --
 * real classroom Whisper.cpp transcriptions may or may not preserve Spanish accents).
 *
 * `FragmentSearcher`, `ContentFragment`, and `FragmentRowSource` do not exist in the main source
 * set yet -- they are T006's job, not this task's. This file will not compile until T006 adds
 * them; that is expected for this test-first split (T005 defines the contract via tests, T006
 * implements it).
 *
 * ### Why this doesn't use Robolectric (revised contract, see task-5-report.md)
 *
 * An earlier version of this test built its FTS5 fixture directly against
 * `androidx.sqlite.db.SupportSQLiteDatabase` under Robolectric. Empirical investigation (a real,
 * running standalone Robolectric harness, not just docs-reading) found that Robolectric 4.17's
 * default NATIVE SQLite engine does not compile in the FTS5 module at all (`no such module: fts5`,
 * reproduced identically at `@Config(sdk = 33/34/35)`), and its LEGACY mode has no aarch64 native
 * build, so it cannot run on Apple Silicon either way. See
 * https://github.com/robolectric/robolectric/issues/8495 for a related, closed-as-not-planned
 * Robolectric FTS bug report, and task-5-report.md for the full writeup.
 *
 * Rather than chase a Robolectric configuration that supports FTS5, the API contract was revised
 * so `FragmentSearcher`'s actual search/ranking logic depends only on a thin [FragmentRowSource]
 * raw-SQL-execution seam, not directly on Android's `SupportSQLiteDatabase`. `FragmentSearcher`
 * still owns all the real query-building and ranking logic (the `CREATE VIRTUAL TABLE ... USING
 * fts5(...)` fixture setup below, and the `SELECT ... WHERE fragments MATCH ? ORDER BY
 * bm25(fragments) LIMIT ?`-shaped query `search()` builds and runs via
 * `source.rawQuery(...)`) -- this test exercises that real production logic, just against a
 * [FragmentRowSource] backed by a plain JDBC connection (`org.xerial:sqlite-jdbc`) instead of
 * Android's SQLite. Both are real SQLite, so the FTS5 SQL text itself is identical either way;
 * production code (T006) will implement `FragmentRowSource` against `SupportSQLiteDatabase`
 * instead. `org.xerial:sqlite-jdbc`'s FTS5 support (including the exact `unicode61
 * remove_diacritics 2` tokenizer used below) was verified empirically before adopting this
 * approach -- see task-5-report.md.
 */
class FragmentSearcherTest {

    private lateinit var connection: Connection
    private lateinit var searcher: FragmentSearcher

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { statement -> statement.execute(CREATE_FRAGMENTS_TABLE_SQL) }
        FIXTURE.forEach(::insertFragment)
        searcher =
            FragmentSearcher(
                FragmentRowSource { sql, args ->
                    connection.prepareStatement(sql).use { statement ->
                        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                        statement.executeQuery().use { resultSet -> resultSet.toRowMaps() }
                    }
                }
            )
    }

    @After
    fun tearDown() {
        connection.close()
    }

    private fun insertFragment(fragment: ContentFragment) {
        connection
            .prepareStatement(
                "INSERT INTO fragments (id, area, nivel, leccion, tema, texto) VALUES (?, ?, ?, ?, ?, ?)"
            )
            .use { statement ->
                statement.setString(1, fragment.id)
                statement.setString(2, fragment.area)
                statement.setString(3, fragment.nivel)
                statement.setString(4, fragment.leccion)
                statement.setString(5, fragment.tema)
                statement.setString(6, fragment.texto)
                statement.executeUpdate()
            }
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

/**
 * Maps every row of this [ResultSet] to a `Map<String, String?>` keyed by column label, matching
 * the shape [FragmentRowSource.rawQuery] returns. Test-only plumbing: it has no knowledge of
 * `ContentFragment` or any FragmentSearcher-specific column list -- it just reflects whatever
 * columns the SQL FragmentSearcher builds actually selected.
 */
private fun ResultSet.toRowMaps(): List<Map<String, String?>> {
    val columnCount = metaData.columnCount
    val columnNames = (1..columnCount).map { metaData.getColumnLabel(it) }
    val rows = mutableListOf<Map<String, String?>>()
    while (next()) {
        rows += columnNames.associateWith { name -> getString(name) }
    }
    return rows
}
