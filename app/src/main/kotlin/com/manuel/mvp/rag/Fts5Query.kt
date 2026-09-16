package com.manuel.mvp.rag

import java.text.Normalizer

/**
 * Turns free-form (whisper-transcribed) question text into a safe FTS5 `MATCH` operand, shared
 * by [AnswerSearcher] and [FragmentSearcher] so both match the same way.
 *
 * The input is user-controlled: it's whatever the child said, verbatim. FTS5's query syntax
 * treats `-`, `"`, `(`, `)`, and `:` as operators/metacharacters (a leading `-` negates the next
 * term, `NEAR(...)`/`col:term` change the query shape entirely), so passing it through unescaped
 * lets ordinary spoken input like "no-se" or "hora: la de comer" throw a `SQLITE_ERROR` or
 * silently change what's searched. Each whitespace-separated token is therefore quoted as an
 * FTS5 string literal (embedded `"` doubled), which makes it a literal phrase match immune to
 * FTS5 syntax. FTS5's unicode61 tokenizer re-tokenizes the text *inside* a quoted phrase too, so
 * punctuation stuck to a word ("¿cuántos", "triángulo?") is stripped by FTS5 itself before
 * matching -- only the stopword comparison below needs to strip it explicitly.
 *
 * Tokens are OR-ed together rather than AND-ed (space-separated phrases would default to AND).
 * Verified on-device: with AND, "¿Cuántos lados tienen un triángulo?" failed to match content
 * phrased "el triángulo tiene 3 lados", because *every* query word has to appear in the row for
 * AND to match at all, and natural question phrasing rarely shares 100% of its words with the
 * more declarative content text. OR ranks rows by how many/how prominently query words appear
 * (via `bm25`) without requiring all of them, and [STOPWORDS] keeps grammatical scaffolding
 * ("qué", "cuántos", "tiene") from diluting that ranking or accidentally matching unrelated rows
 * that happen to share only those. A question made *only* of stopwords ("¿y eso?", "explicame")
 * yields `null` -- callers treat that as "no match", so it becomes the "no tengo información"
 * response instead of matching whatever row happens to contain "eso".
 */
internal object Fts5Query {

    /** The FTS5 `MATCH` expression for [text], or `null` if it has no content words to search for. */
    fun build(text: String): String? {
        val contentTokens = text.trim()
            .split(Regex("\\s+"))
            .filter { token ->
                val word = token.trim { !it.isLetterOrDigit() }.lowercase()
                word.isNotEmpty() && word !in STOPWORDS
            }
        if (contentTokens.isEmpty()) return null
        return contentTokens.joinToString(" OR ") { token -> "\"" + token.replace("\"", "\"\"") + "\"" }
    }

    /**
     * The content words of [text] as a set of comparable keys: lowercased, punctuation stripped,
     * diacritics removed (so "triángulo" and whisper's occasional "triangulo" compare equal, the
     * same way FTS5's `remove_diacritics 2` treats them), stopwords dropped. Used by
     * [AnswerSearcher] to compare a query against each candidate's own phrasings in Kotlin, after
     * FTS5 has produced the candidates.
     */
    fun contentWords(text: String): Set<String> =
        text.trim()
            .split(Regex("\\s+"))
            .map { token -> token.trim { !it.isLetterOrDigit() }.lowercase() }
            .filter { word -> word.isNotEmpty() && word !in STOPWORDS }
            .map(::normalize)
            .toSet()

    /**
     * Lowercases [word] and strips its diacritics (so "triángulo" and "triangulo" compare equal,
     * the same way FTS5's `remove_diacritics 2` tokenizer treats them). Shared with
     * [VocabularyCorrector], which needs the exact same normalization to compare a transcribed
     * word against the vocabulary built from this same content.
     */
    fun normalize(word: String): String =
        Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    /**
     * Spanish question words, articles, prepositions, pronouns, and common verb forms that carry
     * little topic-specific meaning on their own. Not exhaustive by design: covers what actually
     * shows up in `manuel-mvp-test-protocol.md`'s questions plus the most common Spanish function
     * words, not a full stopword list for the language. Also includes the wake word itself
     * ("anita"; "manuel", the original one, is kept because the test protocol's questions still
     * use it), since a transcript may start with it ("Anita, ...") without it being part of the
     * question.
     */
    val STOPWORDS = setOf(
        "anita", "manuel", "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al",
        "a", "en", "y", "o", "u", "que", "qué", "como", "cómo", "cual", "cuál", "cuales",
        "cuáles", "cuando", "cuándo", "donde", "dónde", "quien", "quién", "quienes", "quiénes",
        "cuanto", "cuánto", "cuanta", "cuánta", "cuantos", "cuántos", "cuantas", "cuántas",
        "es", "son", "esta", "está", "estan", "están", "ser", "hay", "tiene", "tienen",
        "tener", "hace", "hacen", "hacer", "hice", "hizo", "hago", "sirve", "sirven", "servir",
        "puedo", "podes", "podés", "puede", "pueden", "se", "me", "te", "le", "les", "su", "sus",
        // "por" is deliberately NOT a stopword: in this domain it carries meaning ("tres por
        // cuatro" is a multiplication, "dividir por cero" is not "dividir cero"), and dropping it
        // made those two division questions indistinguishable.
        "mi", "mis", "tu", "tus", "yo", "vos", "nos", "con", "sin", "para", "si", "sí",
        "no", "ni", "pero", "eso", "esto", "esa", "ese", "esas", "esos", "lo", "algo", "cosa",
        "otro", "otra", "todo", "toda", "todos", "todas", "ya", "muy", "ahora", "bien",
        "explicame", "explícame", "dime", "decime", "contame", "cuéntame", "ayudame", "ayúdame",
        "dame", "mostrame", "muéstrame",
        // Added with the 3rd/4th-grade set: verbs that open most of its questions ("¿qué
        // significa...?", "¿cómo sabemos...?", "¿qué pasa cuando...?") and would otherwise make
        // every question in the set look alike to the ranking.
        "significa", "significan", "quiere", "decir", "pasa", "ocurre", "sabemos", "sé",
        "podemos", "usamos", "hacemos", "hago", "tengo", "tenés", "tenemos", "hay",
        "conviene", "llama", "llaman", "entre", "sobre", "cualquier", "también", "vez", "veces",
        "dice", "dicen", "explicar", "explicá", "che",
    )
}
