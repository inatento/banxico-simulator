# 002 — Devoluciones (B↔H)

## Contexto

Una devolución no es un tipo de mensaje aparte — es un valor de `tipoPg` dentro de los mensajes
que ya existen: `00`, `16`, `17`, `18`, `23`, `24` en el catálogo de `PaymentType.java`. Aplica en
ambas direcciones del protocolo real:

- Si minos no puede acreditar un abono entrante, le manda a Banxico una devolución vía
  `OrdenTopoV` (H→B).
- Si el pago original lo mandó minos y se lo devuelven, Banxico le acredita la devolución a minos
  vía `Abonos` (B→H).

## Requisitos — alcance decidido (2026-09-21)

**Principio general: operar devoluciones solo dentro de lo que el simulador controla, sin
necesitar operar minos real directamente.**

1. **B→H:** el simulador debe poder **enviar** abonos de tipo devolución (no solo `tipoPg=01`
   como hoy) — es decir, generalizar `AbonosCodec`/`sendTestAbono` para armar el campo `detalle`
   según el catálogo de campos que le corresponda a cada tipo de devolución (`PaymentType.java`),
   no solo el catálogo hardcodeado de `TERCERO_A_TERCERO`.
2. **H→B:** para devoluciones que "envíe" H, el simulador **solo necesita poder devolver las
   respuestas correspondientes** (`AcuseRecibo` con el resultado de validación) — no necesita
   generar contenido de devolución él mismo del lado de minos, ni operar minos real para que
   genere una devolución de verdad. El contenido de prueba (una `OrdenTopoV` con `tipoPg`
   de devolución) se inyecta vía el arnés Python (o directamente a nivel de datos de prueba),
   igual que las órdenes normales de Fase 4.

## Fuera de alcance

- Operar minos real para que genere una devolución de negocio genuina (ej. forzar que rechace un
  abono para que emita su propia devolución) — eso depende de lógica interna de minos que no
  controlamos.
- Cualquier lógica de negocio de "por qué" se genera una devolución — solo se prueba la forma y
  el manejo del mensaje, no las reglas que la disparan.

## Diseño propuesto

- Generalizar `AbonosCodec.AbonoVSpec`/`buildAbonoV` para construir el `detalle` a partir del
  catálogo de `PaymentType` correspondiente al `tipoPg` solicitado, en vez de asumir siempre los
  12 campos de `TERCERO_A_TERCERO`. Los campos opcionales (`Set<String>` en cada entrada del enum)
  ya están declarados — reutilizarlos para decidir qué campos pueden ir vacíos.
- La API de control necesita una forma de pedir un abono de un `tipoPg` específico (hoy
  `POST /abonos/validos` siempre manda `tipoPg=01` hardcodeado) — esto depende de que la API
  soporte parámetros de escenario (ver spec 001, ya implica extender la API más allá de
  válido/inválido fijos).
- Del lado H→B, confirmar que la validación de Fase 4 ya existente (`OrderFieldValidator`) cubre
  correctamente los campos de devolución tal cual están en `PaymentType.java` — probablemente ya
  funciona, falta el escenario de prueba explícito, no código nuevo.

## Preguntas abiertas

- ¿Cuáles de los 6 tipos de devolución priorizamos primero (`00`/`16`/`17`/`18`/`23`/`24`), o se
  cubren todos por igual desde el inicio?
- Los campos "*Original" de una devolución (`cveRastreoPgOriginal`, `montoPgOriginal`, etc.)
  hacen referencia a una orden original real — en un abono de prueba aislado, ¿esos valores son
  arbitrarios/sintéticos, o hace falta simular primero la orden "original" para que la devolución
  tenga sentido de extremo a extremo?

## Estado de implementación (2026-09-21)

Parte B→H lista en código: `POST /abonos` acepta `tipoPg` arbitrario + mapa de campos, y
`PaymentType.buildDetail(...)` arma el detalle correcto para cualquier tipo del catálogo (no solo
`TERCERO_A_TERCERO`). **Compilado, no probado todavía contra minos real** — falta ejercitar al
menos un tipo de devolución (ver criterios de aceptación) antes de marcarlos cumplidos. Parte
H→B sigue bloqueada por la falta del arnés (ver `specs/README.md`).

## Criterios de aceptación

- [ ] El simulador puede mandar un `Abonos` con `tipoPg=17` (devolución acreditada, el catálogo
      más simple: un solo campo) y minos lo procesa sin error estructural.
- [ ] El simulador puede mandar un `Abonos` con `tipoPg=16` (devolución extemporánea, catálogo
      con más campos) igual sin error estructural.
- [ ] Una `OrdenTopoV` entrante con `tipoPg` de devolución recibe el `AcuseRecibo` esperado
      (aceptada si los campos son válidos, rechazada con el motivo correcto si no).
