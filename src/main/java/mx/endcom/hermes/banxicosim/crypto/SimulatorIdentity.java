package mx.endcom.hermes.banxicosim.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identidad criptográfica propia del simulador: un par de llaves RSA y un certificado X.509
 * autofirmado.
 *
 * <p>Fundamento (ver ADR-005 y 02_especificacion_tecnica.md &sect;4): {@code CertUtil.stringToPublicKey}
 * en minos ({@code util/impl/CertUtil.java:48-54}) sólo parsea el certificado como X.509 y extrae
 * la llave pública — no valida cadena de confianza ni autoridad certificadora. Un certificado
 * autofirmado es indistinguible, para minos, de uno emitido por Banxico real.</p>
 *
 * <p>La identidad se genera una sola vez y se persiste en disco (PEM) para que el número de
 * certificado y las llaves sean estables entre reinicios del simulador durante una misma
 * campaña de pruebas.</p>
 *
 * <p><b>Número de certificado versionado (2026-09-21):</b> cuando se genera una identidad NUEVA
 * (no cuando se carga una existente), el número de certificado ya no se toma tal cual de
 * {@code identity.certificateNumber} en {@code simulator.properties} — se genera uno nuevo
 * automáticamente ({@link #generateFreshCertificateNumber()}), salvo que se pase uno explícito.
 * Motivo: minos (`EnSesionMessageHandler.fetchCertFromARA()`) **nunca refresca un certificado ya
 * cacheado bajo el mismo número** — es un bug real y conocido de minos, no corregido (ver memoria
 * de proyecto `project-banxico-simulador`). Con un número de certificado estático y fijo por
 * config, regenerar la identidad de este simulador (lo que Pedro tuvo que hacer a mano el
 * 2026-09-21 para resolver un `InvalidKeyException` real contra minosc) silenciosamente deja a
 * minos verificando firmas contra el certificado VIEJO cacheado, sin que nadie lo note hasta que
 * algo truene de forma confusa. Versionar el número en cada generación nueva obliga a minos a
 * hacer siempre una descarga fresca vía ARA. Ver `identity.certificateNumber` en
 * {@code config/simulator.properties.example} y {@code AraSession.handlePideCrtNvo} (minos pide
 * el número que el simulador declaró en su propio {@code EnSesion} — no hay ningún número fijo
 * que sincronizar del lado de minos, así que este cambio es seguro sin tocar minos).</p>
 */
public final class SimulatorIdentity {

	private static final Logger logger = LoggerFactory.getLogger(SimulatorIdentity.class);

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	private final KeyPair keyPair;
	private final X509Certificate certificate;
	private final String certificatePem;
	private final String certificateNumber;

	private SimulatorIdentity(KeyPair keyPair, X509Certificate certificate, String certificateNumber) {
		this.keyPair = keyPair;
		this.certificate = certificate;
		this.certificateNumber = certificateNumber;
		this.certificatePem = toPem(certificate);
	}

	public PrivateKey privateKey() {
		return keyPair.getPrivate();
	}

	public PublicKey publicKey() {
		return keyPair.getPublic();
	}

	public X509Certificate certificate() {
		return certificate;
	}

	/** El certificado en PEM: es lo que se manda por el cable (ver CertUtil.stringToPublicKey, que
	 *  hace {@code raw.getBytes()} sobre lo recibido — PEM es texto ASCII, así que sobrevive
	 *  cualquier codificación de plataforma sin corromperse, a diferencia de DER crudo). */
	public String certificatePem() {
		return certificatePem;
	}

	/** Número de certificado propio (arbitrario para el simulador; minos lo trata como opaco). */
	public String certificateNumber() {
		return certificateNumber;
	}

	/**
	 * Carga la identidad desde {@code dir} si ya existe, o la genera y la persiste.
	 *
	 * @param dir             directorio donde viven {@code identity.key}, {@code identity.crt},
	 *                        {@code identity.number}
	 * @param certificateNumber número de certificado a usar si hay que generar la identidad —
	 *                        {@code null} o vacío significa "versionar automáticamente" (ver
	 *                        {@link #generateFreshCertificateNumber()} y la nota de clase). Pasar
	 *                        un valor explícito solo tiene sentido para casos puntuales (p. ej.
	 *                        reproducir un número exacto en una prueba); el valor por defecto de
	 *                        {@code simulator.properties.example} ya viene vacío.
	 */
	public static SimulatorIdentity loadOrCreate(Path dir, String certificateNumber) throws Exception {
		Files.createDirectories(dir);
		Path keyPath = dir.resolve("identity.key.pem");
		Path certPath = dir.resolve("identity.crt.pem");
		Path numberPath = dir.resolve("identity.number");

		if (Files.exists(keyPath) && Files.exists(certPath) && Files.exists(numberPath)) {
			logger.info("Cargando identidad existente desde {}", dir);
			PrivateKey priv = readPrivateKey(keyPath);
			X509Certificate cert = readCertificate(certPath);
			String number = Files.readString(numberPath, StandardCharsets.US_ASCII).trim();
			PublicKey pub = cert.getPublicKey();
			return new SimulatorIdentity(new KeyPair(pub, priv), cert, number);
		}

		String freshNumber = (certificateNumber == null || certificateNumber.isBlank())
				? generateFreshCertificateNumber()
				: certificateNumber;
		logger.info("Generando identidad nueva (RSA 2048 + certificado autofirmado) en {} -- "
				+ "número de certificado versionado: {}", dir, freshNumber);
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		KeyPair kp = kpg.generateKeyPair();
		X509Certificate cert = selfSign(kp, "CN=BanxicoSimulator, O=HERMES MK I, C=MX");

		Files.writeString(keyPath, toPemPrivateKey(kp.getPrivate()));
		Files.writeString(certPath, toPem(cert));
		Files.writeString(numberPath, freshNumber);

		return new SimulatorIdentity(kp, cert, freshNumber);
	}

	/**
	 * Genera un número de certificado nuevo que minos casi con certeza nunca ha visto antes —
	 * ver la nota de clase sobre por qué esto importa. Epoch en segundos, formateado a los mismos
	 * 10 dígitos decimales del formato que ya usaba este simulador ({@code "0000000001"},
	 * {@code "0000000002"}, ...) — cabe justo (bueno hasta el año 2286) y es trivialmente único
	 * entre corridas sin necesitar un contador persistido aparte.
	 */
	private static String generateFreshCertificateNumber() {
		return String.format("%010d", Instant.now().getEpochSecond());
	}

	private static X509Certificate selfSign(KeyPair kp, String subjectDn) throws Exception {
		X500Name subject = new X500Name(subjectDn);
		BigInteger serial = BigInteger.valueOf(Instant.now().toEpochMilli());
		Instant notBefore = Instant.now().minus(Duration.ofDays(1));
		Instant notAfter = Instant.now().plus(Duration.ofDays(3650));

		SubjectPublicKeyInfo pubKeyInfo = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
		X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
				subject, serial, Date.from(notBefore), Date.from(notAfter), subject, pubKeyInfo);

		ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
				.setProvider("BC")
				.build(kp.getPrivate());

		return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
	}

	private static String toPem(Certificate cert) {
		try {
			String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(cert.getEncoded());
			return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
		} catch (Exception e) {
			throw new IllegalStateException("No fue posible codificar el certificado a PEM", e);
		}
	}

	private static String toPemPrivateKey(PrivateKey key) {
		String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
	}

	private static X509Certificate readCertificate(Path path) throws Exception {
		String pem = Files.readString(path, StandardCharsets.US_ASCII);
		return parseCertificatePem(pem);
	}

	/** Parsea un certificado X.509 en PEM. Misma llamada que usa minos (CertUtil.stringToPublicKey):
	 *  {@code CertificateFactory.getInstance("X.509")}. */
	public static X509Certificate parseCertificatePem(String pem) throws Exception {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		return (X509Certificate) cf.generateCertificate(
				new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
	}

	private static PrivateKey readPrivateKey(Path path) throws Exception {
		String pem = Files.readString(path, StandardCharsets.US_ASCII);
		String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		byte[] der = Base64.getDecoder().decode(b64);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
	}

	/** Carga la llave pública de minos (necesaria para cifrar el reto ARA IdUsuarioAleat, ver
	 *  {@code docs/README.md} y ARA §5) desde un archivo de certificado PEM externo. */
	public static PublicKey loadExternalPublicKey(Path pemFile) throws IOException {
		try {
			String pem = Files.readString(pemFile, StandardCharsets.US_ASCII);
			return parseCertificatePem(pem).getPublicKey();
		} catch (Exception e) {
			throw new IOException("No fue posible leer la llave pública de minos desde " + pemFile, e);
		}
	}
}
