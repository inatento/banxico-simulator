# 009 — Pérdida de `IAmAlive` (heartbeat)

## Contexto

Mecanismo real, ya documentado y verificado contra minos (`SpeiSession.java:285-295`): minos fija
un timeout de lectura de **6 segundos** en su socket SPEI y cierra la conexión de inmediato ante
cualquier `IOException` de timeout, **sin reintentos**. El emisor del heartbeat es Banxico — el
simulador manda `AreYouAlive` cada 3 segundos (`startHeartbeat()`) y minos responde `IAmAlive`.
Nunca se ha probado deliberadamente qué pasa si el heartbeat se interrumpe.

## Requisitos

1. Poder **suspender** el envío de `AreYouAlive` a mitad de una sesión de prueba (simulando que
   Banxico deja de mandar heartbeats) y confirmar que minos cierra la sesión, midiendo cuánto
   tarda en hacerlo (debería rondar los 6s documentados).
2. Poder simular que el simulador **recibe** un `IAmAlive` de minos con retraso o no lo recibe
   del todo, para observar si el simulador mismo tiene alguna lógica de expiración de sesión que
   dependa de eso (hoy no parece tenerla — solo registra el mensaje al recibirlo, no mide cuánto
   tarda en llegar).

## Fuera de alcance

- Cambiar el intervalo real de heartbeat de producción (3s) — esta spec es sobre **probar** el
  comportamiento existente, no sobre cambiar la cadencia real.

## Diseño propuesto

- Parámetro de escenario que detiene `startHeartbeat()` en un punto configurable de la corrida
  (ej. "después del N-ésimo heartbeat" o "después de mandar tal mensaje") en vez de dejarlo correr
  siempre.
- Medir y registrar en `/test-runs/{id}/events` cuánto tiempo pasa entre la suspensión y el cierre
  de conexión por parte de minos, para confirmar que coincide con el timeout documentado de 6s.

## Preguntas abiertas

- ¿Vale la pena también probar el caso contrario — que el simulador deje de *procesar* (no de
  mandar) heartbeats, para ver si eso afecta algo del lado del simulador mismo? Hoy no hay lógica
  de timeout propia documentada del lado del simulador para la recepción de `IAmAlive`.

## Estado de implementación (2026-09-21)

Implementado: `POST /heartbeat/detener` suspende de inmediato el `AreYouAlive` saliente de la
sesión activa (`SpeiSession.suspendHeartbeat()`). **Compilado, no probado todavía contra minos
real** — falta medir el tiempo real hasta que minos cierre la sesión y confirmar que ronda los
6s documentados.

## Criterios de aceptación

- [ ] Se puede detener el heartbeat saliente en un punto conocido de una corrida de prueba.
- [ ] Se confirma y registra que minos cierra la sesión aproximadamente 6 segundos después de la
      suspensión, no antes ni significativamente después.
