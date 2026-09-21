package mx.endcom.hermes.banxicosim.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuración del simulador. Orden de precedencia, de menor a mayor: valores por defecto →
 * archivo .properties (por defecto {@code config/simulator.properties}, sobreescribible con
 * {@code -Dconfig=<ruta>}) → variables de entorno.
 *
 * <p>Las variables de entorno existen para poder correr el contenedor Docker sin tener que montar
 * un archivo de configuración solo para cambiar un puerto o un código de entidad — se necesita un
 * archivo montado de cualquier forma para {@code minos-public-cert.pem}, pero el resto de los
 * parámetros son casos comunes al portar el simulador a otra máquina/CI. El nombre de cada
 * variable es la clave del .properties en mayúsculas con los puntos convertidos a guión bajo, p.
 * ej. {@code spei.port} &rarr; {@code SPEI_PORT}.</p>
 *
 * Ningún valor aquí es secreto de producción: son parámetros de un ambiente de pruebas propio
 * del simulador (ver AGENTS.md &sect;6 — política de datos sensibles).
 */
public final class SimConfig {

	private final Properties props;

	private SimConfig(Properties props) {
		this.props = props;
	}

	public static SimConfig load() throws IOException {
		Properties defaults = new Properties();
		defaults.setProperty("spei.port", "6001");
		defaults.setProperty("ara.port", "6002");
		defaults.setProperty("control.port", "8089");
		defaults.setProperty("identity.dir", "data/identity");
		// Vacío por defecto = versionado automático (ver SimulatorIdentity.loadOrCreate /
		// generateFreshCertificateNumber). Fijarlo a un valor explícito solo tiene sentido para
		// casos puntuales -- ver nota de clase en SimulatorIdentity.java, 2026-09-21.
		defaults.setProperty("identity.certificateNumber", "");
		defaults.setProperty("minos.publicCert.path", "config/minos-public-cert.pem");
		defaults.setProperty("own.entityCode", "90999");
		defaults.setProperty("own.entityIndex", "1");
		defaults.setProperty("minos.entityCode", "90646");
		defaults.setProperty("minos.entityIndex", "2");
		defaults.setProperty("minos.entityName", "MINOS-DEV");
		defaults.setProperty("minos.certificateNumber", "0000000002");
		defaults.setProperty("db.path", "data/banxicosim");
		defaults.setProperty("spei.user", "BXCOTEST");

		Properties props = new Properties(defaults);
		String configPath = System.getProperty("config", "config/simulator.properties");
		Path path = Path.of(configPath);
		if (Files.exists(path)) {
			try (InputStream in = Files.newInputStream(path)) {
				props.load(in);
			}
		}
		applyEnvironmentOverrides(props, defaults);
		return new SimConfig(props);
	}

	/** Sobreescribe cada clave conocida si existe la variable de entorno correspondiente. */
	private static void applyEnvironmentOverrides(Properties props, Properties defaults) {
		for (String key : defaults.stringPropertyNames()) {
			String envName = key.toUpperCase(java.util.Locale.ROOT).replace('.', '_');
			String envValue = System.getenv(envName);
			if (envValue != null && !envValue.isBlank()) {
				props.setProperty(key, envValue);
			}
		}
	}

	public int speiPort() {
		return Integer.parseInt(props.getProperty("spei.port"));
	}

	public int araPort() {
		return Integer.parseInt(props.getProperty("ara.port"));
	}

	/** Puerto de la API de control HTTP (fuera del protocolo SPEI/ARA), ver paquete
	 *  {@code control}. */
	public int controlPort() {
		return Integer.parseInt(props.getProperty("control.port"));
	}

	public Path identityDir() {
		return Path.of(props.getProperty("identity.dir"));
	}

	/** Vacío/no configurado = versionado automático al generar una identidad nueva (ver
	 *  {@code SimulatorIdentity.loadOrCreate}). */
	public String ownCertificateNumber() {
		return props.getProperty("identity.certificateNumber");
	}

	public Path minosPublicCertPath() {
		return Path.of(props.getProperty("minos.publicCert.path"));
	}

	public int ownEntityCode() {
		return Integer.parseInt(props.getProperty("own.entityCode"));
	}

	public int ownEntityIndex() {
		return Integer.parseInt(props.getProperty("own.entityIndex"));
	}

	public int minosEntityCode() {
		return Integer.parseInt(props.getProperty("minos.entityCode"));
	}

	public int minosEntityIndex() {
		return Integer.parseInt(props.getProperty("minos.entityIndex"));
	}

	public String minosEntityName() {
		return props.getProperty("minos.entityName");
	}

	public String minosCertificateNumber() {
		return props.getProperty("minos.certificateNumber");
	}

	public String dbPath() {
		return props.getProperty("db.path");
	}

	public String speiUser() {
		return props.getProperty("spei.user");
	}
}
