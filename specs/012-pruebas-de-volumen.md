# 012 — Pruebas de volumen (tasa sostenida y búsqueda de techo)

## Contexto

Objetivo confirmado por diseño: el simulador debe soportar una tasa alta de transacciones
(300-600 abonos/minuto), y además se busca deliberadamente encontrar hasta dónde resiste **esta
infraestructura específica** (no solo validar un número fijo). El día de certificación real
analizado (spec 001) mostró ~26/min como ritmo observado — el objetivo es 10-20x eso.

**El MCP no es la herramienta para generar esta carga** (ver conversación 2026-09-22): un agente
disparando 300-600 tool calls/minuto no es el patrón para el que MCP está pensado (latencia de
ronda por el modelo en cada llamada). El generador de carga debe vivir **dentro del simulador**,
disparado por una sola llamada de control (HTTP directo o MCP), no orquestado mensaje por mensaje
desde afuera.

## Requisitos

1. **Modo sostenido:** lanzar una campaña a una tasa objetivo fija (ej. 500/min) por una duración
   fija, y confirmar que se mandó el número esperado de mensajes en el tiempo esperado.
2. **Modo rampa:** incrementar la tasa progresivamente hasta que aparezcan fallas, para encontrar
   el techo real de esta infraestructura (VM + JVM + la única sesión SPEI activa).
3. Poder consultar el estado/métricas de una campaña en curso o terminada (enviados, fallidos,
   tasa alcanzada).
4. Poder detener una campaña manualmente antes de que termine.
5. Un tope de seguridad configurable para el modo rampa, para no escalar sin límite por error.

## Fuera de alcance

- Generar la carga vía llamadas HTTP externas repetidas (curl en bucle, o un agente MCP
  disparando tool calls) — la campaña corre internamente en el simulador.
- Multi-sesión: la campaña corre sobre la **única** sesión SPEI activa (ver exclusión ya
  decidida en `specs/README.md`) — el techo que se mida es el de "una sola sesión aguantando
  volumen", no el de múltiples participantes concurrentes. Importante no malinterpretar el
  resultado de la prueba de rampa como "capacidad total del sistema".
- Variar el contenido de cada abono de la campaña (tipo de pago, montos distintos por mensaje) —
  la campaña manda el mismo contenido válido repetido, con clave de rastreo única por envío
  (reutiliza el generador de la spec 002/006).

## Diseño propuesto

- `LoadCampaign` (paquete `spei`, junto a `SpeiSession`) — maneja el ciclo de vida de una
  campaña: un `ScheduledExecutorService` que dispara `SpeiSession.sendCustomAbono(...)` al
  intervalo correspondiente a la tasa actual (`60000 / tasaPorMinuto` ms).
  - Modo sostenido: tasa fija, se detiene sola al cumplir la duración (o antes, si se pide).
  - Modo rampa: cada N segundos incrementa la tasa; se detiene automáticamente ante la primera
    falla (reporta la tasa aproximada donde empezó a fallar) o al llegar al tope de seguridad.
  - Clave de rastreo autogenerada por envío (`CARGA-{runId}-{secuencia}`) para evitar colisiones
    a alta frecuencia (un `System.currentTimeMillis()` no basta por sí solo a >16 envíos/segundo).
- Endpoints nuevos en la API de control:
  - `POST /abonos/carga` — inicia una campaña (`modo`, `tipoPg`, `campos`, y parámetros según el
    modo). Devuelve un id de campaña.
  - `GET /abonos/carga/{id}` — estado/métricas (enviados, fallidos, tasa actual, estado).
  - `POST /abonos/carga/{id}/detener` — detiene una campaña en curso.
- Herramientas MCP correspondientes (`simulator_start_load_campaign`, `simulator_load_campaign_status`,
  `simulator_stop_load_campaign`) — el MCP dispara y consulta, nunca genera el tráfico él mismo.

## Preguntas abiertas

- **"minosa" es un ambiente real compartido con el equipo de minos** — la búsqueda deliberada de
  techo (modo rampa) podría saturar algo que ellos también usan para sus propias pruebas.
  ¿Coordinar una ventana con Pedro antes de correr rampa hasta falla, o hay un ambiente de minos
  dedicado para esto que no comparta con otro trabajo en curso?
- ¿El techo que interesa es el del simulador (esta VM, esta JVM, esta sesión), el de minos, o el
  de la red entre ambos? La prueba de rampa mide los tres mezclados — puede hacer falta
  instrumentación adicional para saber cuál es el limitante real cuando aparezcan fallas.
- ¿Se necesita el empaquetado de la spec 001 (varias `AbonoV` por mensaje `Abonos`) para alcanzar
  tasas altas, o el envío de 1-por-mensaje ya alcanza 300-600/min sin problema? Se puede responder
  empíricamente una vez que la campaña esté implementada.

## Estado de implementación (2026-09-23)

Implementado y compilado: `LoadCampaign` (paquete `spei`), los 3 endpoints
(`POST /abonos/carga`, `GET /abonos/carga/{id}`, `POST /abonos/carga/{id}/detener`), y las 3
herramientas MCP correspondientes. Probado con un contenedor local: el ruteo, el parseo de JSON,
y las validaciones (`modo` inválido, campaña inexistente, sin sesión viva) responden con los
códigos HTTP correctos sin excepciones no manejadas.

**No probado todavía:** el generador de carga en sí (`LoadCampaign.sendOne` disparando
`sendCustomAbono` a la tasa configurada) contra una sesión SPEI real -- eso solo se puede
verificar con minos real conectado. Sigue pendiente: coordinar con Pedro antes de correr el modo
rampa contra "minosa" (ver "Preguntas abiertas").

## Criterios de aceptación

- [ ] Se puede lanzar una campaña sostenida a una tasa objetivo (ej. 500/min) por una duración
      fija, y el número de mensajes realmente enviados coincide con lo esperado.
- [ ] Se puede lanzar una campaña en modo rampa y obtener un reporte de en qué tasa aproximada
      empezaron las fallas.
- [ ] Se puede consultar el estado de una campaña en curso o terminada.
- [ ] Se puede detener una campaña manualmente.
- [ ] Existe un tope de seguridad configurable para el modo rampa.
