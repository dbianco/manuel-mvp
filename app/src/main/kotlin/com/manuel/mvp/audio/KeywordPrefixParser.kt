package com.manuel.mvp.audio

/**
 * Extracts the instruction from an STT transcript prefixed by the wake word ("Anita,
 * <instrucción>"), per FR-003/FR-004: an instruction is only recognized when the keyword anchors
 * the (trimmed) start of the transcript, and returns `null` (silent discard) both when the keyword
 * is missing and when it is present but nothing meaningful follows it.
 *
 * This is a pure text-parsing contract, separate from the acoustic wake-word detector
 * ([WakeWordListener]/openWakeWord) -- see [WakeWordListener] for how the two combine. The exact
 * behavioral contract is pinned by `KeywordPrefixParserTest` (T009); this implementation
 * satisfies it unmodified.
 */
object KeywordPrefixParser {

    /** Kept in sync with [WakeWordListener.KEYWORD_MODEL_NAME] -- the spoken wake word. */
    private const val KEYWORD = WakeWordListener.KEYWORD_MODEL_NAME

    fun parse(transcript: String): String? {
        val trimmedStart = transcript.trimStart()
        if (!trimmedStart.startsWith(KEYWORD, ignoreCase = true)) return null

        val afterKeyword = trimmedStart.substring(KEYWORD.length)
        // Word-boundary check: "Anitas" / "Anitaland" also startsWith("anita"), but the keyword
        // must match the whole word, not just a prefix of a longer one. A letter immediately after
        // the keyword means this is a different word, not the wake word followed by its separator.
        if (afterKeyword.isNotEmpty() && afterKeyword[0].isLetter()) return null

        var rest = afterKeyword.trimStart()
        if (rest.startsWith(",")) {
            rest = rest.substring(1)
        }

        val instruction = rest.trim()
        return instruction.ifEmpty { null }
    }
}
