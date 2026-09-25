# 008 — `MsjCatalogos` con contenido real

## Contexto

Hoy el simulador siempre manda `MsjCatalogos` vacío —
`MsjCatalogosCodec.buildEmptyBody()` (`SpeiSession.java:277`) — suficiente para completar el
handshake (Fase 3), pero nunca se ha probado qué pasa si minos recibe catálogos con contenido
real (nombres de catálogo, formatos, columnas y valores poblados).

## Requisitos

1. El simulador debe poder mandar `MsjCatalogos` con contenido real configurable (no solo el
   cuerpo vacío de hoy) — al menos catálogos de institución y tipo de cuenta, que son justo los
   que `OrderFieldValidator` ya deja como "configurables pero vacíos por defecto" del lado de
   validación de `OrdenTopoV` (ver README, sección de limitaciones conocidas).

## Fuera de alcance

- Generar catálogos "reales" con datos de producción — deben ser sintéticos, coherentes con la
  política de datos sensibles del repo (AGENTS.md §7).

## Diseño propuesto

- Extender `MsjCatalogosCodec` para construir un cuerpo con entradas reales en vez de solo el
  vacío — reutilizando, donde aplique, los mismos catálogos sintéticos que alimenten
  `OrderFieldValidator.setValidInstitucion/setValidTipoCuenta` (conectar ambos: los catálogos que
  el simulador *manda* en `MsjCatalogos` y los que *valida* en `OrdenTopoV` deberían ser
  consistentes entre sí).

## Preguntas abiertas

- ¿Minos hace algo observable distinto al recibir catálogos poblados vs. vacíos, o simplemente
  los ignora igual en ambos casos? Si los ignora, el valor de esta prueba es más limitado de lo
  que parece — vale la pena confirmarlo antes de invertir en generar catálogos sintéticos
  elaborados.
- ¿Qué catálogos priorizar primero — solo institución/tipo de cuenta, o el set completo que
  minos podría esperar?

## Estado de implementación (2026-09-21)

Implementado: `MsjCatalogosCodec.buildPopulatedBody(...)` arma catálogos reales (encabezado:
id/nombre/formato/columnas/renglones), verificado campo por campo contra
`MsjCatalogosMessage.loadProperties()` de minos. Config `catalogos.poblados=true` activa dos
catálogos sintéticos (institución, tipo de cuenta) en vez del cuerpo vacío. **Compilado, no
probado todavía contra minos real** — sigue sin haber evidencia de si minos reacciona distinto
(ver "Preguntas abiertas" arriba).

## Criterios de aceptación

- [ ] El simulador puede mandar `MsjCatalogos` con al menos un catálogo de institución poblado,
      configurable por escenario de prueba.
- [ ] Se documenta (aunque sea como hallazgo, no como código) si minos reacciona distinto a
      catálogos poblados vs. vacíos.
