package mx.endcom.hermes.banxicosim.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuración del simulador, cargada de un archivo .properties (por defecto
 * {@code config/simulator.properties}, sobreescribible con {@code -Dconfig=<ruta>}).
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
		defaults.setProperty("identity.dir", "data/identity");
		defaults.setProperty("identity.certificateNumber", "0000000001");
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
		return new SimConfig(props);
	}

	public int speiPort() {
		return Integer.parseInt(props.getProperty("spei.port"));
	}

	public int araPort() {
		return Integer.parseInt(props.getProperty("ara.port"));
	}

	public Path identityDir() {
		return Path.of(props.getProperty("identity.dir"));
	}

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
