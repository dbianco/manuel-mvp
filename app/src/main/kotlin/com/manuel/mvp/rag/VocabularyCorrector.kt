package com.manuel.mvp.rag

/**
 * Best-effort spelling correction for a transcribed instruction, based on the exact vocabulary of
 * words that actually appear in this app's own content ([buildVocabulary]) -- not a general
 * Spanish dictionary. whisper.cpp sometimes swaps or drops a single letter in a word it doesn't
 * know well ("Córdoba" -> "cordoa", "vértice" -> "bértice", "quién" -> "guién"), which breaks
 * FTS5's exact-token matching even though a human has no trouble understanding what was meant.
 * `ConversationPipeline` runs the whole resolved instruction through [correct] before handing it
 * to [AnswerSearcher]/[FragmentSearcher], so those small mistakes get fixed before they ever reach
 * search.
 *
 * Deliberately conservative -- three guards keep this from "fixing" a word into the wrong one:
 *
 * 1. Words shorter than [MIN_CORRECTABLE_LENGTH] are never touched. A short word is close (by edit
 *    distance) to too many unrelated words to correct safely.
 * 2. The edit-distance budget scales with word length: 1 for 4-6 letter words, 2 for
 *    [LONG_WORD_LENGTH]+ -- tight enough that a genuinely different, unrelated word is unlikely to
 *    fall within budget of some vocabulary word by coincidence.
 * 3. If two or more vocabulary words are equally close, nothing is corrected. Guessing wrong is
 *    worse than leaving an unmatched word alone -- it just won't match anything, exactly like
 *    today, rather than confidently resolving to a wrong topic.
 *
 * A word already in the vocabulary, verbatim, is never touched -- this only fires for words STT
 * actually got wrong.
 */
class VocabularyCorrector(private val vocabulary: Set<String>) {

    /** Returns [text] with each mis-transcribed word replaced by its closest vocabulary match. */
    fun correct(text: String): String = Regex("\\S+").replace(text) { match -> correctToken(match.value) }

    private fun correctToken(token: String): String {
        val core = token.trim { !it.isLetterOrDigit() }
        if (core.isEmpty()) return token
        val corrected = correctWord(core) ?: return token
        val start = token.indexOf(core)
        return token.substring(0, start) + corrected + token.substring(start + core.length)
    }

    private fun correctWord(word: String): String? {
        val normalized = Fts5Query.normalize(word)
        if (normalized.length < MIN_CORRECTABLE_LENGTH) return null
        if (normalized.any { it.isDigit() }) return null
        if (normalized in vocabulary) return null

        val maxDistance = if (normalized.length >= LONG_WORD_LENGTH) 2 else 1
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var tied = false
        for (candidate in vocabulary) {
            // Edit distance can never be smaller than the length difference -- skip the (cheap)
            // full comparison for anything already out of budget.
            if (Math.abs(candidate.length - normalized.length) > maxDistance) continue
            val distance = levenshtein(normalized, candidate)
            when {
                distance < bestDistance -> {
                    bestDistance = distance
                    best = candidate
                    tied = false
                }
                distance == bestDistance -> tied = true
            }
        }
        return if (best != null && bestDistance <= maxDistance && !tied) best else null
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    companion object {
        const val MIN_CORRECTABLE_LENGTH = 4
        const val LONG_WORD_LENGTH = 7

        /**
         * Builds the vocabulary [VocabularyCorrector] corrects against: every distinct,
         * [Fts5Query.normalize]d word appearing in `respuestas` (`tema`, `pregunta`, `variantes` --
         * not `respuesta`, which is spoken output, never matched against) and `fragments` (`tema`,
         * `texto`). Uses the same [FragmentRowSource] raw-SQL seam [AnswerSearcher]/
         * [FragmentSearcher] do, so this can be exercised in a JVM unit test against a real FTS5
         * engine, with no Android involved, and stays in sync automatically whenever the content
         * assets change -- no separate build step to keep it up to date.
         */
        fun buildVocabulary(source: FragmentRowSource): Set<String> {
            val vocabulary = mutableSetOf<String>()
            for (row in source.rawQuery("SELECT tema, pregunta, variantes FROM respuestas", emptyList())) {
                vocabulary += wordsIn(row["tema"])
                vocabulary += wordsIn(row["pregunta"])
                vocabulary += wordsIn(row["variantes"])
            }
            for (row in source.rawQuery("SELECT tema, texto FROM fragments", emptyList())) {
                vocabulary += wordsIn(row["tema"])
                vocabulary += wordsIn(row["texto"])
            }
            return vocabulary
        }

        private fun wordsIn(text: String?): Set<String> {
            if (text.isNullOrEmpty()) return emptySet()
            return Regex("[\\p{L}\\p{Nd}]+").findAll(text).map { Fts5Query.normalize(it.value) }.toSet()
        }
    }
}
