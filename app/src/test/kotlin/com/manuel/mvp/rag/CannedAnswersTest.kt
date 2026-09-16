package com.manuel.mvp.rag

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validates the *real* `content/preguntas_respuestas.json` asset, both as data (30 well-formed,
 * speech-friendly entries) and as behavior: loaded into a real FTS5 engine
 * (`org.xerial:sqlite-jdbc`, the same engine `FragmentSearcherTest` uses) with the exact
 * [ContentSchema] the app creates on-device, the questions from
 * `docs/superpowers/specs/manuel-mvp-test-protocol.md` must resolve to the intended answers
 * through the production [AnswerSearcher] -- and the protocol's out-of-scope and ambiguous
 * questions must resolve to nothing, since "no match" is what produces the "no tengo
 * información" response.
 *
 * The transcriptions used below are, where noted, ones whisper.cpp actually produced on a real
 * phone during development (digits instead of number words, a b/v confusion in "vértice"), so
 * this also guards the matching against realistic STT output rather than idealized text.
 */
class CannedAnswersTest {

    private lateinit var connection: Connection
    private lateinit var searcher: AnswerSearcher
    private lateinit var entries: JSONArray

    @Before
    fun setUp() {
        entries = JSONArray(assetFile().readText(Charsets.UTF_8))

        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { it.execute(ContentSchema.CREATE_RESPUESTAS_TABLE_SQL) }
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

        searcher =
            AnswerSearcher(
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

    @Test
    fun `asset has exactly 137 well-formed entries with unique ids`() {
        // 30 from the original inicial/primario lessons + 99 from the 3rd/4th-grade guide (110
        // questions minus the 11 that duplicated ones already in the first set) + 8 follow-up
        // entries (angles, aristas/caras, poliedros, cuerpos redondos, cuadrilátero, mitad,
        // comparing fractions) grounded in guide answers that already mentioned those terms.
        assertEquals(137, entries.length())

        val ids = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val id = entry.getString("id")
            assertTrue("duplicate id $id", ids.add(id))
            assertTrue("$id: blank tema", entry.getString("tema").isNotBlank())
            assertTrue("$id: blank pregunta", entry.getString("pregunta").isNotBlank())
            assertTrue("$id: blank respuesta", entry.getString("respuesta").isNotBlank())
            assertTrue("$id: needs at least one variante", entry.getJSONArray("variantes").length() > 0)
            assertTrue("$id: needs at least one grounding fragmento", entry.getJSONArray("fragmentos").length() > 0)
        }
    }

    @Test
    fun `every answer is grounded in a real lesson fragment or guide question`() {
        // Two kinds of source: a fragment id from matematica_lecciones.json, or "guia34-NN" for
        // question NN of docs/content/preguntas-matematica-3-4-grado.md (whose answers were
        // rewritten for speech but not changed in substance).
        val fragmentIds = JSONArray(lessonsFile().readText(Charsets.UTF_8)).let { lessons ->
            (0 until lessons.length()).map { lessons.getJSONObject(it).getString("id") }.toSet()
        }
        val guideQuestionNumbers =
            Regex("^### Pregunta (\\d+):", RegexOption.MULTILINE)
                .findAll(guideFile().readText(Charsets.UTF_8))
                .map { it.groupValues[1] }
                .toSet()
        assertEquals(110, guideQuestionNumbers.size)

        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            for (source in entry.getJSONArray("fragmentos").strings()) {
                val guideNumber = Regex("guia34-(\\d+)").matchEntire(source)?.groupValues?.get(1)
                val known = if (guideNumber != null) guideNumber in guideQuestionNumbers else source in fragmentIds
                assertTrue("${entry.getString("id")} cites unknown source $source", known)
            }
        }
    }

    @Test
    fun `every entry's own question resolves to that entry`() {
        // The strongest collision check available without a phone: with 129 entries sharing a
        // vocabulary (sumar, restar, dividir, cero, cuarto...), each canonical question must still
        // rank its own entry first. When this fails, fix it in the content (a more specific
        // `pregunta`, or extra `variantes`), not by special-casing the search.
        val failures = (0 until entries.length()).mapNotNull { i ->
            val entry = entries.getJSONObject(i)
            val expectedId = entry.getString("id")
            val actualId = searcher.search(entry.getString("pregunta")).firstOrNull()?.id
            if (actualId == expectedId) null else "\"${entry.getString("pregunta")}\" -> $actualId (expected $expectedId)"
        }
        assertTrue("Mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `3rd and 4th grade paraphrases resolve to the intended answers`() {
        val expected =
            listOf(
                "¿Qué es una decena?" to "qa-33",
                "¿Cómo se lee 4528?" to "qa-34",
                "¿Qué es redondear?" to "qa-38",
                "¿Cuánto es 45 más 32?" to "qa-44",
                "¿Cuánto es 85 menos 32?" to "qa-51",
                "¿Puedo restar al revés?" to "qa-53",
                "¿Cómo se calcula el vuelto?" to "qa-55",
                "¿Qué es el doble?" to "qa-64",
                "¿Cuánto es cinco por cien?" to "qa-65",
                "¿Es lo mismo cinco por siete que siete por cinco?" to "qa-67",
                "¿Qué es la propiedad conmutativa de la suma?" to "qa-09",
                "¿Qué es el dividendo?" to "qa-73",
                "¿Cuánto es treinta y cinco dividido cinco?" to "qa-74",
                "¿Qué pasa si divido por cero?" to "qa-83",
                "¿Qué es el numerador?" to "qa-87",
                "¿Qué es la mitad?" to "qa-88",
                "¿Cuántos cuartos hay en un entero?" to "qa-90",
                "¿Qué es un número con coma?" to "qa-98",
                "¿Qué es un rombo?" to "qa-112",
                "¿Cuántas caras tiene un cubo?" to "qa-116",
                "¿Por qué la pelota rueda y la caja no?" to "qa-119",
                "¿Cuántos gramos tiene un kilo?" to "qa-124",
                "¿Cuántos minutos tiene una hora?" to "qa-126",
                "¿Qué es el perímetro?" to "qa-127",
                // The original set must keep working next to the new one.
                "Anita, ¿qué es sumar?" to "qa-06",
                "Anita, ¿cuántos lados tiene un triángulo?" to "qa-23",
                "Anita, ¿qué es un vértice?" to "qa-27",
                // The follow-up entries added on top of the guide, and the extra variantes given
                // to 9 existing entries so a test question doesn't need to be recited verbatim.
                "¿Qué son los rombos?" to "qa-112",
                "¿Cuánto pesa un kilo?" to "qa-124",
                "¿Puedo dividir algo por cero?" to "qa-83",
                "¿Qué es un cuarto de algo?" to "qa-89",
                "¿Cuánto es el doble de un número?" to "qa-64",
                "¿Cómo se saca el vuelto?" to "qa-55",
                "¿Qué son las tablas de multiplicar?" to "qa-62",
                "¿Qué son las sumas?" to "qa-06",
                "¿Qué es un ángulo obtuso?" to "qa-130",
                "¿Qué son las aristas?" to "qa-131",
                "¿Qué es una cara de un cuerpo?" to "qa-132",
                "¿Qué son los poliedros?" to "qa-133",
                "¿Qué es una esfera?" to "qa-134",
                "¿Qué son los cuadriláteros?" to "qa-135",
                "¿Cuánto es la mitad de diez?" to "qa-136",
                "¿Qué es más grande un medio o un cuarto?" to "qa-137",
            )

        val failures = expected.mapNotNull { (question, expectedId) ->
            val actualId = searcher.search(question).firstOrNull()?.id
            if (actualId == expectedId) null else "\"$question\" -> $actualId (expected $expectedId)"
        }
        assertTrue("Mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `answers avoid symbols that Spanish TTS reads badly`() {
        // The lesson fragments write "3 x 4" and "6÷2", which Android's Spanish voice reads as
        // "tres equis cuatro" and mangles "÷" -- the curated answers spell operations out in
        // words ("tres por cuatro", "seis dividido dos") precisely to avoid that.
        val badSymbols = listOf("+", "÷", "=", " x ", " X ")
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val respuesta = entry.getString("respuesta")
            for (symbol in badSymbols) {
                assertFalse("${entry.getString("id")} respuesta contains '$symbol'", respuesta.contains(symbol))
            }
        }
    }

    @Test
    fun `test-protocol questions resolve to the intended answers`() {
        val expected =
            listOf(
                // Section 2.1: answerable directly.
                "Manuel, ¿cómo se cuenta del uno al diez?" to "qa-01",
                "Manuel, ¿qué es el número cero?" to "qa-02",
                "Manuel, ¿cómo sé si un número es más grande o más chico que otro?" to "qa-04",
                "Manuel, ¿qué es sumar?" to "qa-06",
                "Manuel, ¿cómo sumo con los dedos de la mano?" to "qa-08",
                "Manuel, ¿qué es restar?" to "qa-11",
                "Manuel, ¿cómo resto contando hacia atrás?" to "qa-13",
                "Manuel, ¿qué es multiplicar?" to "qa-16",
                "Manuel, ¿cuánto es la tabla del cinco?" to "qa-19",
                "Manuel, ¿qué es dividir?" to "qa-28",
                // Section 2.2: alternate phrasings of the same ideas.
                "Manuel, contame los números hasta el diez" to "qa-01",
                "Manuel, ¿el cero es un número o no es nada?" to "qa-02",
                "Manuel, ¿cuál es más grande, cinco o siete?" to "qa-04",
                "Manuel, si tengo tres caramelos y me dan dos más, ¿qué hice?" to "qa-06",
                "Manuel, ayudame a sumar con la mano" to "qa-08",
                "Manuel, si tenía cinco lápices y perdí dos, ¿qué pasó?" to "qa-11",
                "Manuel, contá para atrás desde el cinco" to "qa-13",
                "Manuel, explicame la multiplicación como si fuera chiquito" to "qa-16",
                "Manuel, ¿la tabla del dos cuánto da?" to "qa-18",
                "Manuel, si reparto caramelos en partes iguales, ¿cómo se llama eso?" to "qa-28",
                // Section 3: multi-turn dialogue turns that stand on their own.
                "Manuel, ¿y si sumo tres números seguidos?" to "qa-10",
                "Manuel, ¿y cuánto es cinco por tres?" to "qa-20",
                "Manuel, ¿y cinco por cero?" to "qa-21",
                "Manuel, ¿cuántos lados tiene un triángulo?" to "qa-23",
                "Manuel, ¿y el cuadrado?" to "qa-24",
                "Manuel, ¿qué es un vértice?" to "qa-27",
                "Manuel, ¿la resta es lo contrario de qué?" to "qa-15",
                "Manuel, ¿cómo reparto algo en partes iguales?" to "qa-29",
                "Manuel, ¿y si divido un número por uno?" to "qa-30",
                // Real whisper.cpp output captured on a phone during development.
                " ¿Cuántos lados tienen triángulo?" to "qa-23",
                " ¿Cuántos lados tienen cuadrado?" to "qa-24",
                " que es dividir." to "qa-28",
                " ¿Cómo se cuenta del 1 al 10?" to "qa-01",
                " ¿Qué es un bértice?" to "qa-27",
            )

        val failures = expected.mapNotNull { (question, expectedId) ->
            val actualId = searcher.search(question).firstOrNull()?.id
            if (actualId == expectedId) null else "\"$question\" -> $actualId (expected $expectedId)"
        }
        assertTrue("Mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `out-of-scope and ambiguous test-protocol questions resolve to nothing`() {
        val unanswerable =
            listOf(
                // Section 2.3: outside the lesson content -- must not invent an answer (FR-008).
                "Manuel, ¿quién descubrió América?",
                "Manuel, ¿cómo se dice 'hola' en inglés?",
                "Manuel, ¿qué hora es?",
                "Manuel, ¿cuántos planetas hay en el sistema solar?",
                "Manuel, contame un chiste",
                // Section 2.4: ambiguous or incomplete.
                "Manuel, ¿y eso?",
                "Manuel, explicame",
                "Manuel, ¿cuánto es?",
                "Manuel, la cosa esa de las figuras",
                "Manuel, ¿está bien lo que hice?",
            )

        val failures = unanswerable.mapNotNull { question ->
            val match = searcher.search(question).firstOrNull()
            if (match == null) null else "\"$question\" -> ${match.id} (expected no match)"
        }
        assertTrue("Unexpected matches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        // Gradle runs unit tests with the module directory (app/) as the working directory; the
        // repo-root fallback keeps this runnable from an IDE that uses the project root instead.
        fun assetFile(): File = firstExisting("src/main/assets/content/preguntas_respuestas.json")
        fun lessonsFile(): File = firstExisting("src/main/assets/content/matematica_lecciones.json")
        fun guideFile(): File = firstExisting("../docs/content/preguntas-matematica-3-4-grado.md")

        private fun firstExisting(relativePath: String): File =
            listOf(File(relativePath), File("app", relativePath), File(relativePath.removePrefix("../")))
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
