package mx.endcom.hermes.banxicosim.spei;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.endcom.hermes.banxicosim.config.SimConfig;
import mx.endcom.hermes.banxicosim.crypto.RsaCipher;
import mx.endcom.hermes.banxicosim.crypto.SimulatorIdentity;
import mx.endcom.hermes.banxicosim.persistence.H2Store;
import mx.endcom.hermes.banxicosim.spei.messages.AbonosCodec;
import mx.endcom.hermes.banxicosim.spei.messages.AcuseReciboCodec;
import mx.endcom.hermes.banxicosim.spei.messages.ClvSimCodec;
import mx.endcom.hermes.banxicosim.spei.messages.EnSesionCodec;
import mx.endcom.hermes.banxicosim.spei.messages.MsjCatalogosCodec;
import mx.endcom.hermes.banxicosim.spei.messages.OrdenTopoVCodec;
import mx.endcom.hermes.banxicosim.spei.messages.ReenvioCodec;
import mx.endcom.hermes.banxicosim.validation.OrderFieldValidator;
import mx.endcom.hermes.banxicosim.wire.ByteReader;
import mx.endcom.hermes.banxicosim.crypto.AesCipher;

/**
 * Maneja una conexión del socket SPEI principal: todo el enlace (Fases 1-3) y la fase de
 * operación (Fases 4-5) para esa conexión.
 *
 * <p>Secuencia (ver 02_especificacion_tecnica.md &sect;3, verificada contra el código real de
 * minos — ver citas en cada paso):</p>
 * <ol>
 *   <li>Espera {@code ConexionMessage} (código 16, sin cuerpo) — lo manda minos primero
 *       ({@code MinosServiceImpl.java:239-241}).</li>
 *   <li>Manda {@code GreetingMessage} (código 247).</li>
 *   <li>Manda {@code SmLoginReqMessage} (código 254); espera {@code LoginMessage} (código 1).</li>
 *   <li>Manda {@code ClvSimMessage} (código 80); espera {@code RespClvSimMessage} (código 221) —
 *       a partir de aquí hay llave de sesión AES.</li>
 *   <li>Manda {@code EnSesionMessage} (código 13, particionado sin cifrar) y
 *       {@code MsjCatalogosMessage} (código 31, cifrado AES). La sesión queda "viva".</li>
 *   <li>Atiende {@code Reenvio}/{@code InicioSesionCifrada} (mensajes que minos manda sin que la
 *       spec técnica los liste explícitamente, pero que el código real sí exige — ver
 *       {@code ReenvioCodec} y el manejo de InicioSesionCifrada abajo) y, en operación,
 *       {@code OrdenTopoV} (Fase 4) y puede mandar {@code Abonos} (Fase 5).</li>
 * </ol>
 */
public final class SpeiSession implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(SpeiSession.class);

	private final Socket socket;
	private final SimulatorIdentity identity;
	private final PublicKey minosPublicKey; // para cifrar ClvSim y verificar firmas de minos
	private final SimConfig config;
	private final H2Store store;
	private final long runId;
	private final OrderFieldValidator validator = new OrderFieldValidator();

	private DataOutputStream out;
	private byte[] sessionKey;
	private byte[] sessionIv;
	private volatile boolean alive = false;

	public SpeiSession(Socket socket, SimulatorIdentity identity, PublicKey minosPublicKey, SimConfig config,
			H2Store store) {
		this.socket = socket;
		this.identity = identity;
		this.minosPublicKey = minosPublicKey;
		this.config = config;
		this.store = store;
		this.runId = store.newRun("SPEI", socket.getRemoteSocketAddress().toString());
	}

	public boolean isAlive() {
		return alive;
	}

	@Override
	public void run() {
		try (socket;
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())) {
			this.out = dataOut;
			logger.info("[SPEI] Conexión entrante de {}", socket.getRemoteSocketAddress());

			awaitConexion(in);
			sendGreeting();
			sendSmLoginReq();
			awaitLogin(in);
			ClvSimCodec.SessionKeys keys = performClvSim(in);
			this.sessionKey = keys.key();
			this.sessionIv = keys.iv();
			sendEnSesion();
			sendMsjCatalogos();
			alive = true;
			logger.info("[SPEI] Sesión viva (handshake completo, fases 1-3 cumplidas)");

			mainLoop(in);
		} catch (java.io.EOFException eof) {
			logger.info("[SPEI] Conexión cerrada por minos ({})", socket.getRemoteSocketAddress());
		} catch (Exception e) {
			logger.error("[SPEI] Sesión terminada con error: {}", e.getMessage(), e);
		} finally {
			alive = false;
		}
	}

	// ---- Fase 1 ----

	private void awaitConexion(DataInputStream in) throws Exception {
		Frame frame = Frame.read(in);
		if (frame.operation() != SpeiProtocol.OP_CONEXION) {
			throw new IllegalStateException("Se esperaba ConexionMessage (16), llegó " + frame.operation());
		}
		store.logEvent(runId, "IN", "Conexion", frame.operation(), "recibido", null, null);
		logger.info("[SPEI] << Conexion");
	}

	private void sendGreeting() throws Exception {
		Frame.of(SpeiProtocol.OP_GREETING, new byte[0]).writeTo(out);
		store.logEvent(runId, "OUT", "Greeting", SpeiProtocol.OP_GREETING, "enviado", null, null);
		logger.info("[SPEI] >> Greeting");
	}

	// ---- Fase 2 ----

	private void sendSmLoginReq() throws Exception {
		Frame.of(SpeiProtocol.OP_SMLOGINREQ, new byte[0]).writeTo(out);
		store.logEvent(runId, "OUT", "SmLoginReq", SpeiProtocol.OP_SMLOGINREQ, "enviado", null, null);
		logger.info("[SPEI] >> SmLoginReq");
	}

	private void awaitLogin(DataInputStream in) throws Exception {
		Frame frame = Frame.read(in);
		if (frame.operation() != SpeiProtocol.OP_LOGIN) {
			throw new IllegalStateException("Se esperaba LoginMessage (1), llegó " + frame.operation());
		}
		String user = new ByteReader(frame.body()).readCString();
		store.logEvent(runId, "IN", "Login", frame.operation(), "recibido", "usuario=" + user, frame.body());
		logger.info("[SPEI] << Login, usuario minos: {}", user);
	}

	private ClvSimCodec.SessionKeys performClvSim(DataInputStream in) throws Exception {
		if (minosPublicKey == null) {
			logger.warn("[SPEI] No hay llave pública de minos configurada; ClvSim se manda igual "
					+ "pero minos NO podrá desencriptar la llave de sesión. Ver README.");
		}
		ClvSimCodec.ClvSimBody clvSim = ClvSimCodec.build(minosPublicKey, identity.privateKey());
		Frame.of(SpeiProtocol.OP_CLVSIM, clvSim.bytes()).writeTo(out);
		store.logEvent(runId, "OUT", "ClvSim", SpeiProtocol.OP_CLVSIM, "enviado", null, clvSim.bytes());
		logger.info("[SPEI] >> ClvSim (reto RSA de sesión)");

		Frame resp = Frame.read(in);
		if (resp.operation() != SpeiProtocol.OP_RESP_CLVSIM) {
			throw new IllegalStateException("Se esperaba RespClvSim (221), llegó " + resp.operation());
		}
		ClvSimCodec.RespClvSimResult result = ClvSimCodec.verifyResponse(resp.body(),
				clvSim.sessionKeys().rawSymmetricKey(), minosPublicKey);
		store.logEvent(runId, "IN", "RespClvSim", resp.operation(), "recibido",
				"firmaVerificada=" + result.signatureVerified(), resp.body());
		logger.info("[SPEI] << RespClvSim, firma verificada={}", result.signatureVerified());
		return clvSim.sessionKeys();
	}

	// ---- Fase 3 ----

	private void sendEnSesion() throws Exception {
		EnSesionCodec.EntityCert own = new EnSesionCodec.EntityCert(
				config.ownEntityCode(), "BANXICOSIM", identity.certificateNumber());
		EnSesionCodec.EntityCert minos = new EnSesionCodec.EntityCert(
				config.minosEntityCode(), config.minosEntityName(), config.minosCertificateNumber());

		byte[] payload = EnSesionCodec.buildBody(
				LocalDate.now(), 65535, 4096, own, minos,
				"simulador-hermes-banxico".getBytes(StandardCharsets.ISO_8859_1), 20);
		byte[] body = WireFraming.withLengthPrefix(payload);
		Frame.of(SpeiProtocol.OP_ENSESION, body).writeTo(out);
		store.logEvent(runId, "OUT", "EnSesion", SpeiProtocol.OP_ENSESION, "enviado", null, body);
		logger.info("[SPEI] >> EnSesion (entidad propia={}, entidad minos={})",
				config.ownEntityCode(), config.minosEntityCode());
	}

	private void sendMsjCatalogos() throws Exception {
		byte[] payload = MsjCatalogosCodec.buildEmptyBody();
		byte[] body = WireFraming.buildEncryptedPartitioned(payload, sessionKey, sessionIv);
		Frame.of(SpeiProtocol.OP_MSJCATALOGOS, body).writeTo(out);
		store.logEvent(runId, "OUT", "MsjCatalogos", SpeiProtocol.OP_MSJCATALOGOS, "enviado", null, body);
		logger.info("[SPEI] >> MsjCatalogos (catálogos vacíos, v1)");
	}

	// ---- Operación (Fases 4-5) ----

	private void mainLoop(DataInputStream in) throws Exception {
		while (!socket.isClosed()) {
			Frame frame = Frame.read(in);
			switch (frame.operation()) {
				case SpeiProtocol.OP_INICIO_SESION_CIFRADA -> handleInicioSesionCifrada(frame);
				case 207 -> handleReenvio(frame); // ReenvioMessage.MSG_CODE, ver ReenvioCodec
				case SpeiProtocol.OP_ORDEN_TOPOV -> handleOrdenTopoV(frame);
				case SpeiProtocol.OP_IAMALIVE -> logger.info("[SPEI] << IAmAlive");
				case SpeiProtocol.OP_DEADSRVR, SpeiProtocol.OP_SMTTYCLOSE, SpeiProtocol.OP_NOSERVICE -> {
					logger.info("[SPEI] minos cerró la sesión (op {})", frame.operation());
					return;
				}
				default -> logger.warn("[SPEI] Código de operación no manejado en v1: {} ({} bytes de cuerpo)",
						frame.operation(), frame.body().length);
			}
		}
	}

	private void handleInicioSesionCifrada(Frame frame) {
		try {
			ByteReader r = new ByteReader(frame.body());
			String minosCertNumber = r.readCString();
			String encryptedRandomB64 = r.readCString();
			String signatureB64 = r.readCString();
			byte[] nonce = RsaCipher.decryptFromBase64(
					encryptedRandomB64.getBytes(StandardCharsets.US_ASCII), identity.privateKey());
			boolean verified = false;
			if (minosPublicKey != null) {
				byte[] rawSig = RsaCipher.decodeBase64(signatureB64.getBytes(StandardCharsets.US_ASCII));
				verified = RsaCipher.verify(nonce, rawSig, minosPublicKey);
			}
			store.logEvent(runId, "IN", "InicioSesionCifrada", frame.operation(), "recibido",
					"firmaVerificada=" + verified, frame.body());
			logger.info("[SPEI] << InicioSesionCifrada, nonce desencriptado correctamente, firma verificada={}",
					verified);
		} catch (Exception e) {
			logger.warn("[SPEI] No fue posible procesar InicioSesionCifrada: {}", e.getMessage());
		}
	}

	private void handleReenvio(Frame frame) throws Exception {
		byte[] plaintext = AesCipher.decrypt(frame.body(), sessionKey, sessionIv);
		ReenvioCodec.Reenvio reenvio = ReenvioCodec.parse(plaintext);
		store.logEvent(runId, "IN", "Reenvio", frame.operation(), "recibido",
				"bytesProcesados=" + reenvio.processedBytes(), frame.body());
		logger.info("[SPEI] << Reenvio (bytesProcesados={}), respondo FinReenvio sin reenvÍo real (v1)",
				reenvio.processedBytes());

		byte[] finReenvioPlain = ReenvioCodec.buildFinReenvioBody();
		byte[] finReenvioCipher = AesCipher.encrypt(finReenvioPlain, sessionKey, sessionIv);
		Frame.of(32, finReenvioCipher).writeTo(out); // FinReenvioMessage.OP = 32 (ver ToSpeiInputMessage)
		store.logEvent(runId, "OUT", "FinReenvio", 32, "enviado", null, finReenvioCipher);
		logger.info("[SPEI] >> FinReenvio");
	}

	private void handleOrdenTopoV(Frame frame) throws Exception {
		WireFraming.Unwrapped unwrapped = WireFraming.unwrapEncryptedSignedPartitioned(
				frame.body(), sessionKey, sessionIv, minosPublicKey);
		OrdenTopoVCodec.ParsedOrdenTopoV orden = OrdenTopoVCodec.parse(unwrapped.payload());
		store.logEvent(runId, "IN", "OrdenTopoV", frame.operation(), "recibido",
				"folioPack=" + orden.folioPack() + " ordenes=" + orden.orders().size()
						+ " firmaVerificada=" + unwrapped.signatureVerified(),
				frame.body());
		logger.info("[SPEI] << OrdenTopoV folioPack={}, {} orden(es), firma verificada={}",
				orden.folioPack(), orden.orders().size(), unwrapped.signatureVerified());

		List<AcuseReciboCodec.OrderError> errors = new ArrayList<>();
		for (OrdenTopoVCodec.Order order : orden.orders()) {
			OrderFieldValidator.OrderContext ctx = new OrderFieldValidator.OrderContext(
					order.paymentType(), order.trackingKey(), order.amount(),
					orden.entityCode(), orden.receptorEntityCode());
			String[] detailFields = OrdenTopoVCodec.splitDetailFields(order.detail());
			List<String> validationErrors = validator.validate(ctx, detailFields);
			if (validationErrors.isEmpty()) {
				logger.info("[SPEI]   orden folioInterno={} tipoPg={} claveRastreo={}: ACEPTADA",
						order.internalFolio(), order.paymentType(), order.trackingKey());
			} else {
				logger.warn("[SPEI]   orden folioInterno={} tipoPg={} claveRastreo={}: RECHAZADA -> {}",
						order.internalFolio(), order.paymentType(), order.trackingKey(), validationErrors);
				errors.add(new AcuseReciboCodec.OrderError(order.internalFolio(), (char) 1));
			}
		}

		char status = errors.isEmpty() ? AcuseReciboCodec.STATUS_ACCEPTED : AcuseReciboCodec.STATUS_REJECTED;
		byte[] acusePayload = AcuseReciboCodec.buildBody(
				orden.operationDate(), orden.folioPack(), orden.entityIndex(), orden.entityCode(), status, errors);
		byte[] acuseBody = WireFraming.withLengthPrefix(acusePayload);
		Frame.of(SpeiProtocol.OP_ACUSERECIBO, acuseBody).writeTo(out);
		store.logEvent(runId, "OUT", "AcuseRecibo", SpeiProtocol.OP_ACUSERECIBO,
				errors.isEmpty() ? "aceptado" : "rechazado", "erroresOrdenes=" + errors.size(), acuseBody);
		logger.info("[SPEI] >> AcuseRecibo folioPack={} status={} erroresOrdenes={}",
				orden.folioPack(), (int) status, errors.size());
	}

	// ---- Fase 5: envío manual de abonos (disparado desde Main vía consola) ----

	/** Manda un abono de prueba, válido o deliberadamente inválido. Ver README &sect;"Probar
	 *  Fase 5" para cómo dispararlo desde la consola del simulador. */
	public void sendTestAbono(boolean valid) {
		if (!alive) {
			logger.warn("[SPEI] No hay sesión SPEI viva todavía, no se puede mandar Abonos");
			return;
		}
		try {
			String detail = valid
					// nombreOrdenante|tipoCtaOrdenante|ctaOrdenante|rfcOrdenante|nombreBeneficiario|
					// tipoCtaBeneficiario|ctaBeneficiario|rfcBeneficiario|conceptoPg|iva|referenciaNumerica|referenciaCobranza
					? String.join("\0",
							"JUAN PEREZ GOMEZ", "40", "012180000123456789",
							"PEGJ800101H01", "MARIA LOPEZ RUIZ", "40", "012180000987654321",
							"", "PAGO DE PRUEBA SIMULADOR", "", "1234567", "")
					// rfcOrdenante deliberadamente inválido (no cumple RFC/CURP) para probar rechazo
					: String.join("\0",
							"JUAN PEREZ GOMEZ", "40", "012180000123456789",
							"RFC-INVALIDO!!", "MARIA LOPEZ RUIZ", "40", "012180000987654321",
							"", "PAGO DE PRUEBA SIMULADOR", "", "1234567", "");

			AbonosCodec.AbonoVSpec spec = new AbonosCodec.AbonoVSpec(
					LocalDate.now(), config.ownEntityIndex(), config.ownEntityCode(),
					config.minosEntityIndex(), config.minosEntityCode(),
					1, 0, false, new BigDecimal("100.00"), 1, "SIMU" + System.currentTimeMillis(),
					detail, valid);
			byte[] abonoV = AbonosCodec.buildAbonoV(spec, identity.privateKey());

			AbonosCodec.AbonoTRef abonoT = new AbonosCodec.AbonoTRef(
					config.minosEntityIndex(), config.minosEntityCode(), 1, (short) 1);
			byte[] payload = AbonosCodec.buildPayload(
					LocalDate.now(), 1, abonoT, new BigDecimal("100.00"), abonoV,
					new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);

			byte[] body = WireFraming.buildEncryptedSignedPartitioned(payload, identity.privateKey(),
					sessionKey, sessionIv);
			Frame.of(SpeiProtocol.OP_ABONOS, body).writeTo(out);
			store.logEvent(runId, "OUT", "Abonos", SpeiProtocol.OP_ABONOS,
					valid ? "enviado-valido" : "enviado-invalido", null, body);
			logger.info("[SPEI] >> Abonos ({})", valid ? "contenido válido" : "contenido deliberadamente inválido");
		} catch (Exception e) {
			logger.error("[SPEI] No fue posible mandar el abono de prueba: {}", e.getMessage(), e);
		}
	}
}
