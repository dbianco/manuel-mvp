# Plan: Manual test protocol document (T022)

## Technical Context

- Format: Markdown, `docs/superpowers/specs/manuel-mvp-test-protocol.md`.
- Primary dependencies: none — pure documentation.
- Content grounding: `app/src/main/assets/content/matematica_lecciones.json` (6 lessons: counting, addition, subtraction, multiplication, geometric shapes, division — re-read during this task), `docs/superpowers/specs/spec.md` (SC-001 through SC-011, FR-003's keyword-prefix model), `docs/superpowers/specs/2026-09-14-manuel-mvp-design.md` section 11 (the original test-set structure this task's own wording references), `app/src/main/kotlin/com/manuel/mvp/MainActivity.kt` (T020, for the real model file path constants).

## Constitution Check

- Not applicable in the usual sense (this is a documentation task, not code) — no personal data, no network calls, no schema, no secrets, no CI/test gate of its own, no UI. The one relevant standard is indirectly honored: the protocol itself exists to produce the evidence ("Ask for the test that proves the fix... a test command and its output are [evidence]") this project's quality standard already expects, just for a human-executed test rather than an automated one.

## Project Structure

```
docs/superpowers/specs/
└── manuel-mvp-test-protocol.md   # new
```

## Research

- **Grounding every question in a real fragment**: re-read `matematica_lecciones.json` directly (not from memory of writing T004) to list every lesson/topic pair, then wrote each of the 20 content-grounded questions (10 direct + 10 alternate-phrasing) against a specific, real `id` from that file — avoiding a protocol that tests content the app doesn't actually have.
- **Keyword-prefixed phrasing (FR-003)**: the original design doc's section 11 predates the shipped app's keyword-prefix requirement (a single "Hablar" button in the original design vs. "Manuel, <instrucción>" in the shipped `spec.md`) — every question/turn in this protocol is phrased the way the shipped app actually expects to be addressed, not copied verbatim from the older design doc's phrasing style.
- **Recording template fields**: copied exactly from the design doc's section 11 ("pregunta/turno esperado, transcripción obtenida, fragmentos recuperados, memoria de sesión usada, respuesta generada, respuesta correcta esperada, tiempo total, observaciones") — this is the one piece of section 11 that doesn't need updating for the keyword-prefix change, since it's about what to *record*, not how to *phrase* a test utterance.
- **Model file paths in the pre-flight checklist**: re-read from `MainActivity.kt`'s actual `WHISPER_MODEL_RELATIVE_PATH`/`LLAMA_MODEL_RELATIVE_PATH` constants (T020) rather than re-typed from memory, so the checklist stays accurate if those constants ever change.
