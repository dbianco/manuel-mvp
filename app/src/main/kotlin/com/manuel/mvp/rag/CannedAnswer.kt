package com.manuel.mvp.rag

/**
 * One curated question/answer pair from `app/src/main/assets/content/preguntas_respuestas.json`:
 * [pregunta] is the canonical phrasing it was written for, [respuesta] the exact text the
 * assistant speaks back, and [tema] the lesson topic it covers. The alternate phrasings used for
 * matching (`variantes`) and the lesson fragments it was grounded in (`fragmentos`) stay in the
 * database only -- nothing downstream of [AnswerSearcher] needs them.
 */
data class CannedAnswer(
    val id: String,
    val tema: String,
    val pregunta: String,
    val respuesta: String,
)
