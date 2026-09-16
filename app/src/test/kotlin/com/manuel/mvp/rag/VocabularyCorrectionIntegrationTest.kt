package com.manuel.mvp.rag

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * End-to-end proof, against the *real* content assets and a real FTS5 engine, that
 * [VocabularyCorrector] plus [AnswerSearcher] together recover from the exact whisper.cpp
 * mis-transcriptions a live phone testing session ran into: a dropped letter ("Córdoba" ->
 * "cordoa"), a b/v confusion ("vértice" -> "bértice"), and a qu/gu confusion ("quién" -> "guién")
 * -- this is what `ConversationPipeline.handleDetection` actually does (correct, then search),
 * unlike `CannedAnswersTest`'s other assertions, which search the raw text directly.
 *
 * [VocabularyCorrectorTest] pins the correction contract itself against a small fixture
 * vocabulary; this test instead builds the vocabulary from the real ~1200-word corpus, where a
 * coincidental collision (an unrelated real word happening to fall within budget of some other
 * real word) would actually show up.
 */
class VocabularyCorrectionIntegrationTest {

    private lateinit var connection: Connection
    private lateinit var searcher: AnswerSearcher
    private lateinit var corrector: VocabularyCorrector

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { it.execute(ContentSchema.CREATE_RESPUESTAS_TABLE_SQL) }
        connection.createStatement().use { it.execute(ContentSchema.CREATE_FRAGMENTS_TABLE_SQL) }
        seedRespuestas()
        seedFragments()

        val source = FragmentRowSource { sql, args ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { it.toRowMaps() }
            }
        }
        searcher = AnswerSearcher(source)
        corrector = VocabularyCorrector(VocabularyCorrector.buildVocabulary(source))
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `a dropped letter in a place name still resolves after correction`() {
        assertResolves(rawTranscript = "quien fundo cordoa", expectedId = "qa-328")
    }

    @Test
    fun `a qu gu confusion in the question word still resolves after correction`() {
        assertResolves(rawTranscript = "guien fundo cordoba", expectedId = "qa-328")
    }

    @Test
    fun `both mistakes together in the same transcript still resolve`() {
        assertResolves(rawTranscript = "guien fundo cordoa", expectedId = "qa-328")
    }

    @Test
    fun `a b v confusion in vertice still resolves after correction`() {
        assertResolves(rawTranscript = "que es un bertice", expectedId = "qa-27")
    }

    @Test
    fun `a different single-letter mishearing of vertice also resolves`() {
        // Caught live on a real phone: whisper heard "mertice", not "bertice" -- this is exactly
        // why the fix must be a real corrector against the vocabulary, not one more hardcoded
        // misspelled variante (which, worse, would have tied with "vertice" for THIS mishearing
        // and blocked correction -- see qa-27's git history).
        assertResolves(rawTranscript = "que es un mertice", expectedId = "qa-27")
    }

    @Test
    fun `an already-correct transcript is unaffected by correction`() {
        assertResolves(rawTranscript = "quien fundo cordoba", expectedId = "qa-328")
    }

    private fun assertResolves(rawTranscript: String, expectedId: String) {
        val corrected = corrector.correct(rawTranscript)
        val actualId = searcher.search(corrected).firstOrNull()?.id
        assertEquals("\"$rawTranscript\" -> corrected \"$corrected\" -> $actualId", expectedId, actualId)
    }

    private fun seedRespuestas() {
        val entries = JSONArray(assetFile("content/preguntas_respuestas.json").readText(Charsets.UTF_8))
        connection
            .prepareStatement(
                "INSERT INTO respuestas (id, tema, pregunta, variantes, respuesta, fragmentos) VALUES (?, ?, ?, ?, ?, ?)"
            )
            .use { statement ->
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    statement.setString(1, entry.getString("id"))
                    statement.setString(2, entry.getString("tema"))
                    statement.setString(3, entry.getString("pregunta"))
                    statement.setString(4, entry.getJSONArray("variantes").strings().joinToString(" | "))
                    statement.setString(5, entry.getString("respuesta"))
                    statement.setString(6, entry.getJSONArray("fragmentos").strings().joinToString(","))
                    statement.executeUpdate()
                }
            }
    }

    private fun seedFragments() {
        val entries = JSONArray(assetFile("content/matematica_lecciones.json").readText(Charsets.UTF_8))
        connection
            .prepareStatement("INSERT INTO fragments (id, area, nivel, leccion, tema, texto) VALUES (?, ?, ?, ?, ?, ?)")
            .use { statement ->
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    statement.setString(1, entry.getString("id"))
                    statement.setString(2, entry.getString("area"))
                    statement.setString(3, entry.getString("nivel"))
                    statement.setString(4, entry.getString("leccion"))
                    statement.setString(5, entry.getString("tema"))
                    statement.setString(6, entry.getString("texto"))
                    statement.executeUpdate()
                }
            }
    }

    private companion object {
        // Gradle runs unit tests with the module directory (app/) as the working directory; the
        // repo-root fallback keeps this runnable from an IDE that uses the project root instead.
        fun assetFile(relativePath: String): File =
            listOf(File("src/main/assets", relativePath), File("app/src/main/assets", relativePath))
                .firstOrNull { it.exists() }
                ?: error("asset not found: $relativePath (cwd=${File(".").absolutePath})")

        fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    }
}

private fun ResultSet.toRowMaps(): List<Map<String, String?>> {
    val columnNames = (1..metaData.columnCount).map { metaData.getColumnLabel(it) }
    val rows = mutableListOf<Map<String, String?>>()
    while (next()) {
        rows += columnNames.associateWith { name -> getString(name) }
    }
    return rows
}
