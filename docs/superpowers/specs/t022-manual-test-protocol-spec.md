# Spec: Manual test protocol document (T022)

## User Scenarios

### Primary user story

As the person running the MVP's field validation, I want a concrete, ready-to-execute test protocol (30 loose questions plus 5 multi-turn dialogues, grounded in the actual preloaded lesson content) with a recording template and an explicit mapping to SC-001 through SC-011, so I can run the whole validation session on the target hardware and know exactly what to record and how to judge pass/fail — without having to re-derive the test set from the source spec myself.

### Acceptance scenarios

1. **Given** the document, **When** the 30 loose questions are inspected, **Then** they split exactly into the four groups the source spec's section 11 requires (10 directly answerable, 10 alternate phrasing/minor errors, 5 out-of-content, 5 ambiguous/incomplete), each grounded in a real fragment from `app/src/main/assets/content/matematica_lecciones.json`.
2. **Given** the document, **When** the 5 multi-turn dialogues are inspected, **Then** each has an opening "repasemos la lección N"-style turn plus 2-3 follow-up turns, each turn re-prefixed with the keyword, per FR-003/FR-011.
3. **Given** the document, **When** the recording template is inspected, **Then** it has exactly the fields the source spec's section 11 requires: pregunta/turno esperado, transcripción obtenida, fragmentos recuperados, memoria de sesión usada, respuesta generada, respuesta correcta esperada, tiempo total, observaciones.
4. **Given** the document, **When** the SC-mapping section is inspected, **Then** every one of SC-001 through SC-011 is mapped to a specific protocol section/measurement that validates it.
5. **Given** the document, **When** the pre-flight checklist is inspected, **Then** it covers every precondition the app actually needs to function (network off per FR-012, RECORD_AUDIO granted, whisper/llama model files provisioned at the exact paths `MainActivity` expects, the trained `manuel.onnx` wake-word model present, a confirmed Spanish TTS voice) — cross-referenced against the real file paths/constants in `MainActivity.kt` (T020) and the known-pending items from T003/T013/T015.

## Functional Requirements

- **FR-001**: The system MUST add `docs/superpowers/specs/manuel-mvp-test-protocol.md` containing: a pre-flight checklist, 30 loose questions split into the four required groups, 5 multi-turn dialogues, a per-test recording template, a wake-word-specific test section (SC-010/SC-011), a stability section (SC-007), a battery/temperature section (SC-009), an audibility section (SC-006), and an SC-001-through-SC-011 mapping table.
- **FR-002**: Every one of the 30 loose questions and every dialogue turn MUST be grounded in a real fragment from the actual preloaded content asset (re-read from the real file, not guessed) — except the 5 "fuera de contenido" and 5 "ambiguas/incompletas" questions, which are deliberately NOT grounded in the content (that's the point of those two groups).
- **FR-003**: Every question/turn MUST be written as the literal phrase a tester says aloud, prefixed with "Manuel," per FR-003 of the top-level spec — this protocol tests the shipped keyword-prefixed interaction model, not the original design doc's since-superseded single-button model.
- **FR-004**: The pre-flight checklist MUST name the exact internal-storage paths `MainActivity.kt` (T020) expects for the whisper/llama model files, cross-referenced against that file's actual constants (not re-typed from memory).
- **FR-005**: The document MUST NOT claim any test has been executed — it is a protocol to run, not a report of results; every recording template/table is empty, ready to fill in during an actual session.

## Success Criteria

- **SC-001**: The document contains exactly 30 loose questions (10+10+5+5) and exactly 5 multi-turn dialogues, each dialogue with 3-4 total turns (1 opening + 2-3 follow-ups).
- **SC-002**: Every content-grounded question/turn is checked against the real `matematica_lecciones.json` (re-read during this task) to confirm the referenced lesson/topic actually exists.
- **SC-003**: All 11 success criteria (SC-001 through SC-011 of the top-level spec) appear in the mapping table with a named protocol section.

## Clarifications

- 2026-09-14: **This document is a protocol, not a report.** No field-test session has actually happened — this repo has neither the trained `manuel.onnx` wake-word model nor the whisper/llama GGUF models provisioned yet (T003/T013/T015's standing notes), so the protocol can't be executed yet either. The document is written so it's ready the moment those files exist and a target device is available.
- 2026-09-14: **Grounded in the actual shipped interaction model (keyword prefix), not the original design doc's button-only model.** The source design doc's section 11 (which this task's own wording references) predates the FR-003 keyword-prefix redefinition recorded in the top-level `spec.md`'s Clarifications; every question here is phrased "Manuel, ..." to match what the shipped app actually expects, not the original single-button phrasing.
