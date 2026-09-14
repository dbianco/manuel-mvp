# Especificación del MVP

## Asistente educativo offline para escuelas rurales

**Versión:** 0.1  
**Estado:** Propuesta para prueba de concepto  
**Plataforma inicial:** Android  
**Idioma:** español  

## 1. Objetivo

Validar que un niño pueda hacer una pregunta hablada sobre contenidos educativos y recibir una respuesta hablada, sin conexión a Internet.

El MVP debe demostrar tres cosas:

1. El dispositivo entiende preguntas simples en español.
2. El sistema recupera información de los contenidos cargados.
3. El sistema responde de forma clara, breve y apropiada para nivel inicial/primario.

No se busca todavía construir el dispositivo físico definitivo ni un asistente conversacional general.

## 2. Alcance

### Incluido

- Aplicación Android ejecutable sin Internet.
- Activación mediante botón “Hablar”.
- Captura de una pregunta de voz.
- Transcripción local.
- Recuperación local de fragmentos educativos.
- Generación de una respuesta con un LLM local.
- Lectura de la respuesta mediante voz sintética local.
- Contenidos precargados dentro de la aplicación.
- Registro local básico de errores y tiempos de respuesta.

### Fuera de alcance

- Palabra de activación tipo “Alexa”.
- Escucha permanente.
- Reconocimiento de múltiples usuarios.
- Cámara o visión artificial.
- Conexión a servicios cloud.
- Actualización remota de contenidos.
- Administración de usuarios y escuelas.
- Conversación larga con memoria persistente.
- Diseño industrial o fabricación en serie.

## 3. Usuario y escenario principal

Un niño presiona un botón, realiza una pregunta y espera una respuesta hablada.

Ejemplos:

- “¿Qué necesitan las plantas para crecer?”
- “¿Cuántos lados tiene un triángulo?”
- “¿Qué animales viven en la granja?”
- “¿Por qué llueve?”

La respuesta debe ser breve, comprensible y basada en los contenidos disponibles.

## 4. Requisitos funcionales

### RF-01 — Iniciar pregunta

La aplicación debe permitir iniciar y finalizar la captura mediante un botón visible.

### RF-02 — Transcribir voz offline

La aplicación debe convertir el audio en texto sin enviar datos a Internet.

Si la transcripción no es suficientemente confiable, debe pedir que el usuario repita la pregunta.

### RF-03 — Buscar contenido

La aplicación debe buscar fragmentos relevantes dentro de los contenidos precargados.

Para el MVP se utilizará una búsqueda local simple sobre SQLite/FTS5 o equivalente. No se agrega una base vectorial hasta que la búsqueda textual resulte insuficiente.

### RF-04 — Generar respuesta restringida

El LLM debe recibir:

- la pregunta transcripta;
- los fragmentos recuperados;
- una instrucción de rol educativo;
- una regla para no inventar información.

Si no hay contenido relevante, debe responder que no tiene información suficiente y sugerir consultar al docente.

### RF-05 — Leer respuesta

La aplicación debe reproducir la respuesta mediante un motor TTS instalado y configurado en español.

### RF-06 — Operar sin red

La función principal debe seguir funcionando con Wi-Fi, datos móviles y Bluetooth desactivados.

### RF-07 — Medir el flujo

La aplicación debe registrar localmente:

- duración de captura;
- tiempo de transcripción;
- tiempo de búsqueda;
- tiempo de generación;
- tiempo total;
- errores.

No se almacenarán audios ni preguntas de niños por defecto.

## 5. Arquitectura propuesta

```text
Botón Hablar
      |
      v
Captura de audio Android
      |
      v
Whisper.cpp / modelo pequeño de STT
      |
      v
Texto de la pregunta
      |
      v
Búsqueda local en SQLite/FTS5
      |
      v
Fragmentos educativos relevantes
      |
      v
llama.cpp / LLM GGUF cuantizado
      |
      v
Respuesta breve
      |
      v
Android Text-to-Speech offline
```

## 6. Componentes de software

### Aplicación Android

- Kotlin.
- Interfaz simple de una sola pantalla.
- Un botón grande para iniciar la pregunta.
- Estado visible: escuchando, procesando, respondiendo o error.
- Sin login ni conexión obligatoria.

### Reconocimiento de voz

Se utilizará Whisper.cpp integrado en Android.

Modelo inicial recomendado:

- `tiny` para equipos modestos;
- `base` si el teléfono tiene suficiente memoria y el tiempo de respuesta es aceptable.

### Base de contenidos

Formato de carga inicial: Markdown, JSON o texto plano convertido durante la preparación.

Cada fragmento debe incluir:

```json
{
  "id": "ciencias-plantas-001",
  "area": "Ciencias Naturales",
  "nivel": "Inicial",
  "tema": "Las plantas",
  "texto": "Las plantas necesitan agua, luz y aire para crecer."
}
```

Los contenidos se indexan antes de distribuir la aplicación. El MVP no incluye editor de contenidos.

### Recuperación

La búsqueda debe devolver entre 3 y 5 fragmentos relevantes. El sistema debe limitar el tamaño del contexto para reducir memoria y tiempo de respuesta.

### LLM

Modelo inicial: GGUF cuantizado de 1B a 3B parámetros.

Requisitos del prompt:

- responder en español;
- usar lenguaje simple;
- responder en 2 a 4 frases;
- usar solamente el contexto entregado;
- indicar cuando la información no está disponible;
- no inventar fuentes, nombres ni datos.

### Texto a voz

Se utilizará el motor TTS de Android con una voz española descargada previamente. La aplicación debe comprobar durante el primer inicio que existe una voz disponible sin conexión.

## 7. Hardware para la prueba

### Mínimo

- Teléfono Android ARM64.
- 8 GB de RAM recomendados.
- 15 GB de almacenamiento libre.
- Android 10 o superior.
- Batería funcional.

### Preferido

- 8 GB o más de RAM.
- Procesador Snapdragon reciente o equivalente.
- 20 GB de almacenamiento libre.
- Parlante integrado adecuado o parlante USB/Bluetooth.
- Micrófono externo opcional para comparar calidad de captura.

El teléfono sirve para validar el flujo y el contenido. No representa todavía la acústica del dispositivo final en un aula.

## 8. Flujo de usuario

1. El usuario abre la aplicación.
2. Presiona “Hablar”.
3. La aplicación muestra “Escuchando”.
4. El usuario formula una pregunta.
5. La aplicación detecta el final o permite presionar “Listo”.
6. Se transcribe el audio.
7. Se recuperan los contenidos relevantes.
8. Se genera una respuesta.
9. La respuesta aparece en pantalla y se reproduce por voz.
10. El usuario puede iniciar otra pregunta.

## 9. Criterios de aceptación

El MVP se considera válido si cumple lo siguiente en un teléfono objetivo:

- Funciona sin conexión a Internet.
- Responde correctamente al menos 8 de 10 preguntas del conjunto de prueba.
- No inventa una respuesta cuando el tema no está cargado.
- La transcripción de preguntas claras supera el 80% de exactitud aproximada.
- El tiempo total de respuesta no supera 15 segundos en al menos 8 de 10 pruebas.
- La respuesta hablada es entendible en una habitación pequeña.
- La aplicación no se cierra durante 30 preguntas consecutivas.
- La temperatura y el consumo de batería son aceptables para una sesión de 30 minutos.

## 10. Conjunto de prueba

Preparar inicialmente 30 preguntas:

- 10 preguntas respondibles directamente por los contenidos;
- 10 preguntas con formulaciones alternativas o errores leves;
- 5 preguntas fuera del contenido;
- 5 preguntas ambiguas o incompletas.

Cada prueba debe registrar:

- pregunta esperada;
- transcripción obtenida;
- fragmentos recuperados;
- respuesta generada;
- respuesta correcta esperada;
- tiempo total;
- observaciones.

## 11. Riesgos y mitigaciones

| Riesgo | Mitigación para el MVP |
|---|---|
| Ruido ambiente | Usar botón para hablar y probar primero en habitación tranquila |
| Transcripción incorrecta | Usar Whisper `base` si el teléfono lo soporta y permitir repetir |
| Respuestas inventadas | Contexto obligatorio y respuesta de fallback |
| Respuesta lenta | Modelo de 1B–3B, respuestas cortas y contexto limitado |
| Poco almacenamiento | Contenidos de texto y modelos cuantizados |
| Batería insuficiente | Sesiones cortas y medición de consumo |
| TTS dependiente de Internet | Descargar y verificar la voz española antes de la prueba |
| Contenido pedagógicamente inadecuado | Revisión humana de todos los contenidos y respuestas del conjunto de prueba |

## 12. Plan de implementación mínimo

### Fase 1 — Prototipo textual

- Cargar contenidos de prueba.
- Ejecutar búsqueda local.
- Ejecutar LLM local.
- Mostrar respuestas en pantalla.

### Fase 2 — Voz

- Integrar captura de audio.
- Integrar Whisper.cpp.
- Integrar TTS offline.
- Agregar estados y manejo de errores.

### Fase 3 — Evaluación

- Ejecutar el conjunto de 30 preguntas.
- Medir tiempos y consumo.
- Revisar exactitud pedagógica.
- Probar con al menos dos niños y un docente.

## 13. Decisión de avance

Avanzar a un prototipo de dispositivo dedicado solamente si:

- el flujo funciona sin Internet;
- la respuesta es suficientemente rápida;
- el contenido resulta útil para docentes y niños;
- el reconocimiento de voz funciona en condiciones reales;
- existe una mejora clara respecto de una aplicación común en un teléfono.

## 14. Decisiones deliberadamente postergadas

- No se diseñará todavía una carcasa.
- No se integrará una batería propia.
- No se incorporará wake word.
- No se usará una GPU dedicada.
- No se agregará una base vectorial mientras la búsqueda textual sea suficiente.
- No se entrenará un modelo propio.

Estas decisiones reducen el costo y el tiempo del MVP. Se revisarán únicamente a partir de mediciones del prototipo.
