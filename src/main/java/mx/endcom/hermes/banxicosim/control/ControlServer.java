package mx.endcom.hermes.banxicosim.control;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import mx.endcom.hermes.banxicosim.persistence.H2Store;
import mx.endcom.hermes.banxicosim.spei.SpeiServer;
import mx.endcom.hermes.banxicosim.spei.SpeiSession;

/**
 * API de control HTTP del simulador. No es parte del protocolo SPEI/ARA (minos no le habla a
 * este servidor) -- es una herramienta adicional para disparar y observar corridas de prueba con
 * clientes HTTP simples (ver {@code httpclient/*.http} en la raíz del repo), sin depender de la
 * consola stdin que ya expone {@code Main}. Usa {@link HttpServer} del JDK, incluido desde Java
 * 6, para no agregar una dependencia nueva (ver AGENTS.md &sect;4, "sin frameworks").
 *
 * <p>Endpoints (ver README.md &sect;"API de control y flujo de pruebas" para el contrato
 * completo):</p>
 * <ul>
 *   <li>{@code GET /health} -- liveness del propio simulador.</li>
 *   <li>{@code GET /session} -- estado de la sesión SPEI más reciente (o {@code null}).</li>
 *   <li>{@code POST /abonos/validos} / {@code POST /abonos/invalidos} -- mismo camino que los
 *       comandos de consola {@code abono}/{@code abono-invalido}.</li>
 *   <li>{@code GET /test-runs} -- corridas de prueba persistidas en H2 (Fase 6).</li>
 *   <li>{@code GET /test-runs/{id}/events} -- eventos de una corrida.</li>
 * </ul>
 */
public final class ControlServer {

	private static final Logger logger = LoggerFactory.getLogger(ControlServer.class);
	private static final Pattern RUN_EVENTS_PATH = Pattern.compile("^/test-runs/(\\d+)/events/?$");

	private final HttpServer httpServer;
	private final ExecutorService executor = Executors.newCachedThreadPool();

	public ControlServer(int port, SpeiServer speiServer, H2Store store) throws IOException {
		this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		httpServer.createContext("/health", exchange -> dispatch(exchange, this::health));
		httpServer.createContext("/session", exchange -> dispatch(exchange, ex -> session(ex, speiServer)));
		httpServer.createContext("/abonos/validos",
				exchange -> dispatch(exchange, ex -> triggerAbono(ex, speiServer, true)));
		httpServer.createContext("/abonos/invalidos",
				exchange -> dispatch(exchange, ex -> triggerAbono(ex, speiServer, false)));
		httpServer.createContext("/test-runs", exchange -> dispatch(exchange, ex -> testRuns(ex, store)));
		httpServer.setExecutor(executor);
	}

	public void start() {
		httpServer.start();
		logger.info("[Control] API de control HTTP escuchando en el puerto {}", httpServer.getAddress().getPort());
	}

	public void stop() {
		httpServer.stop(0);
		executor.shutdownNow();
	}

	// ---- Endpoints ----

	private Response health(HttpExchange exchange) {
		if (!"GET".equals(exchange.getRequestMethod())) {
			return Response.methodNotAllowed();
		}
		return Response.ok(Map.of("status", "ok"));
	}

	private Response session(HttpExchange exchange, SpeiServer speiServer) {
		if (!"GET".equals(exchange.getRequestMethod())) {
			return Response.methodNotAllowed();
		}
		SpeiSession session = speiServer.lastSession();
		if (session == null) {
			return Response.ok(sessionMap(null));
		}
		return Response.ok(sessionMap(session));
	}

	private Map<String, Object> sessionMap(SpeiSession session) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (session == null) {
			body.put("session", null);
			return body;
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("runId", session.runId());
		details.put("channel", "SPEI");
		details.put("remoteAddress", session.remoteAddress());
		details.put("alive", session.isAlive());
		details.put("fase", session.phase().name());
		details.put("diaOperativo", session.operationalDate() == null ? null : session.operationalDate().toString());
		body.put("session", details);
		return body;
	}

	private Response triggerAbono(HttpExchange exchange, SpeiServer speiServer, boolean valid) {
		if (!"POST".equals(exchange.getRequestMethod())) {
			return Response.methodNotAllowed();
		}
		SpeiSession session = speiServer.lastSession();
		if (session == null || !session.isAlive()) {
			return Response.of(409, Map.of(
					"error", "no-hay-sesion-viva",
					"detalle", "No hay una sesión SPEI viva todavía -- conecta minos primero (ver README)."));
		}
		session.sendTestAbono(valid);
		return Response.ok(Map.of(
				"status", "enviado",
				"valido", valid,
				"runId", session.runId()));
	}

	private Response testRuns(HttpExchange exchange, H2Store store) {
		if (!"GET".equals(exchange.getRequestMethod())) {
			return Response.methodNotAllowed();
		}
		String path = exchange.getRequestURI().getPath();
		if (path.equals("/test-runs") || path.equals("/test-runs/")) {
			var runs = store.listRuns().stream().map(run -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", run.id());
				m.put("startedAt", run.startedAt().toString());
				m.put("remoteAddress", run.remoteAddress());
				m.put("channel", run.channel());
				return (Object) m;
			}).toList();
			return Response.ok(Map.of("runs", runs));
		}

		Matcher matcher = RUN_EVENTS_PATH.matcher(path);
		if (matcher.matches()) {
			long runId;
			try {
				runId = Long.parseLong(matcher.group(1));
			} catch (NumberFormatException e) {
				return Response.of(400, Map.of("error", "id-invalido"));
			}
			var events = store.listEventsForRun(runId).stream().map(ev -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("id", ev.id());
				m.put("runId", ev.runId());
				m.put("occurredAt", ev.occurredAt().toString());
				m.put("direction", ev.direction());
				m.put("messageName", ev.messageName());
				m.put("opCode", ev.opCode());
				m.put("result", ev.result());
				m.put("detail", ev.detail());
				m.put("rawHex", ev.rawHex());
				return (Object) m;
			}).toList();
			return Response.ok(Map.of("runId", runId, "events", events));
		}

		return Response.of(404, Map.of("error", "ruta-no-encontrada"));
	}

	// ---- Infraestructura interna del handler ----

	private interface EndpointHandler {
		Response handle(HttpExchange exchange) throws Exception;
	}

	private record Response(int status, Object body) {
		static Response ok(Object body) {
			return new Response(200, body);
		}

		static Response of(int status, Object body) {
			return new Response(status, body);
		}

		static Response methodNotAllowed() {
			return new Response(405, Map.of("error", "metodo-no-soportado"));
		}
	}

	private void dispatch(HttpExchange exchange, EndpointHandler handler) {
		try {
			Response response = handler.handle(exchange);
			writeJson(exchange, response.status(), response.body());
		} catch (Exception e) {
			logger.error("[Control] Error atendiendo {} {}: {}",
					exchange.getRequestMethod(), exchange.getRequestURI(), e.getMessage(), e);
			try {
				writeJson(exchange, 500, Map.of("error", "error-interno", "detalle", String.valueOf(e.getMessage())));
			} catch (IOException ignored) {
				// no hay mucho más que hacer si ni siquiera se puede mandar el error
			}
		} finally {
			exchange.close();
		}
	}

	private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, payload.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(payload);
		}
	}
}
