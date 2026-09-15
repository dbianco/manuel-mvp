# Protocolo de prueba manual — Manuel MVP

**Estado: protocolo listo para ejecutar, aún no ejecutado.** Este documento define qué probar y cómo registrarlo (T022); no contiene resultados. No puede ejecutarse todavía porque, a la fecha de este documento, faltan por provisionar: el modelo `manuel.onnx` entrenado (palabra clave, ver nota de T003) y los modelos GGUF de whisper.cpp/llama.cpp (ver notas de T013/T015). En cuanto esos archivos existan en un dispositivo objetivo, este protocolo puede correrse tal cual.

Valida `docs/superpowers/specs/spec.md`'s SC-001 a SC-011, usando como base el conjunto de prueba de la sección 11 del documento de diseño original (`2026-09-14-manuel-mvp-design.md`), adaptado a la palabra clave "Manuel, <instrucción>" que reemplazó al modelo de un solo botón (ver Clarifications de `spec.md`).

## 0. Antes de empezar (pre-flight)

- [ ] **Sin red**: Wi-Fi, datos móviles y Bluetooth desactivados en el teléfono (FR-012/SC-001).
- [ ] **Permiso de micrófono**: `RECORD_AUDIO` concedido (la app lo pide al tocar "Escuchar" por primera vez, T020).
- [ ] **Modelo de palabra clave**: `app/src/main/assets/wakeword/manuel.onnx` presente (junto a `melspectrogram.onnx`/`embedding_model.onnx`, ya vendorizados desde T003).
- [ ] **Modelo Whisper**: archivo GGML presente en el almacenamiento interno de la app, en la ruta que espera `MainActivity.kt` (T020): `<filesDir>/models/ggml-tiny.bin`.
- [ ] **Modelo Llama**: archivo GGUF (Llama 3.2 3B Instruct, Q4_K_M) presente en `<filesDir>/models/llama-3.2-3b-instruct-q4_k_m.gguf`.
- [ ] **Voz en español**: al iniciar la app, confirmar que no se reporta ausencia de voz en español (FR-009); si el dispositivo no tiene una voz TTS en español descargada, instalarla desde los ajustes de Android antes de continuar.
- [ ] **Hardware objetivo**: teléfono Android gama media/alta ya disponible (8GB+ RAM, procesador Snapdragon reciente o equivalente, Android 10+ — este proyecto declara `minSdk = 29`/Android 10 —, 20GB de almacenamiento libre, batería cargada). No es hardware de gama baja.
- [ ] **Ambiente**: primera pasada en una habitación tranquila (ruido de fondo bajo); las pruebas de ruido ambiente (sección 4) son la excepción deliberada.
- [ ] **Reinicio de sesión entre bloques**: tocar "Dejar de escuchar" y volver a tocar "Escuchar" entre cada pregunta suelta (sección 2) para asegurar que cada una arranca sin memoria de sesión previa; **no** hacerlo entre los turnos de un mismo diálogo multi-turno (sección 3), ya que ahí la continuidad de la memoria es justamente lo que se está probando.

## 1. Plantilla de registro por prueba

Completar una fila así por cada pregunta suelta o turno de diálogo:

| Campo | Contenido |
|---|---|
| Pregunta/turno esperado | El texto exacto dicho en voz alta (ver listas abajo) |
| Transcripción obtenida | Lo que Whisper transcribió realmente |
| Fragmentos recuperados | Los `id` de `ContentFragment` que devolvió `FragmentSearcher` |
| Memoria de sesión usada | Los intercambios previos que tenía `SessionMemory` en ese momento (vacío si es el primer turno) |
| Respuesta generada | El texto que generó el LLM y que se escuchó por TTS |
| Respuesta correcta esperada | Qué debería haber dicho, en base al contenido real |
| Tiempo total | Segundos desde que terminó de hablar el usuario hasta que empezó la respuesta hablada (SC-005) |
| Observaciones | Cualquier cosa relevante: transcripción rara, respuesta inventada, corte, etc. |

## 2. 30 preguntas sueltas

Cada una se dice exactamente así, prefijada con la palabra clave (FR-003). Se arma ("Escuchar") antes de cada una y se desarma después (ver pre-flight).

### 2.1 — 10 respondibles directamente por los contenidos

| # | Pregunta | Fragmento esperado |
|---|---|---|
| 1 | "Manuel, ¿cómo se cuenta del uno al diez?" | `matematica-leccion-01-001` |
| 2 | "Manuel, ¿qué es el número cero?" | `matematica-leccion-01-002` |
| 3 | "Manuel, ¿cómo sé si un número es más grande o más chico que otro?" | `matematica-leccion-01-004` |
| 4 | "Manuel, ¿qué es sumar?" | `matematica-leccion-02-001` |
| 5 | "Manuel, ¿cómo sumo con los dedos de la mano?" | `matematica-leccion-02-003` |
| 6 | "Manuel, ¿qué es restar?" | `matematica-leccion-03-001` |
| 7 | "Manuel, ¿cómo resto contando hacia atrás?" | `matematica-leccion-03-003` |
| 8 | "Manuel, ¿qué es multiplicar?" | `matematica-leccion-04-001` |
| 9 | "Manuel, ¿cuánto es la tabla del cinco?" | `matematica-leccion-04-004` |
| 10 | "Manuel, ¿qué es dividir?" | `matematica-leccion-06-001` |

### 2.2 — 10 con formulaciones alternativas o errores leves

| # | Pregunta | Fragmento esperado (misma idea que arriba, dicha distinto) |
|---|---|---|
| 11 | "Manuel, contame los números hasta el diez" | `matematica-leccion-01-001` |
| 12 | "Manuel, ¿el cero es un número o no es nada?" | `matematica-leccion-01-002` |
| 13 | "Manuel, ¿cuál es más grande, cinco o siete?" | `matematica-leccion-01-004` |
| 14 | "Manuel, si tengo tres caramelos y me dan dos más, ¿qué hice?" | `matematica-leccion-02-001`/`-002` |
| 15 | "Manuel, ayudame a sumar con la mano" | `matematica-leccion-02-003` |
| 16 | "Manuel, si tenía cinco lápices y perdí dos, ¿qué pasó?" | `matematica-leccion-03-001`/`-002` |
| 17 | "Manuel, contá para atrás desde el cinco" | `matematica-leccion-03-003` |
| 18 | "Manuel, explicame la multiplicación como si fuera chiquito" | `matematica-leccion-04-001` |
| 19 | "Manuel, ¿la tabla del dos cuánto da?" | `matematica-leccion-04-003` |
| 20 | "Manuel, si reparto caramelos en partes iguales, ¿cómo se llama eso?" | `matematica-leccion-06-001`/`-003` |

### 2.3 — 5 fuera de contenido (deben resultar en "no tengo información suficiente", nunca una respuesta inventada — FR-008/SC-003)

| # | Pregunta |
|---|---|
| 21 | "Manuel, ¿quién descubrió América?" |
| 22 | "Manuel, ¿cómo se dice 'hola' en inglés?" |
| 23 | "Manuel, ¿qué hora es?" |
| 24 | "Manuel, ¿cuántos planetas hay en el sistema solar?" |
| 25 | "Manuel, contame un chiste" |

### 2.4 — 5 ambiguas o incompletas

| # | Pregunta |
|---|---|
| 26 | "Manuel, ¿y eso?" |
| 27 | "Manuel, explicame" |
| 28 | "Manuel, ¿cuánto es?" |
| 29 | "Manuel, la cosa esa de las figuras" |
| 30 | "Manuel, ¿está bien lo que hice?" |

## 3. 5 diálogos multi-turno (validan FR-011/SC-008: coherencia de memoria de sesión)

Se arma una sola vez ("Escuchar") al principio de cada diálogo y se mantiene armado durante todos sus turnos — **no** desarmar entre turnos. Cada turno se dice por separado, re-prefijado con la palabra clave.

**Diálogo 1 — Suma**
1. "Manuel, repasemos la lección de la suma"
2. "Manuel, dame un ejemplo"
3. "Manuel, ¿y si sumo tres números seguidos?"

**Diálogo 2 — Multiplicación (tabla del 5)**
1. "Manuel, repasemos la tabla del cinco"
2. "Manuel, ¿y cuánto es cinco por tres?"
3. "Manuel, ¿y cinco por cero?"

**Diálogo 3 — Formas geométricas**
1. "Manuel, repasemos las figuras geométricas"
2. "Manuel, ¿cuántos lados tiene un triángulo?"
3. "Manuel, ¿y el cuadrado?"
4. "Manuel, ¿qué es un vértice?"

**Diálogo 4 — Resta**
1. "Manuel, repasemos la resta"
2. "Manuel, dame un ejemplo"
3. "Manuel, ¿la resta es lo contrario de qué?"

**Diálogo 5 — División**
1. "Manuel, repasemos la división"
2. "Manuel, ¿cómo reparto algo en partes iguales?"
3. "Manuel, ¿y si divido un número por uno?"

Un diálogo se cuenta como "coherente" (SC-008) si, en los turnos de seguimiento (2 en adelante), la respuesta se mantiene sobre el mismo tema del turno 1 sin que el usuario lo repita — por ejemplo, en el Diálogo 2, el turno 2 debe entenderse como preguntando dentro de la tabla del 5, no como una pregunta de multiplicación genérica sin contexto.

## 4. Prueba específica de palabra clave (SC-010/SC-011)

- [ ] **SC-011 (activaciones correctas)**: con la app armada, decir "Manuel, <instrucción>" 10 veces en condiciones de aula/demo (con algo de ruido ambiente normal, no silencio de laboratorio). Registrar cuántas de las 10 activan correctamente la captura (se enciende el estado "Escuchando"). Meta: al menos 9 de 10.
- [ ] **SC-010 (falsos positivos)**: con la app armada, mantener al menos 15 minutos de conversación normal entre alumnos/docentes/público, **sin** decir la palabra clave a propósito. Registrar cada vez que la app entra en estado "Escuchando"/"Procesando"/"Respondiendo" sin que nadie haya dicho "Manuel". Meta: que estos falsos disparos ocurran en menos del 5% de los intervalos observados (dividir la sesión en intervalos de ~1 minuto y contar cuántos tuvieron un disparo no intencional).

## 5. Prueba de estabilidad (SC-007)

- [ ] Ejecutar las 30 preguntas sueltas de la sección 2 más los 15 turnos de los 5 diálogos de la sección 3 (45 instrucciones en total, ≥30 consecutivas) sin que la aplicación se cierre inesperadamente (crash). Registrar cualquier cierre, en qué instrucción ocurrió, y el estado visible (`AssistantState`) justo antes.
- [ ] Además de lo anterior, dejar la app armada 15 minutos con conversación ambiente (puede reutilizarse la sesión de la sección 4) y confirmar que tampoco se cierra en ese lapso.

## 6. Prueba de batería y temperatura (SC-009)

- [ ] Con la app armada de forma continua durante 30 minutos (puede combinarse con las pruebas de las secciones 2-4 si entran en la ventana de 30 minutos), registrar: porcentaje de batería al inicio y al final, y si el dispositivo se siente notablemente caliente al tacto o si Android muestra alguna advertencia de temperatura. Meta: sin sobrecalentamiento reportado ni una caída de batería mayor a la esperable para el hardware de gama media/alta usado.

## 7. Prueba de audibilidad (SC-006, cualitativa)

- [ ] Con al menos 2 niños y 1 docente presentes en una habitación pequeña, hacer un subconjunto de las preguntas de la sección 2 (por ejemplo, las 10 de "respondibles directamente") y preguntarles si entendieron la respuesta hablada sin dificultad. Registrar comentarios cualitativos (volumen, claridad de pronunciación, velocidad).

## 8. Mapeo a criterios de aceptación (SC-001 a SC-011)

| Criterio | Qué lo valida |
|---|---|
| SC-001 (100% offline) | Checklist de pre-flight (sección 0) + que las 45 instrucciones de las secciones 2-3 funcionen sin red |
| SC-002 (≥8/10 de las 30 preguntas correctas) | Sección 2 completa, contra la columna "Respuesta correcta esperada" |
| SC-003 (no inventa fuera de contenido) | Sección 2.3 (5 preguntas fuera de contenido) |
| SC-004 (≥80% exactitud de transcripción) | Columna "Transcripción obtenida" vs. lo realmente dicho, en las 45 instrucciones de las secciones 2-3 |
| SC-005 (≤15s en ≥8/10 pruebas) | Columna "Tiempo total" de la plantilla (sección 1), en las 45 instrucciones |
| SC-006 (respuesta entendible) | Sección 7 |
| SC-007 (sin crashes) | Sección 5 |
| SC-008 (≥3/5 diálogos coherentes) | Sección 3 |
| SC-009 (batería/temperatura aceptables) | Sección 6 |
| SC-010 (≤5% falsos positivos de palabra clave) | Sección 4, primera parte |
| SC-011 (≥9/10 activaciones correctas) | Sección 4, segunda parte |
