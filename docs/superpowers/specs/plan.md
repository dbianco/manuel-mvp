## Technical Context

- **Lenguaje/versión**: Kotlin (JVM target 17), Android Gradle Plugin reciente, `minSdk` 29 (Android 10) / `targetSdk` a la última estable disponible.
- **Dependencias principales**:
  - `llama.cpp` (bindings JNI propios) para inferencia del LLM — Llama 3.2 3B Instruct, GGUF Q4_K_M.
  - `whisper.cpp` (bindings JNI propios) para STT — modelo `tiny`, con `base` como opción si el hardware lo permite.
  - Porcupine (Picovoice Android SDK) para detección de palabra clave en background ("Manuel").
  - SQLite nativo de Android con extensión FTS5 (acceso vía `SQLiteOpenHelper` o Room con soporte FTS5) para el RAG léxico.
  - `android.speech.tts.TextToSpeech` (motor nativo) para TTS offline en español.
  - Kotlin Coroutines + `Flow` para orquestar el pipeline asíncrono (palabra clave → STT → RAG → LLM → TTS) y el estado de UI.
  - Jetpack Compose para la pantalla única (más simple para un UI dirigido por estados: armado/escuchando/procesando/respondiendo/error).
- **Almacenamiento**:
  - Contenidos educativos: base SQLite/FTS5 embebida en `assets/`, indexada antes de distribuir la app (sin editor de contenidos ni ingesta en runtime).
  - Memoria de sesión (ventana de 5 intercambios): **solo en memoria** (ninguna tabla ni archivo), para garantizar que no sobrevive al cierre de la app, a "Dejar de escuchar" ni al timeout de 5 minutos.
  - Métricas locales (tiempos, errores, tasa de activación de palabra clave): tabla SQLite separada o archivo de log local acotado en tamaño; nunca incluye audio ni texto de preguntas de los niños.
  - Modelos (GGUF, whisper, keyword de Porcupine): empaquetados en `assets/models/` o descargados a almacenamiento interno en el primer inicio si el tamaño de la APK lo requiere.
- **Herramientas de testing**: JUnit + MockK para lógica determinística (parseo de prefijo de palabra clave, ventana de memoria de sesión, armado del prompt, ranking de FTS5); Espresso/Compose UI tests para los estados de pantalla; protocolo de prueba manual (no automatizado en CI) para el conjunto de 30 preguntas + 5 diálogos multi-turno de la sección 11 del spec fuente, dado que depende de hardware físico y juicio humano.
- **Plataforma objetivo**: Android 10+ (API 29+), gama media/alta (8GB+ RAM, Snapdragon reciente o equivalente), 20GB de almacenamiento libre.
- **Objetivos de performance**: respuesta total ≤15s en ≥8/10 pruebas (SC-005); ≥80% exactitud de transcripción (SC-004); ≤5% de falsos positivos de palabra clave sobre conversación ambiente (SC-010); ≥90% de activaciones correctas de palabra clave (SC-011).
- **Restricciones**: operación 100% sin red en runtime (sin llamadas HTTP de ningún tipo); sin persistencia de audio ni texto de preguntas de niños; contexto acotado (fragmentos RAG + memoria de sesión) para controlar latencia y uso de memoria.

## Constitution Check

- No personal data in logs or error messages: cumplido — las métricas locales (FR-013) solo registran tiempos, errores y tasa de activación de palabra clave; nunca audio ni texto de preguntas de niños.
- Every outbound HTTP call has an explicit timeout and a retry budget: no aplica — la app no realiza llamadas de red en runtime (FR-012, operación 100% offline); se documenta como N/A explícito para este MVP.
- Database schema changes ship as reversible migrations with a tested down path: aplica al esquema SQLite/FTS5 de contenidos y métricas — se define versionado de esquema desde el inicio (greenfield) con migración inicial y downgrade path probado, aunque el MVP no prevea actualización remota de contenidos.
- Public API changes are additive within a major version: no aplica — el MVP no expone ninguna API pública (app standalone, sin backend).
- Secrets come from the environment or the secret manager, never from source: aplica a la AccessKey de Picovoice/Porcupine — se carga desde `local.properties` (no versionado) o variable de entorno de build, nunca hardcodeada en el repositorio.
- Tests run in CI before merge and a red build blocks the merge: se configura CI (GitHub Actions) corriendo unit tests + lint en cada PR antes de mergear a `main`.
- Accessibility: interactive elements are keyboard reachable and labelled: los botones "Escuchar"/"Dejar de escuchar" y el indicador de estado llevan `contentDescription`/labels accesibles para TalkBack, aunque el uso principal sea por voz.

## Project Structure

```text
manuel-mvp/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/manuel/mvp/
│       │   │   ├── `MainActivity.kt`
│       │   │   ├── ui/
│       │   │   │   ├── `MainScreen.kt`              # pantalla única, botones Escuchar/Dejar de escuchar
│       │   │   │   └── `AssistantState.kt`          # armado, escuchando, procesando, respondiendo, error
│       │   │   ├── audio/
│       │   │   │   ├── `WakeWordListener.kt`        # wrapper de Porcupine, arma/desarma la escucha de "Manuel"
│       │   │   │   └── `AudioCaptureManager.kt`     # captura de mic post-palabra-clave
│       │   │   ├── stt/
│       │   │   │   └── `WhisperTranscriber.kt`      # bridge JNI a whisper.cpp
│       │   │   ├── rag/
│       │   │   │   ├── `ContentDatabase.kt`         # SQLite/FTS5
│       │   │   │   ├── `ContentDao.kt`
│       │   │   │   └── `FragmentSearcher.kt`        # top 3-5 fragmentos
│       │   │   ├── llm/
│       │   │   │   ├── `LlamaEngine.kt`             # bridge JNI a llama.cpp
│       │   │   │   └── `PromptBuilder.kt`           # arma prompt: turno actual + RAG + memoria de sesión
│       │   │   ├── session/
│       │   │   │   └── `SessionMemory.kt`           # ventana de 5 intercambios, en memoria, timeout 5 min
│       │   │   ├── tts/
│       │   │   │   └── `SpeechSynthesizer.kt`       # wrapper de TextToSpeech, verificación de voz ES
│       │   │   ├── pipeline/
│       │   │   │   └── `ConversationPipeline.kt`    # orquesta: palabra clave -> STT -> RAG -> LLM -> TTS
│       │   │   └── metrics/
│       │   │       └── `LocalMetricsLogger.kt`      # tiempos + tasa de activación/falsos positivos
│       │   ├── res/                                  # layouts (si aplica), strings.xml (es), drawables
│       │   └── assets/
│       │       ├── models/                           # GGUF de Llama, modelo whisper, keyword file de Porcupine
│       │       └── content/`matematica_lecciones.json` # fragmentos precargados (una materia, 4-6 lecciones)
│       ├── test/kotlin/com/manuel/mvp/...             # unit tests: SessionMemory, PromptBuilder, parseo de palabra clave, FragmentSearcher
│       └── androidTest/kotlin/com/manuel/mvp/...      # tests instrumentados de estados de UI
├── docs/superpowers/specs/`2026-09-14-manuel-mvp-design.md`   (ya existente, documento fuente)
└── `especificacion-mvp-llm-rag-offline-android.md`             (ya existente, v0.1 previa)
```

## Research

- **Motor de wake word**: se elige Porcupine (Picovoice) en vez de un enfoque de VAD + Whisper corriendo continuamente, porque Porcupine está diseñado para detección de palabra clave de bajo consumo (huella chica, offline, sin necesidad de correr STT completo todo el tiempo). Alternativa descartada: transcripción continua con Whisper.cpp, por consumo de batería y porque introduciría latencia y falsos positivos al procesar toda la conversación ambiente.
- **Runtime del LLM**: llama.cpp con Llama 3.2 3B Instruct Q4_K_M (definido en el spec) sobre variantes más grandes, para ajustar latencia y memoria a teléfonos de 8GB+ RAM. Alternativa descartada: un modelo 7B, por riesgo de exceder el presupuesto de 15s de SC-005 en el hardware objetivo.
- **Recuperación (RAG)**: SQLite/FTS5 léxico en vez de una base vectorial, siguiendo la decisión ya explícita del spec fuente (sección 15) dado el volumen chico de contenido (4-6 lecciones). Se revisita solo si el volumen de contenido crece significativamente.
- **Memoria de sesión**: se mantiene únicamente en memoria (sin tabla ni archivo), la forma más simple de garantizar el requisito de privacidad "sin persistencia entre sesiones" (FR-010) sin necesitar lógica de borrado seguro de disco.
- **UI**: pantalla única con Jetpack Compose, dado que solo hay un conjunto chico de estados (armado/escuchando/procesando/respondiendo/error) y dos botones — más simple de modelar como UI reactiva a estado que con Views/XML tradicional.
- **Testing de campo**: el conjunto de 30 preguntas + 5 diálogos multi-turno (sección 11 del spec fuente) se ejecuta como protocolo manual documentado, no automatizado en CI, porque las métricas de exactitud, latencia percibida y coherencia dependen de hardware físico y juicio humano (docente/niños). CI solo cubre lógica determinística (parseo de prefijo de palabra clave, ventana de memoria, ensamblado de prompt, ranking FTS5).
