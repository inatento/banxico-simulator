# specs/ — desarrollo guiado por especificación (SDD) para el simulador

Este directorio define, antes de programarlas, las mejoras de automatización de pruebas del
simulador (control de empaquetado/reensamblado, variaciones de red, y lo que se derive de ahí).
No reemplaza la especificación funcional/técnica de `HERMES-MKI-VOBEDA` (esa sigue siendo la
fuente de verdad del protocolo real) — este directorio especifica **capacidades nuevas de la
herramienta de prueba misma**, que no existen en el protocolo SPEI real.

## Convención

- Un archivo por spec, numerado (`001-`, `002-`...) en el orden en que se propusieron, no en el
  orden en que se implementan.
- Cada spec sigue la misma forma: Contexto → Requisitos → Fuera de alcance → Diseño propuesto →
  Preguntas abiertas → Criterios de aceptación.
- **Preguntas abiertas es una sección real, no decorativa** — una spec puede mergearse con
  preguntas abiertas sin resolver, siempre que estén marcadas explícitamente y no bloqueen el
  criterio de aceptación mínimo. Se cierran en un commit posterior que las resuelve, citando el
  spec que actualiza.
- Cambios al protocolo/wire format del simulador (no a su tooling de pruebas) siguen siendo R2 —
  ver AGENTS.md §6, dueño de spec: Miguel Zavala.

## Índice

| # | Spec | Estado |
|---|---|---|
| [001](001-particionado-y-empaquetado.md) | Particionado y empaquetado configurable (B↔H) | En progreso |
| [002](002-devoluciones.md) | Devoluciones (B↔H), alcance acotado al simulador | En progreso (B→H) |
| [003](003-reenvio.md) | Reenvío real, solo el alcance de Banxico | Bloqueado (falta arnés) |
| [004](004-firmas-corruptas.md) | Escenarios de firma corrupta (ambos sentidos) | En progreso (B→H) |
| [005](005-variaciones-de-red.md) | Variaciones de red simulables desde la API | Borrador |
| [006](006-folio-duplicado.md) | Folio/clave de rastreo duplicado | Implementado |
| [007](007-renovacion-certificado.md) | Renovación de certificado (`PideCrtNvo`) | Bloqueado (falta arnés) |
| [008](008-msjcatalogos-contenido-real.md) | `MsjCatalogos` con contenido real | Implementado |
| [009](009-perdida-de-iamalive.md) | Pérdida de `IAmAlive` (heartbeat) | Implementado |
| [010](010-reconexion-a-media-transaccion.md) | Reconexión tras caída a media transacción | Borrador (depende de 001) |
| [011](011-mcp.md) | Integración MCP sobre la API de control (arquitectura general) | En progreso |
| [012](012-pruebas-de-volumen.md) | Pruebas de volumen (tasa sostenida y búsqueda de techo) | En progreso |

**2026-09-21 — hallazgo que afecta 003/007 y la parte H→B de 004:** el "arnés Python" que las
tres specs asumían poder extender **no existe en el repo** (`AGENTS.md` lo describe como
"ad-hoc", nunca se comiteó). Decisión: reconstruirlo en Java bajo `src/test/java` (JUnit 5, ya
está en `pom.xml`) en vez de Python, reutilizando las clases de cripto/wire ya existentes —
pendiente de construir, por eso quedan "Bloqueado" y no "Borrador".

## Decisiones de alcance (2026-09-21)

- **Multi-sesión simultánea: excluido explícitamente.** No se especificará ni implementará —
  minos, por diseño (`speiSocket`/`araSocket` son campos singulares en
  `SpeiSocketServiceImpl`/`AraSocketServiceImpl`), no sostiene múltiples sesiones concurrentes
  hacia Banxico. No hay nada real que simular ahí.
- **CoDi: diferido**, no en el alcance actual — se marca como posible implementación futura si el
  equipo lo decide más adelante. No tiene spec todavía.

## Pendientes de convertir en spec

- Ninguno por ahora — todo lo identificado hasta el 2026-09-21 ya tiene spec (borrador).
