#!/usr/bin/env node
// Servidor MCP del simulador (spec 011) -- envuelve la API de control HTTP existente
// (ControlServer.java) como herramientas MCP. No reemplaza la API: cada herramienta de aquí es
// una llamada HTTP directa a un endpoint que ya existe y sigue funcionando igual por curl/
// httpclient/*.http. Sin autenticación propia -- hereda el mismo nivel de acceso que la API de
// control hoy (pensada para uso interno de desarrollo/QA, ver AGENTS.md &sect;6 del repo Java).
//
// Configuración: variable de entorno SIMULATOR_URL, p. ej.
//   SIMULATOR_URL=http://192.168.1.200:8089 node index.js
// Default: http://localhost:8089 (para correr junto al simulador en la misma máquina).

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const BASE_URL = process.env.SIMULATOR_URL ?? "http://localhost:8089";

async function callApi(method, path, body) {
	const res = await fetch(new URL(path, BASE_URL), {
		method,
		headers: body ? { "Content-Type": "application/json" } : undefined,
		body: body ? JSON.stringify(body) : undefined,
	});
	const text = await res.text();
	let parsed;
	try {
		parsed = text ? JSON.parse(text) : {};
	} catch {
		parsed = { raw: text };
	}
	return { status: res.status, body: parsed };
}

function toolResult(result) {
	return {
		content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
		isError: result.status >= 400,
	};
}

const server = new McpServer({
	name: "banxico-simulator",
	version: "0.1.0",
});

server.tool(
	"simulator_health",
	"Liveness del simulador (GET /health). Úsalo primero para confirmar que el simulador está " +
		"arriba antes de disparar cualquier otra herramienta.",
	{},
	async () => toolResult(await callApi("GET", "/health")),
);

server.tool(
	"simulator_session",
	"Estado de la sesión SPEI más reciente (GET /session) -- si minos está conectado, en qué fase " +
		"del handshake, y si sigue viva.",
	{},
	async () => toolResult(await callApi("GET", "/session")),
);

server.tool(
	"simulator_send_abono_valido",
	"Dispara un abono de prueba con contenido válido, tipo de pago 01 fijo (POST /abonos/validos). " +
		"Requiere una sesión SPEI viva -- revisa primero con simulator_session.",
	{},
	async () => toolResult(await callApi("POST", "/abonos/validos")),
);

server.tool(
	"simulator_send_abono_invalido",
	"Dispara un abono de prueba con contenido deliberadamente inválido (RFC roto), tipo de pago 01 " +
		"fijo (POST /abonos/invalidos). Requiere una sesión SPEI viva.",
	{},
	async () => toolResult(await callApi("POST", "/abonos/invalidos")),
);

server.tool(
	"simulator_send_abono",
	"Dispara un abono de cualquier tipo de pago del catálogo real SPEI (0-36, sin 13/14), con " +
		"clave de rastreo y modo de firma configurables (specs 002 devoluciones, 004 firmas, 006 " +
		"folio duplicado). 'campos' debe traer los nombres de campo que ese tipo de pago requiere " +
		"-- ver PaymentType.java en el repo para el catálogo exacto por tipo (los campos opcionales " +
		"pueden omitirse). Requiere una sesión SPEI viva.",
	{
		tipoPg: z.number().int().describe("Código de tipo de pago SPEI (0-36, sin 13 ni 14)."),
		trackingKey: z.string().optional().describe("Clave de rastreo. Si se omite, se autogenera."),
		firma: z.enum(["valida", "vacia", "corrupta"]).default("valida")
			.describe("valida = firma real; vacia = 32 ceros (sin firma); corrupta = firma real con un byte alterado."),
		campos: z.record(z.string(), z.string())
			.describe("Mapa de nombre de campo -> valor, según el catálogo de PaymentType para tipoPg."),
	},
	async ({ tipoPg, trackingKey, firma, campos }) =>
		toolResult(await callApi("POST", "/abonos", { tipoPg, trackingKey, firma, campos })),
);

server.tool(
	"simulator_stop_heartbeat",
	"Suspende deliberadamente el AreYouAlive saliente de la sesión activa (POST /heartbeat/detener, " +
		"spec 009) -- para medir cuánto tarda minos en cerrar la sesión por su timeout de lectura de " +
		"6 segundos. Requiere una sesión SPEI viva.",
	{},
	async () => toolResult(await callApi("POST", "/heartbeat/detener")),
);

server.tool(
	"simulator_list_test_runs",
	"Lista las corridas de prueba persistidas (GET /test-runs), más reciente primero -- cada una " +
		"con su id, canal (SPEI/ARA) y cuándo empezó.",
	{},
	async () => toolResult(await callApi("GET", "/test-runs")),
);

server.tool(
	"simulator_test_run_events",
	"Lista, en orden cronológico, cada mensaje enviado/recibido de una corrida (GET " +
		"/test-runs/{id}/events) -- incluye el hex crudo del wire, útil para diagnosticar " +
		"desalineamientos o fallas de firma comparando contra lo esperado.",
	{ runId: z.number().int().describe("Id de la corrida, de simulator_list_test_runs.") },
	async ({ runId }) => toolResult(await callApi("GET", `/test-runs/${runId}/events`)),
);

server.tool(
	"simulator_start_load_campaign",
	"Spec 012 -- inicia una campaña de volumen que el simulador genera internamente (NO dispares " +
		"esto en un bucle desde el agente -- una sola llamada arranca la campaña completa). Modo " +
		"'sostenida': tasa fija por una duración. Modo 'rampa': sube la tasa hasta la primera falla " +
		"o hasta un tope de seguridad -- útil para encontrar el techo real de esta infraestructura. " +
		"Corre sobre la única sesión SPEI activa. Requiere una sesión SPEI viva.",
	{
		modo: z.enum(["sostenida", "rampa"]),
		tipoPg: z.number().int().describe("Código de tipo de pago SPEI (0-36, sin 13 ni 14)."),
		campos: z.record(z.string(), z.string())
			.describe("Mapa de nombre de campo -> valor, según el catálogo de PaymentType para tipoPg. Se reutiliza para cada envío de la campaña."),
		tasaPorMinuto: z.number().int().optional().describe("Modo sostenida: tasa objetivo fija."),
		duracionSegundos: z.number().int().optional().describe("Modo sostenida: duración; se omite para indefinida (detener manualmente)."),
		tasaInicialPorMinuto: z.number().int().optional().describe("Modo rampa: tasa de arranque."),
		incrementoPorMinuto: z.number().int().optional().describe("Modo rampa: cuánto sube la tasa por escalón."),
		segundosPorEscalon: z.number().int().optional().describe("Modo rampa: cada cuántos segundos sube un escalón."),
		tasaMaxima: z.number().int().optional().describe("Modo rampa: tope de seguridad -- se detiene sola si lo alcanza sin fallar antes."),
	},
	async (args) => toolResult(await callApi("POST", "/abonos/carga", args)),
);

server.tool(
	"simulator_load_campaign_status",
	"Estado y métricas de una campaña de volumen (spec 012): enviados, fallidos, tasa actual, " +
		"si sigue corriendo o ya terminó y por qué.",
	{ id: z.string().describe("Id de campaña, devuelto por simulator_start_load_campaign.") },
	async ({ id }) => toolResult(await callApi("GET", `/abonos/carga/${id}`)),
);

server.tool(
	"simulator_stop_load_campaign",
	"Detiene manualmente una campaña de volumen en curso (spec 012).",
	{ id: z.string().describe("Id de campaña a detener.") },
	async ({ id }) => toolResult(await callApi("POST", `/abonos/carga/${id}/detener`)),
);

const transport = new StdioServerTransport();
await server.connect(transport);
