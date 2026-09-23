# banxico-simulator-mcp

Servidor MCP (spec [011](../specs/011-mcp.md)) que envuelve la API de control HTTP del simulador
(`ControlServer.java`). No la reemplaza — cada herramienta de aquí es una llamada HTTP directa a
un endpoint que sigue funcionando igual por `curl`/`httpclient/*.http`.

## Uso

```bash
npm install
SIMULATOR_URL=http://192.168.1.200:8089 node index.js
```

`SIMULATOR_URL` apunta a donde esté corriendo el simulador (default `http://localhost:8089`, útil
si el MCP corre en la misma máquina). Transporte: stdio (estándar para clientes MCP como Claude
Code) — regístralo en la config de tu cliente MCP apuntando a `node index.js` con esa variable de
entorno.

## Herramientas expuestas

| Herramienta | Endpoint que envuelve |
|---|---|
| `simulator_health` | `GET /health` |
| `simulator_session` | `GET /session` |
| `simulator_send_abono_valido` | `POST /abonos/validos` |
| `simulator_send_abono_invalido` | `POST /abonos/invalidos` |
| `simulator_send_abono` | `POST /abonos` (tipo de pago/clave/firma configurables — specs 002/004/006) |
| `simulator_stop_heartbeat` | `POST /heartbeat/detener` (spec 009) |
| `simulator_list_test_runs` | `GET /test-runs` |
| `simulator_test_run_events` | `GET /test-runs/{id}/events` |

## Sin autenticación propia

Mismo nivel de acceso que la API de control HTTP hoy — pensado para uso interno de desarrollo/QA,
no para exponerse fuera de la red donde corre el simulador. Ver spec 011, "Preguntas abiertas",
para la discusión pendiente sobre si esto necesita cambiar.

## Estado

Cubre exactamente lo que existe en la API de control al día de hoy. Conforme las specs 001, 003,
005, 007, 010 agreguen endpoints nuevos (particionado, variaciones de red, etc.), las
herramientas correspondientes se agregan aquí siguiendo el mismo patrón — no se diseñan por
adelantado sin la API que envuelven ya definida (ver spec 011).
