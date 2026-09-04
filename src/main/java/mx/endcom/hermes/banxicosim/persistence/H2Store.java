package mx.endcom.hermes.banxicosim.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistencia H2 embebida de corridas de prueba (Fase 6 de 02_especificacion_tecnica.md &sect;9).
 *
 * <p>Guarda, por cada mensaje enviado o recibido en una sesión: tipo de mensaje, dirección,
 * código de operación, timestamp y un resumen de resultado (aceptado/rechazado/error), más los
 * bytes crudos en hexadecimal para depuración posterior. No usa un ORM — es JDBC directo sobre
 * H2 en modo archivo, igual de simple que lo que necesita una herramienta de pruebas (mismo
 * patrón de propósito que {@code hermes-conectividad-monitor}, sin replicar su implementación).</p>
 */
public final class H2Store implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(H2Store.class);

	private final Connection connection;
	private final AtomicLong runIdSeq = new AtomicLong();

	private H2Store(Connection connection) {
		this.connection = connection;
	}

	public static H2Store open(String dbPath) throws SQLException {
		Class<?> ignored;
		try {
			ignored = Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Driver H2 no disponible en el classpath", e);
		}
		// H2 exige una ruta explícitamente relativa (./) o absoluta -- no acepta "data/x" a secas.
		String normalizedPath = dbPath.startsWith("/") || dbPath.startsWith("./") || dbPath.startsWith("~")
				? dbPath
				: "./" + dbPath;
		Connection conn = DriverManager.getConnection("jdbc:h2:file:" + normalizedPath + ";AUTO_SERVER=TRUE");
		H2Store store = new H2Store(conn);
		store.initSchema();
		return store;
	}

	private void initSchema() throws SQLException {
		try (Statement st = connection.createStatement()) {
			st.execute("""
					CREATE TABLE IF NOT EXISTS test_run (
						id BIGINT PRIMARY KEY,
						started_at TIMESTAMP NOT NULL,
						remote_address VARCHAR(255),
						channel VARCHAR(16)
					)
					""");
			st.execute("""
					CREATE TABLE IF NOT EXISTS test_event (
						id IDENTITY PRIMARY KEY,
						run_id BIGINT NOT NULL,
						occurred_at TIMESTAMP NOT NULL,
						direction VARCHAR(8) NOT NULL,
						message_name VARCHAR(64) NOT NULL,
						op_code INT,
						result VARCHAR(32),
						detail VARCHAR(4000),
						raw_hex CLOB
					)
					""");
		}
	}

	public long newRun(String channel, String remoteAddress) {
		long id = runIdSeq.incrementAndGet() * 1000 + System.currentTimeMillis() % 1000;
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO test_run (id, started_at, remote_address, channel) VALUES (?, ?, ?, ?)")) {
			ps.setLong(1, id);
			ps.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
			ps.setString(3, remoteAddress);
			ps.setString(4, channel);
			ps.executeUpdate();
		} catch (SQLException e) {
			logger.warn("No fue posible registrar la corrida de prueba: {}", e.getMessage());
		}
		return id;
	}

	public void logEvent(long runId, String direction, String messageName, int opCode, String result,
			String detail, byte[] raw) {
		try (PreparedStatement ps = connection.prepareStatement(
				"""
				INSERT INTO test_event (run_id, occurred_at, direction, message_name, op_code, result, detail, raw_hex)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			ps.setLong(1, runId);
			ps.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
			ps.setString(3, direction);
			ps.setString(4, messageName);
			ps.setInt(5, opCode);
			ps.setString(6, result);
			ps.setString(7, detail);
			ps.setString(8, raw == null ? null : bytesToHex(raw));
			ps.executeUpdate();
		} catch (SQLException e) {
			logger.warn("No fue posible registrar el evento de prueba {}: {}", messageName, e.getMessage());
		}
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}

	@Override
	public void close() throws SQLException {
		connection.close();
	}
}
