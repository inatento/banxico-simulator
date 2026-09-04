package mx.endcom.hermes.banxicosim.spei.messages;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import mx.endcom.hermes.banxicosim.crypto.RsaCipher;
import mx.endcom.hermes.banxicosim.wire.ByteReader;
import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Desafío RSA de sesión: el simulador manda {@code ClvSim} (código 80) y minos responde
 * {@code RespClvSim} (código 221).
 *
 * <p>Verificado contra {@code core/spei/dto/in/ClvSim.java} (lo que minos parsea) y
 * {@code SpeiInputManagerServiceImpl#processClvSimMessage} (líneas 860-869) del repo minos:</p>
 * <ul>
 *   <li>minos desencripta {@code encriptedSymetricKey} con SU PROPIA llave privada
 *       ({@code Spei.privateKey}) — por eso el simulador debe cifrar con la llave PÚBLICA real
 *       de minos (pre-configurada, la misma que se usa para el reto ARA IdUsuarioAleat si minos
 *       usa un solo certificado para ambos sockets — ver README).</li>
 *   <li>Los primeros 16 bytes desencriptados son la llave AES de sesión; los siguientes 16, el IV
 *       (ver {@code ClvSim.build()}: {@code key = copyOfRange(0,16)}, {@code vector = copyOfRange(16,32)}).</li>
 *   <li>minos firma exactamente esos 32 bytes (la llave simétrica completa, ANTES de partirla)
 *       con su propia llave privada, y regresa esa firma en Base64 como único campo de
 *       {@code RespClvSim} (ver {@code processClvSimMessage}, línea 865:
 *       {@code CipherBase64.sign(symmetricalKey, Spei.privateKey)}).</li>
 *   <li>El campo {@code signature} que el simulador manda en {@code ClvSim} NO es leído por
 *       {@code ClvSim.build()} ni usado en ningún punto de {@code processClvSimMessage} — es
 *       decorativo del lado de minos. El simulador igual lo llena con una firma real propia
 *       (firma los mismos 32 bytes con su propia llave privada) porque ADR-005 pide protocolo
 *       real sin atajos, aunque minos no la verifique.</li>
 * </ul>
 */
public final class ClvSimCodec {

	private static final SecureRandom RANDOM = new SecureRandom();

	private ClvSimCodec() {
	}

	public record SessionKeys(byte[] key, byte[] iv, byte[] rawSymmetricKey) {
	}

	public record ClvSimBody(byte[] bytes, SessionKeys sessionKeys) {
	}

	public static ClvSimBody build(PublicKey minosPublicKey, PrivateKey ownPrivateKey) throws Exception {
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		byte[] key = java.util.Arrays.copyOfRange(raw, 0, 16);
		byte[] iv = java.util.Arrays.copyOfRange(raw, 16, 32);

		byte[] encryptedB64 = RsaCipher.encryptToBase64(raw, minosPublicKey);
		byte[] ownSignatureB64 = RsaCipher.encodeBase64(RsaCipher.sign(raw, ownPrivateKey));

		byte[] body = new ByteWriter()
				.writeShortBE((short) 1) // idEncryptionAlgorithm: valor arbitrario, minos no lo valida
				.writeCString(new String(encryptedB64, StandardCharsets.US_ASCII))
				.writeCString(new String(ownSignatureB64, StandardCharsets.US_ASCII))
				.toByteArray();

		return new ClvSimBody(body, new SessionKeys(key, iv, raw));
	}

	public record RespClvSimResult(boolean signatureVerified) {
	}

	/** Verifica (opcional, no bloqueante) la firma que minos regresa en RespClvSim. */
	public static RespClvSimResult verifyResponse(byte[] respClvSimBody, byte[] rawSymmetricKey,
			PublicKey minosPublicKey) {
		try {
			String sigB64 = new ByteReader(respClvSimBody).readCString();
			if (minosPublicKey == null) {
				return new RespClvSimResult(false);
			}
			byte[] rawSig = RsaCipher.decodeBase64(sigB64.getBytes(StandardCharsets.US_ASCII));
			boolean ok = RsaCipher.verify(rawSymmetricKey, rawSig, minosPublicKey);
			return new RespClvSimResult(ok);
		} catch (Exception e) {
			return new RespClvSimResult(false);
		}
	}
}
