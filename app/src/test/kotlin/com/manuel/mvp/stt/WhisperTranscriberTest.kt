package com.manuel.mvp.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract that T013's real `WhisperTranscriber`
 * implementation must satisfy: accept a whisper.cpp transcription only when its confidence meets
 * a configured threshold, and otherwise signal that the user should repeat the instruction
 * (FR-005: "DEBE pedir que se repita la instrucción cuando la confianza de transcripción sea
 * insuficiente"), rather than acting on an unreliable transcription.
 *
 * `WhisperTranscriber`, `WhisperEngine`, `TranscriptionResult` and `TranscriptionOutcome` do not
 * exist in the main source set yet -- they are T013's job, not this task's (T012). This file will
 * not compile until T013 adds them; that is expected for this test-first split (T012 defines the
 * contract via tests, T013 implements it), same pattern as T005 -> T006, T007 -> T008, and
 * T009 -> T010.
 *
 * ### The engine seam
 *
 * [WhisperEngine] is the injectable boundary standing in for the real whisper.cpp JNI bridge (which
 * needs the vendored native library and a loaded GGUF model -- out of reach for a JVM unit test).
 * These tests substitute [FakeWhisperEngine], a hand-written test double returning a fixed, canned
 * [TranscriptionResult], so `WhisperTranscriber`'s confidence-threshold decision can be exercised
 * deterministically with no real transcription happening. T013 must provide the real,
 * JNI-backed [WhisperEngine] implementation; that implementation is not exercised here.
 *
 * ### Threshold boundary semantics: `>=`, not `>`
 *
 * A confidence exactly equal to the configured threshold is accepted, not treated as
 * "insuficiente" -- mirroring `SessionMemory`'s already-established `>=` convention for its
 * inactivity timeout (T007/T008), for consistency across the codebase's threshold-style contracts.
 */
class WhisperTranscriberTest {

    /** Scenario 1: confidence above the threshold returns the transcribed text unchanged. */
    @Test
    fun `returns transcribed text unchanged when confidence is above the threshold`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "contame un cuento", confidence = 0.9f))
        val transcriber = WhisperTranscriber(engine, confidenceThreshold = 0.6f)

        val outcome = transcriber.transcribe(ShortArray(0))

        assertEquals(TranscriptionOutcome.Transcribed("contame un cuento"), outcome)
    }

    /** Scenario 2: confidence exactly at the threshold is still accepted (inclusive boundary). */
    @Test
    fun `accepts transcription when confidence exactly equals the threshold`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "dime la hora", confidence = 0.6f))
        val transcriber = WhisperTranscriber(engine, confidenceThreshold = 0.6f)

        val outcome = transcriber.transcribe(ShortArray(0))

        assertEquals(TranscriptionOutcome.Transcribed("dime la hora"), outcome)
    }

    /** Scenario 3: confidence below the threshold requests a repeat, not the unreliable text. */
    @Test
    fun `requests repeat when confidence is below the threshold`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "quizás algo", confidence = 0.59f))
        val transcriber = WhisperTranscriber(engine, confidenceThreshold = 0.6f)

        val outcome = transcriber.transcribe(ShortArray(0))

        assertEquals(TranscriptionOutcome.RepeatRequested("quizás algo"), outcome)
    }

    /** Scenario 4: zero confidence requests a repeat, not an empty-string "success". */
    @Test
    fun `requests repeat rather than returning empty success at zero confidence`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "", confidence = 0.0f))
        val transcriber = WhisperTranscriber(engine, confidenceThreshold = 0.6f)

        val outcome = transcriber.transcribe(ShortArray(0))

        assertEquals(TranscriptionOutcome.RepeatRequested(""), outcome)
    }

    /** Scenario 5: the decision reflects the threshold configured at construction, not a hardcoded default. */
    @Test
    fun `decision reflects a custom threshold configured at construction`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "resta de dos cifras", confidence = 0.5f))
        val strictTranscriber = WhisperTranscriber(engine, confidenceThreshold = 0.9f)
        val lenientTranscriber = WhisperTranscriber(engine, confidenceThreshold = 0.4f)

        assertEquals(
            TranscriptionOutcome.RepeatRequested("resta de dos cifras"),
            strictTranscriber.transcribe(ShortArray(0)),
        )
        assertEquals(
            TranscriptionOutcome.Transcribed("resta de dos cifras"),
            lenientTranscriber.transcribe(ShortArray(0)),
        )
    }

    /** Scenario 6: the exact audio array is passed through to the engine, unmodified. */
    @Test
    fun `passes the exact audio array through to the engine unmodified`() {
        val engine = FakeWhisperEngine(TranscriptionResult(text = "listo", confidence = 0.9f))
        val transcriber = WhisperTranscriber(engine, confidenceThreshold = 0.6f)
        val audio = shortArrayOf(1, 2, 3, -4, 5)

        transcriber.transcribe(audio)

        assertSame(audio, engine.lastAudioReceived)
    }

    /** Fake [WhisperEngine] test double: returns a fixed, canned result and records what it was given. */
    private class FakeWhisperEngine(private val result: TranscriptionResult) : WhisperEngine {
        var lastAudioReceived: ShortArray? = null
            private set

        override fun transcribe(audioPcm16: ShortArray): TranscriptionResult {
            lastAudioReceived = audioPcm16
            return result
        }
    }
}
