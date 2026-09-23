# 006 — Folio/clave de rastreo duplicado

## Contexto

Cada transacción (abono u orden) lleva una clave de rastreo (`cveRastreo`/`trackingKey`) que
debería ser única por operación. Hoy no existe ningún escenario de prueba que mande la misma
clave dos veces — `sendTestAbono` genera la suya con `"SIMU" + System.currentTimeMillis()`
(`SpeiSession.java:448`), siempre distinta.

## Requisitos

1. **B→H:** poder mandar dos `Abonos` con la **misma** clave de rastreo, en la misma sesión o en
   sesiones distintas, y observar cómo reacciona minos (¿acepta ambas? ¿rechaza la segunda?
   ¿algún otro comportamiento?).
2. **H→B:** confirmar si la validación de Fase 4 del simulador (`OrderFieldValidator`) ya detecta
   o no una `OrdenTopoV` entrante con `cveRastreo` repetida — si no lo hace hoy, decidir si es
   responsabilidad real de Banxico detectarlo (y por lo tanto hay que agregarlo) o si es
   responsabilidad de minos evitarlo desde el origen.

## Fuera de alcance

- Deduplicación a nivel de negocio de minos (por qué mandaría uno duplicado) — solo se prueba
  la mecánica del mensaje repetido, no el motivo.

## Diseño propuesto

- Agregar un parámetro a la API de control para fijar explícitamente la clave de rastreo del
  abono de prueba (hoy es siempre generada, no hay forma de repetirla a propósito) — esto además
  es un prerequisito compartido con otras specs que necesiten control fino del contenido
  (002, 004).

## Preguntas abiertas

- ¿"Duplicado" se prueba dentro de la misma sesión SPEI, entre sesiones distintas, o ambas? El
  comportamiento esperado de minos podría diferir.
- ¿Existe ya, del lado de minos, una regla de negocio conocida para folios duplicados que debamos
  simplemente confirmar, o es un área sin definir todavía incluso para minos?

## Referencia de un día de certificación real (analizado 2026-09-21)

En el día de certificación revisado (`minosA.log.2025-09-12.1`, 21 mensajes `Abonos`) no se
observó ninguna clave de rastreo repetida — confirma que en flujo normal esto no ocurre; el valor
de esta spec es exclusivamente como prueba deliberada de robustez, no como algo que se espere ver
en producción.

## Estado de implementación (2026-09-21)

Implementado: `POST /abonos` acepta `trackingKey` explícita (antes siempre autogenerada). Mandar
la misma clave dos veces es tan simple como llamar el endpoint dos veces con el mismo valor —
no hace falta código adicional para el escenario en sí, solo ejercitarlo y observar la respuesta
de minos. **Compilado, no probado todavía contra minos real.**

## Criterios de aceptación

- [ ] Se puede disparar un abono de prueba con una clave de rastreo específica (no autogenerada)
      desde la API de control.
- [ ] Mandar la misma clave dos veces produce un resultado observable y documentado (aceptado,
      rechazado, o lo que sea que haga minos), registrado en `/test-runs/{id}/events`.
