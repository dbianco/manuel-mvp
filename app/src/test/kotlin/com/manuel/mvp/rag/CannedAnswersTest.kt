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
    fun `asset has exactly 337 well-formed entries with unique ids`() {
        // 30 from the original inicial/primario lessons + 99 from the 3rd/4th-grade math guide
        // (110 questions minus the 11 that duplicated ones already in the first set) + 8 follow-up
        // entries (angles, aristas/caras, poliedros, cuerpos redondos, cuadrilátero, mitad,
        // comparing fractions) grounded in guide answers that already mentioned those terms +
        // 100 ciencias naturales + 100 geografía de Córdoba (one entry per guide question, 1:1,
        // since those two guides have no overlap with anything already covered).
        assertEquals(337, entries.length())

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
        // Four kinds of source: a fragment id from matematica_lecciones.json, or "<prefix>-NN" for
        // question NN of one of three markdown guides under docs/content/ (whose answers were
        // rewritten for speech but not changed in substance): "guia34" (math, 3rd/4th grade),
        // "cnat" (ciencias naturales), "geocba" (geografía de Córdoba).
        val fragmentIds = JSONArray(lessonsFile().readText(Charsets.UTF_8)).let { lessons ->
            (0 until lessons.length()).map { lessons.getJSONObject(it).getString("id") }.toSet()
        }
        val mathGuideNumbers = questionNumbersIn(mathGuideFile())
        val cienciasGuideNumbers = questionNumbersIn(cienciasGuideFile())
        val geografiaGuideNumbers = questionNumbersIn(geografiaGuideFile())
        assertEquals(110, mathGuideNumbers.size)
        assertEquals(100, cienciasGuideNumbers.size)
        assertEquals(100, geografiaGuideNumbers.size)

        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            for (source in entry.getJSONArray("fragmentos").strings()) {
                val known =
                    when (val number = Regex("^(guia34|cnat|geocba)-(\\d+)$").matchEntire(source)?.groupValues) {
                        null -> source in fragmentIds
                        else -> when (number[1]) {
                            "guia34" -> number[2] in mathGuideNumbers
                            "cnat" -> number[2] in cienciasGuideNumbers
                            else -> number[2] in geografiaGuideNumbers
                        }
                    }
                assertTrue("${entry.getString("id")} cites unknown source $source", known)
            }
        }
    }

    @Test
    fun `every entry's own question resolves to that entry`() {
        // The strongest collision check available without a phone: with 337 entries across three
        // subjects sharing a lot of everyday vocabulary (sumar, restar, dividir, cero, cuarto,
        // río, planta...), each canonical question must still rank its own entry first. When this
        // fails, fix it in the content (a more specific `pregunta`, or extra `variantes`), not by
        // special-casing the search.
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
    fun `ciencias naturales and geografia de Cordoba paraphrases resolve to the intended answers`() {
        val expected =
            listOf(
                // Ciencias naturales.
                // Not "¿Qué son los seres vivos?" -- that plural phrasing is a genuine tie between
                // qa-138's own definition and qa-139's "características" (both match a variante
                // exactly), which is a real ambiguity in the content, not a bug in the search.
                "¿Cómo sabemos que algo es un ser vivo?" to "qa-138",
                "¿Con qué vemos?" to "qa-149",
                "¿Para qué sirve el corazón?" to "qa-159",
                "¿Qué come un animal herbívoro?" to "qa-181",
                "¿Cómo hacen las plantas su alimento?" to "qa-192",
                "¿Cómo es un gas?" to "qa-205",
                "¿Por qué necesitamos el agua?" to "qa-213",
                "¿De qué se compone el aire?" to "qa-219",
                "¿Qué produce el día y la noche?" to "qa-225",
                "¿Qué son las fases de la luna?" to "qa-230",
                "¿Para qué sirve reciclar?" to "qa-234",
                // Geografía de Córdoba.
                "¿Dónde queda la provincia de Córdoba?" to "qa-238",
                "¿Cómo se llama la capital de Córdoba?" to "qa-239",
                "¿Cuál es la montaña más alta de Córdoba?" to "qa-251",
                "¿Qué pueblos hay en el Valle de Punilla?" to "qa-255",
                "¿Cómo se llama también el río Suquía?" to "qa-264",
                "¿Cuál es el lago más famoso de Córdoba?" to "qa-271",
                "¿Cuál es la puerta de las sierras cordobesas?" to "qa-288",
                "¿Córdoba es la mayor productora de maní?" to "qa-307",
                "¿Cuál es el árbol típico de las sierras cordobesas?" to "qa-320",
                "¿Quién fundó Córdoba?" to "qa-328",
                "¿Cómo se llama el festival de folklore de Cosquín?" to "qa-332",
                // The two older sets must keep resolving next to 200 new entries.
                "Anita, ¿qué es sumar?" to "qa-06",
                "¿Qué es un poliedro?" to "qa-133",
                // Minimal-question shortcuts of otherwise long/compound questions -- a child
                // shouldn't have to recite the full phrasing to get an answer.
                "¿Quién fundó Córdoba?" to "qa-328",
                "¿Cuándo se fundó Córdoba?" to "qa-328",
                "¿Cuántos planetas hay?" to "qa-223",
                "¿Qué come un herbívoro?" to "qa-181",
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
                // ("¿Cuántos planetas hay en el sistema solar?" moved out of this list: ciencias
                // naturales now covers "el sistema solar" as a topic, on purpose -- see the
                // ciencias-naturales/geografía test above.)
                "Manuel, ¿quién descubrió América?",
                "Manuel, ¿cómo se dice 'hola' en inglés?",
                "Manuel, ¿qué hora es?",
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
        fun mathGuideFile(): File = firstExisting("../docs/content/preguntas-matematica-3-4-grado.md")
        fun cienciasGuideFile(): File = firstExisting("../docs/content/preguntas-ciencias-naturales-3-4-grado.md")
        fun geografiaGuideFile(): File = firstExisting("../docs/content/preguntas-geografia-cordoba.md")

        /** The set of "### Pregunta N:" numbers (as strings) found in a guide markdown file. */
        fun questionNumbersIn(file: File): Set<String> =
            Regex("^### Pregunta (\\d+):", RegexOption.MULTILINE)
                .findAll(file.readText(Charsets.UTF_8))
                .map { it.groupValues[1] }
                .toSet()

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
