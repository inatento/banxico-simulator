# 003 — Reenvío real (solo el alcance de Banxico)

## Contexto

`Reenvio` (código 207) lo manda **siempre minos** — confirmado en el código real de minos:
`core/spei/dto/out/Reenvio.java` y `core/spei/message/out/ReenvioMessage.java` (paquete `out` =
lo que minos envía). `FinReenvio` (código 32) lo manda siempre Banxico en respuesta — confirmado
igual, `dto/in/FinReenvio.java`/`message/in/FinReenvioMessage.java` del lado de minos (paquete
`in` = lo que minos recibe). Es unidireccional: minos pide, Banxico responde.

Hoy `SpeiSession.handleReenvio` (`SpeiSession.java:364`) recibe el `Reenvio` y contesta
`FinReenvio` **sin reenvío real** — es un stub, documentado como limitación conocida de v1.

## Requisitos — alcance decidido (2026-09-21)

**Principio: el simulador solo implementa lo que le toca a Banxico en un escenario real — no es
responsable de lo que minos decida o necesite hacer.**

En el protocolo real, `Reenvio.processedBytes` le dice a Banxico "esto es lo último que
procesé de lo que me mandaste, reenvía lo que sigue". El único trabajo real de Banxico aquí es:
llevar un registro de lo que ya mandó en la sesión, y si le piden reenvío desde cierto punto,
reenviar exactamente eso — nada más.

1. El simulador debe llevar un historial de los frames/bytes que ha mandado en la sesión SPEI
   activa (suficiente para poder reconstituir "todo lo mandado después del byte N").
2. Al recibir un `Reenvio` con `processedBytes = N`, debe reenviar real y correctamente todo lo
   que mandó después de esa posición — no simplemente responder `FinReenvio` vacío como hoy.

## Fuera de alcance

- Por qué minos decide pedir un reenvío, cuándo lo hace, o cómo maneja lo que recibe — eso es
  responsabilidad y lógica interna de minos, no se simula ni se prueba desde este simulador.
- Forzar a minos real a pedir un reenvío deliberadamente — las pruebas de este escenario se
  ejercitan haciendo que el **arnés Python** ("minos falso") mande un `Reenvio` con un
  `processedBytes` arbitrario en el punto de la conversación que se quiera probar.

## Diseño propuesto

- Buffer de historial de envío por sesión (`SpeiSession`), acotado — no necesita ser ilimitado,
  pero sí cubrir razonablemente el tamaño de una sesión de prueba completa.
- `handleReenvio` calcula el offset faltante contra ese historial y reenvía los frames
  correspondientes, en vez de responder `FinReenvio` de inmediato.

## Preguntas abiertas

- ¿Qué tan grande debe ser el historial retenido? (¿toda la sesión, o una ventana de los últimos
  N mensajes/bytes?) — impacta memoria si las sesiones de prueba son largas.
- ¿Reenviar significa repetir los frames *tal cual* se mandaron (mismo cifrado, misma firma), o
  hay que re-cifrar/re-firmar al reenviar? Verificar contra el código real de minos qué espera
  recibir en un reenvío.

## Criterios de aceptación

- [ ] El arnés Python, tras recibir una parte de la conversación, manda `Reenvio` con
      `processedBytes` apuntando a un punto intermedio conocido, y el simulador reenvía
      exactamente los bytes correspondientes (verificable byte a byte contra lo que ya se había
      mandado, registrado en `/test-runs/{id}/events`).
- [ ] Un `Reenvio` con `processedBytes` igual al total ya mandado (nada que reenviar) se responde
      con `FinReenvio` sin contenido adicional, sin error.
