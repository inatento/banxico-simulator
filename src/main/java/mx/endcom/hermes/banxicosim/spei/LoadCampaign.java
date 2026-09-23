package mx.endcom.hermes.banxicosim.spei;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.endcom.hermes.banxicosim.spei.messages.AbonosCodec;

/**
 * Spec 012 -- genera volumen de {@code Abonos} internamente (un {@link ScheduledExecutorService}
 * disparando {@link SpeiSession#sendCustomAbono}), en vez de depender de que algo externo llame
 * la API de control cientos de veces por minuto. Corre sobre la ÚNICA sesión SPEI activa -- no
 * hay multi-sesión (ver exclusión de alcance en {@code specs/README.md}), así que el techo que
 * mide el modo RAMPA es el de una sola sesión, no el de múltiples participantes concurrentes.
 */
public final class LoadCampaign {

	private static final Logger logger = LoggerFactory.getLogger(LoadCampaign.class);

	public enum Mode {
		SOSTENIDA, RAMPA
	}

	public enum Estado {
		CORRIENDO, DETENIDA, TERMINADA
	}

	private final String id;
	private final SpeiSession session;
	private final Mode mode;
	private final int tipoPg;
	private final Map<String, String> campos;

	// Modo sostenido
	private final Long duracionSegundos;
	// Modo rampa
	private final int incrementoPorMinuto;
	private final int segundosPorEscalon;
	private final int tasaMaxima;

	private volatile int tasaActualPorMinuto;
	private final AtomicInteger enviados = new AtomicInteger();
	private final AtomicInteger fallidos = new AtomicInteger();
	private final AtomicInteger secuencia = new AtomicInteger();
	private volatile Estado estado = Estado.CORRIENDO;
	private volatile String detalleFinalizacion;
	private final Instant inicio = Instant.now();
	private volatile Instant fin;

	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> envioTask;

	private LoadCampaign(String id, SpeiSession session, Mode mode, int tipoPg, Map<String, String> campos,
			int tasaInicialPorMinuto, Long duracionSegundos, int incrementoPorMinuto, int segundosPorEscalon,
			int tasaMaxima) {
		this.id = id;
		this.session = session;
		this.mode = mode;
		this.tipoPg = tipoPg;
		this.campos = campos;
		this.tasaActualPorMinuto = tasaInicialPorMinuto;
		this.duracionSegundos = duracionSegundos;
		this.incrementoPorMinuto = incrementoPorMinuto;
		this.segundosPorEscalon = segundosPorEscalon;
		this.tasaMaxima = tasaMaxima;
	}

	/** Modo sostenido: tasa fija durante {@code duracionSegundos} (o indefinida si es null, hasta
	 *  que se detenga manualmente). */
	public static LoadCampaign sostenida(String id, SpeiSession session, int tipoPg,
			Map<String, String> campos, int tasaPorMinuto, Long duracionSegundos) {
		return new LoadCampaign(id, session, Mode.SOSTENIDA, tipoPg, campos, tasaPorMinuto,
				duracionSegundos, 0, 0, tasaPorMinuto);
	}

	/** Modo rampa: empieza en {@code tasaInicialPorMinuto} y sube {@code incrementoPorMinuto} cada
	 *  {@code segundosPorEscalon}, hasta la primera falla o hasta {@code tasaMaxima} (tope de
	 *  seguridad) sin fallas. */
	public static LoadCampaign rampa(String id, SpeiSession session, int tipoPg, Map<String, String> campos,
			int tasaInicialPorMinuto, int incrementoPorMinuto, int segundosPorEscalon, int tasaMaxima) {
		return new LoadCampaign(id, session, Mode.RAMPA, tipoPg, campos, tasaInicialPorMinuto,
				null, incrementoPorMinuto, segundosPorEscalon, tasaMaxima);
	}

	public String id() {
		return id;
	}

	public synchronized void start() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "load-campaign-" + id);
			t.setDaemon(true);
			return t;
		});
		scheduleAtCurrentRate();
		if (mode == Mode.RAMPA) {
			scheduler.scheduleAtFixedRate(this::bumpRate, segundosPorEscalon, segundosPorEscalon, TimeUnit.SECONDS);
		}
		if (mode == Mode.SOSTENIDA && duracionSegundos != null) {
			scheduler.schedule(() -> finish(Estado.TERMINADA, "duración alcanzada"),
					duracionSegundos, TimeUnit.SECONDS);
		}
		logger.info("[Carga:{}] Iniciada, modo={}, tasa inicial={}/min", id, mode, tasaActualPorMinuto);
	}

	private synchronized void scheduleAtCurrentRate() {
		if (envioTask != null) {
			envioTask.cancel(false);
		}
		long intervalMs = Math.max(1, 60_000L / tasaActualPorMinuto);
		envioTask = scheduler.scheduleAtFixedRate(this::sendOne, 0, intervalMs, TimeUnit.MILLISECONDS);
	}

	private void sendOne() {
		if (estado != Estado.CORRIENDO) {
			return;
		}
		try {
			String trackingKey = "CARGA-" + id + "-" + secuencia.incrementAndGet();
			session.sendCustomAbono(tipoPg, trackingKey, campos, AbonosCodec.SignatureMode.VALIDA);
			enviados.incrementAndGet();
		} catch (Exception e) {
			fallidos.incrementAndGet();
			logger.warn("[Carga:{}] Falla al enviar a tasa {}/min: {}", id, tasaActualPorMinuto, e.getMessage());
			if (mode == Mode.RAMPA) {
				finish(Estado.TERMINADA,
						"Falla detectada en modo rampa a " + tasaActualPorMinuto + "/min: " + e.getMessage());
			}
		}
	}

	private void bumpRate() {
		if (estado != Estado.CORRIENDO) {
			return;
		}
		int nueva = tasaActualPorMinuto + incrementoPorMinuto;
		if (nueva > tasaMaxima) {
			finish(Estado.TERMINADA, "Tope de seguridad alcanzado (" + tasaMaxima + "/min) sin fallas");
			return;
		}
		tasaActualPorMinuto = nueva;
		logger.info("[Carga:{}] Escalón de rampa: {}/min", id, tasaActualPorMinuto);
		scheduleAtCurrentRate();
	}

	public synchronized void stop() {
		if (estado == Estado.CORRIENDO) {
			finish(Estado.DETENIDA, "Detenida manualmente");
		}
	}

	private synchronized void finish(Estado nuevoEstado, String detalle) {
		if (estado != Estado.CORRIENDO) {
			return;
		}
		estado = nuevoEstado;
		detalleFinalizacion = detalle;
		fin = Instant.now();
		logger.info("[Carga:{}] Finalizada: {} (enviados={}, fallidos={})", id, detalle, enviados.get(), fallidos.get());
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
	}

	public Map<String, Object> status() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", id);
		m.put("modo", mode.name());
		m.put("estado", estado.name());
		m.put("tasaActualPorMinuto", tasaActualPorMinuto);
		m.put("enviados", enviados.get());
		m.put("fallidos", fallidos.get());
		m.put("inicio", inicio.toString());
		m.put("fin", fin == null ? null : fin.toString());
		m.put("detalleFinalizacion", detalleFinalizacion);
		return m;
	}
}
