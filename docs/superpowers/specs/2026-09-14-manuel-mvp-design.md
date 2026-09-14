# Especificación del MVP — Manuel

## Asistente educativo offline para escuelas rurales de Córdoba

**Versión:** 0.2
**Estado:** Aprobado para implementación (brainstorming)
**Plataforma inicial:** Android
**Idioma:** español
**Basado en:** `especificacion-mvp-llm-rag-offline-android.md` (v0.1), revisado y ampliado mediante brainstorming el 2026-09-14

## 1. Objetivo

Validar, en un teléfono Android, que un flujo conversacional de varios turnos —activado por botón (o wake word "Hola Manuel" como experimento)— puede escuchar una pregunta o pedido en español, recuperar contenido educativo cargado localmente, generar una respuesta pedagógicamente apropiada y sostenida en contexto (recordando los últimos 5 intercambios), y leerla en voz alta, todo sin conexión a Internet.

El MVP debe demostrar:

1. El dispositivo entiende preguntas y pedidos en español, incluyendo diálogos de varios turnos ("repasemos la lección 4...").
2. El sistema recupera información relevante de los contenidos cargados y mantiene coherencia temática durante el diálogo.
3. El sistema responde de forma clara, breve y apropiada para nivel inicial/primario, sin inventar información.
4. (Stretch) Es viable activar el asistente por voz ("Hola Manuel") en un aula, sin escucha permanente costosa en batería/privacidad.

No se busca todavía construir el dispositivo físico definitivo ni un asistente conversacional general de dominio abierto.

## 2. Alcance

### Incluido

- Aplicación Android ejecutable sin Internet.
- Activación mediante botón "Hablar" (mecanismo principal y garantizado).
- Activación experimental mediante wake word "Manuel" (stretch goal, con fallback silencioso al botón si falla).
- Captura de una pregunta o pedido de voz.
- Transcripción local (Whisper.cpp).
- Memoria de sesión: ventana deslizante de los últimos 5 intercambios (pregunta+respuesta), sin persistencia entre sesiones.
- Recuperación local de fragmentos educativos (SQLite/FTS5).
- Generación de una respuesta con un LLM local, usando el contexto recuperado y la memoria de sesión.
- Lectura de la respuesta mediante voz sintética local (TTS de Android).
- Contenidos precargados dentro de la aplicación: una materia, 4-6 lecciones de nivel inicial/primario.
- Registro local básico de errores y tiempos de respuesta (sin audio ni texto de las preguntas de los niños).

### Fuera de alcance

- Escucha permanente de calidad productiva (el wake word es un experimento, no un requisito de aceptación).
- Reconocimiento de múltiples usuarios.
- Cámara o visión artificial.
- Conexión a servicios cloud.
- Actualización remota de contenidos.
- Administración de usuarios y escuelas.
- Memoria persistente entre sesiones (más allá de los 5 intercambios de la sesión activa).
- Diseño industrial o fabricación en serie.
- Soporte para hardware de gama baja (se apunta a gama media/alta para este PoC).

## 3. Usuario y escenario principal

Un niño presiona un botón (o dice "Manuel" si el wake word está activo), formula una pregunta o pedido, y sostiene un breve intercambio de varios turnos si corresponde.

Ejemplo guía:

```
- "Hola Manuel"
- "Buenas, ¿en qué puedo ayudarte?"
- "Me ayudarías a repasar la lección número 4 de matemática"
- "Sí, como no, empecemos con la lección número 4, repasaremos cómo funciona
   la operación de multiplicación. Veamos..."
```

Otros ejemplos de preguntas sueltas:

- "¿Qué necesitan las plantas para crecer?"
- "¿Cuántos lados tiene un triángulo?"

La respuesta debe ser breve, comprensible, basada en los contenidos disponibles, y coherente con el hilo de la conversación cuando corresponda.

## 4. Arquitectura

```text
["Hola Manuel" — Porcupine, stretch]         Botón "Hablar" (mecanismo principal)
              \                                        /
               \                                      /
                v                                    v
                    Captura de audio Android
                            |
                            v
                  Whisper.cpp (tiny/base) — STT
                            |
                            v
                   Texto de la pregunta/pedido
                            |
                            v
        Memoria de sesión (últimos 5 intercambios) ---> se agrega como contexto
                            |
                            v
              Búsqueda local SQLite/FTS5 (RAG léxico)
                            |
                            v
                Fragmentos educativos relevantes
                            |
                            v
        Llama 3.2 3B Instruct GGUF (Q4_K_M) — llama.cpp
                            |
                            v
                      Respuesta breve
                            |
                            v
              Android TTS offline (voz española)
                            |
                            v
        Se guarda el turno en memoria de sesión (rolling window de 5)
```

## 5. Componentes de software

### Aplicación Android

- Kotlin.
- Interfaz simple de una sola pantalla.
- Un botón grande para iniciar la pregunta.
- Estado visible: escuchando, procesando, respondiendo o error.
- Sin login ni conexión obligatoria.

### Wake word (stretch goal)

- Motor: Porcupine (Picovoice), corriendo en background escuchando solo la palabra clave.
- Se puede arrancar con una wake word estándar de Porcupine mientras se entrena/consigue el modelo custom "Manuel".
- Si la detección falla o no está disponible, degrada limpiamente al botón — el botón es el camino garantizado para los criterios de aceptación.
- Confirmación visual/sonora breve al detectar la wake word, antes de empezar a escuchar la pregunta, para mitigar falsos positivos en aula ruidosa.

### Reconocimiento de voz (STT)

- Whisper.cpp integrado en Android.
- Modelo inicial: `tiny` para equipos modestos, `base` si el teléfono lo soporta con tiempo de respuesta aceptable.

### Memoria conversacional

- Ventana deslizante de los últimos 5 intercambios (pregunta+respuesta) dentro de una sesión activa.
- Se inyectan en el prompt del LLM junto a los fragmentos recuperados por el RAG del turno actual.
- El prompt prioriza el fragmento RAG del turno actual sobre el historial, para evitar que un error de un turno anterior se arrastre.
- Sin memoria vectorial ni persistencia entre sesiones: se borra al cerrar la app o tras un timeout de inactividad (5 min).

### Base de contenidos

- Una sola materia (sugerido: Matemática, por el ejemplo guía), 4-6 lecciones de nivel inicial/primario.
- Contenido redactado/adaptado a mano por el equipo del proyecto, sin pipeline de ingesta automática.
- Formato de carga inicial: JSON, con fragmentos como:

```json
{
  "id": "matematica-leccion-04-001",
  "area": "Matemática",
  "nivel": "Primario",
  "leccion": "Lección 4 — Multiplicación",
  "tema": "Multiplicación básica",
  "texto": "Multiplicar es sumar un número varias veces. Por ejemplo, 3 x 4 es lo mismo que sumar 3 cuatro veces: 3+3+3+3=12."
}
```

- Los contenidos se indexan antes de distribuir la aplicación. El MVP no incluye editor de contenidos.

### Recuperación (RAG)

- SQLite/FTS5, búsqueda léxica (sin base vectorial mientras la búsqueda textual sea suficiente para este volumen chico de contenido).
- La búsqueda debe devolver entre 3 y 5 fragmentos relevantes.
- El sistema debe limitar el tamaño del contexto (fragmentos RAG + memoria de sesión) para reducir memoria y tiempo de respuesta.

### LLM

- Modelo: Llama 3.2 3B Instruct, cuantizado GGUF (Q4_K_M), servido con llama.cpp.
- Requisitos del prompt:
  - responder en español;
  - usar lenguaje simple, apropiado para nivel inicial/primario;
  - responder en 2 a 4 frases por turno;
  - usar solamente el contexto entregado (fragmentos RAG del turno actual + memoria de sesión);
  - mantener coherencia con el tema/lección en curso durante al menos 3 turnos de seguimiento;
  - indicar cuando la información no está disponible;
  - no inventar fuentes, nombres ni datos.

### Texto a voz

- Motor TTS nativo de Android, con voz española descargada previamente.
- La aplicación debe comprobar en el primer inicio que existe una voz disponible sin conexión.

## 6. Hardware para la prueba

- Teléfono Android gama media/alta, ya disponible: 8GB+ RAM, procesador Snapdragon reciente o equivalente, Android 10+, 20GB de almacenamiento libre, batería funcional.
- No se diseña todavía para hardware de gama baja — eso queda para una fase posterior si el PoC valida el concepto.
- El teléfono sirve para validar el flujo y el contenido. No representa todavía la acústica del dispositivo final en un aula.

## 7. Requisitos funcionales

### RF-01 — Iniciar interacción

La aplicación debe permitir iniciar la captura mediante un botón visible (obligatorio) o, de forma experimental, mediante la wake word "Manuel" (best-effort, con fallback silencioso al botón si la detección falla o no está disponible).

### RF-02 — Transcribir voz offline

La aplicación debe convertir el audio en texto sin enviar datos a Internet. Si la transcripción no es suficientemente confiable, debe pedir que el usuario repita la pregunta.

### RF-03 — Buscar contenido

La aplicación debe buscar fragmentos relevantes dentro de los contenidos precargados usando SQLite/FTS5.

### RF-04 — Generar respuesta restringida y contextual

El LLM debe recibir:

- la pregunta/pedido transcripto del turno actual;
- los fragmentos recuperados del turno actual;
- los últimos 5 intercambios de la sesión (memoria conversacional);
- una instrucción de rol educativo;
- una regla para no inventar información y para priorizar el contexto del turno actual sobre el historial.

Si no hay contenido relevante, debe responder que no tiene información suficiente y sugerir consultar al docente.

### RF-05 — Leer respuesta

La aplicación debe reproducir la respuesta mediante un motor TTS instalado y configurado en español.

### RF-06 — Operar sin red

La función principal debe seguir funcionando con Wi-Fi, datos móviles y Bluetooth desactivados.

### RF-07 — Medir el flujo

La aplicación debe registrar localmente: duración de captura, tiempo de transcripción, tiempo de búsqueda, tiempo de generación, tiempo total, y errores. No se almacenarán audios ni preguntas de niños por defecto.

### RF-08 — Sostener diálogo multi-turno

La sesión debe poder sostener un pedido de tipo "repasemos la lección N" y mantener coherencia temática durante al menos 3 turnos de seguimiento, usando la memoria de sesión de los últimos 5 intercambios.

## 8. Flujo de usuario

1. El usuario abre la aplicación.
2. Presiona "Hablar" (o dice "Manuel", si el wake word está activo y funciona).
3. La aplicación muestra "Escuchando".
4. El usuario formula una pregunta o pedido.
5. La aplicación detecta el final o permite presionar "Listo".
6. Se transcribe el audio.
7. Se recuperan los contenidos relevantes y se combinan con la memoria de sesión.
8. Se genera una respuesta.
9. La respuesta aparece en pantalla y se reproduce por voz.
10. El turno se guarda en la memoria de sesión (ventana de 5).
11. El usuario puede continuar la conversación o iniciar otra pregunta.

## 9. Privacidad

No se almacenan audios ni transcripciones de las preguntas de los niños por defecto — solo métricas agregadas (tiempos, errores). Esto es especialmente relevante tratándose de datos de menores.

## 10. Criterios de aceptación

El MVP se considera válido si cumple lo siguiente en el teléfono objetivo:

- Funciona sin conexión a Internet.
- Responde correctamente al menos 8 de 10 preguntas del conjunto de prueba.
- No inventa una respuesta cuando el tema no está cargado.
- La transcripción de preguntas claras supera el 80% de exactitud aproximada.
- El tiempo total de respuesta no supera 15 segundos en al menos 8 de 10 pruebas.
- La respuesta hablada es entendible en una habitación pequeña.
- La aplicación no se cierra durante 30 preguntas consecutivas.
- La temperatura y el consumo de batería son aceptables para una sesión de 30 minutos.
- Al menos 3 de 5 diálogos multi-turno de prueba mantienen coherencia contextual (el sistema recuerda de qué lección/tema se está hablando sin que el niño lo repita).
- Si se implementa el wake word: se documenta su tasa de falsos positivos/negativos, sin ser criterio bloqueante de éxito del PoC.

## 11. Conjunto de prueba

Preparar inicialmente:

- 30 preguntas sueltas:
  - 10 respondibles directamente por los contenidos;
  - 10 con formulaciones alternativas o errores leves;
  - 5 fuera del contenido;
  - 5 ambiguas o incompletas.
- 5 diálogos multi-turno (ej. "repasemos la lección N" + 2-3 turnos de seguimiento) para validar coherencia contextual.

Cada prueba debe registrar: pregunta/turno esperado, transcripción obtenida, fragmentos recuperados, memoria de sesión usada, respuesta generada, respuesta correcta esperada, tiempo total, observaciones.

## 12. Riesgos y mitigaciones

| Riesgo | Mitigación para el MVP |
|---|---|
| Ruido ambiente | Botón como mecanismo principal; probar primero en habitación tranquila |
| Transcripción incorrecta | Usar Whisper `base` si el teléfono lo soporta; permitir repetir |
| Respuestas inventadas | Contexto obligatorio y respuesta de fallback |
| Respuesta lenta | Modelo 3B cuantizado, respuestas cortas, contexto limitado |
| Poco almacenamiento | Contenidos de texto y modelos cuantizados |
| Batería insuficiente | Sesiones cortas y medición de consumo |
| TTS dependiente de Internet | Descargar y verificar la voz española antes de la prueba |
| Contenido pedagógicamente inadecuado | Revisión humana de todos los contenidos y respuestas del conjunto de prueba |
| Wake word con falsos positivos en aula ruidosa | Confirmación visual/sonora antes de escuchar la pregunta; fallback a botón |
| Memoria conversacional arrastrando un error de un turno anterior | Ventana corta (5 turnos); prompt prioriza el fragmento RAG del turno actual sobre el historial |

## 13. Plan de implementación mínimo

### Fase 1 — Prototipo textual

- Cargar contenidos de prueba.
- Ejecutar búsqueda local (FTS5).
- Ejecutar LLM local con memoria de sesión simulada por texto.
- Mostrar respuestas en pantalla.

### Fase 2 — Voz + botón + memoria multi-turno

- Integrar captura de audio.
- Integrar Whisper.cpp.
- Integrar TTS offline.
- Integrar memoria de sesión (ventana de 5 intercambios) en el flujo real.
- Agregar estados y manejo de errores.

### Fase 3 — Wake word (stretch)

- Integrar Porcupine con wake word estándar o custom ("Manuel").
- Implementar fallback silencioso al botón.
- Medir falsos positivos/negativos en condiciones reales.

### Fase 4 — Evaluación

- Ejecutar el conjunto de 30 preguntas + 5 diálogos multi-turno.
- Medir tiempos y consumo.
- Revisar exactitud pedagógica.
- Probar con al menos dos niños y un docente.

## 14. Decisión de avance

Avanzar a un prototipo de dispositivo dedicado solamente si:

- el flujo funciona sin Internet;
- la respuesta es suficientemente rápida;
- el contenido resulta útil para docentes y niños;
- el reconocimiento de voz funciona en condiciones reales;
- la memoria conversacional de 5 turnos resulta suficiente y coherente en el uso real;
- existe una mejora clara respecto de una aplicación común en un teléfono.

La viabilidad del wake word se evalúa por separado, sin bloquear la decisión de avance general.

## 15. Decisiones deliberadamente postergadas

- No se diseñará todavía una carcasa.
- No se integrará una batería propia.
- No se hará escucha permanente de calidad productiva (el wake word queda como experimento acotado).
- No se usará una GPU dedicada.
- No se agregará una base vectorial mientras la búsqueda textual sea suficiente.
- No se entrenará un modelo propio de LLM.
- No se soporta hardware de gama baja en este PoC.
- No hay memoria persistente entre sesiones, más allá de los 5 intercambios de la sesión activa.

Estas decisiones reducen el costo y el tiempo del MVP. Se revisarán únicamente a partir de mediciones del prototipo.
