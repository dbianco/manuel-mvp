package com.manuel.mvp.rag

/**
 * A single retrievable unit of preloaded lesson content (see
 * `app/src/main/assets/content/matematica_lecciones.json`, T004).
 *
 * Field names/shape are the binding contract already established by
 * `FragmentSearcherTest.kt` (T005) and the `fragments` FTS5 table it defines: `id` is the stable
 * fragment identifier (e.g. `"matematica-leccion-01-001"`), `area`/`nivel`/`leccion`/`tema` are
 * content-classification metadata, and `texto` is the actual fragment text that full-text search
 * matches against.
 */
data class ContentFragment(
    val id: String,
    val area: String,
    val nivel: String,
    val leccion: String,
    val tema: String,
    val texto: String,
)
