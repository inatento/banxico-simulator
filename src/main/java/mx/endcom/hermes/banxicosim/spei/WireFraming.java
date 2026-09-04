package mx.endcom.hermes.banxicosim.spei;

import java.security.PrivateKey;
import java.security.PublicKey;

import mx.endcom.hermes.banxicosim.crypto.AesCipher;
import mx.endcom.hermes.banxicosim.crypto.RsaCipher;
import mx.endcom.hermes.banxicosim.wire.ByteReader;
import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Envolturas de cuerpo del protocolo SPEI, verificadas contra el código fuente real de minos:
 * particionado (con prefijo de tamaño), firmado y cifrado AES-CBC de sesión. Se implementan en
 * ambos sentidos (construir para mandar, y deshacer para parsear) porque el simulador hace las
 * dos cosas según el mensaje.
 *
 * <p>Fuente: {@code core/spei/message/in/protocol/SpeiInput{Partitioned,Signed,EncryptedAndSigned}Message.java}
 * y sus contrapartes {@code .../out/protocol/SpeiOutput...Message.java}. Confirmado: NO hay bytes
 * de relleno entre el cuerpo y la firma en el protocolo SPEI principal (a diferencia del protocolo
 * ARA — ver {@code AraSignedMessage}, que sí tiene una peculiaridad de un byte, documentada en
 * {@code AraWireFraming}).</p>
 */
public final class WireFraming {

	private WireFraming() {
	}

	/** Antepone el prefijo de 2 bytes = tamaño total (particionado sin cifrar, ej. EnSesion, AcuseRecibo).
	 *  Como el simulador siempre manda el mensaje completo en una sola parte, esto basta para que
	 *  minos considere el mensaje completo de inmediato (ver SpeiInputPartitionedMessage.isComplete():
	 *  {@code totalBodySize == body.length - 2}). */
	public static byte[] withLengthPrefix(byte[] inner) {
		return new ByteWriter().writeShortBE((short) inner.length).writeBytes(inner).toByteArray();
	}

	/** Firma {@code payload} con la llave privada propia y arma [tamañoFirma BE(4)][payload][firma],
	 *  donde la firma va en Base64 (ASCII) — igual que minos (ver SpeiOutputSignedMessage.signMessage). */
	public static byte[] signedBlock(byte[] payload, PrivateKey signingKey) throws Exception {
		byte[] rawSignature = RsaCipher.sign(payload, signingKey);
		byte[] sigB64 = RsaCipher.encodeBase64(rawSignature);
		return new ByteWriter()
				.writeIntBE(sigB64.length)
				.writeBytes(payload)
				.writeBytes(sigB64)
				.toByteArray();
	}

	/** Cifra con AES-CBC de sesión (llave/IV acordados en ClvSim/RespClvSim). */
	public static byte[] encryptSession(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
		return AesCipher.encrypt(plaintext, key, iv);
	}

	/** Arma el cuerpo de un mensaje "particionado + cifrado" sin firma, tipo MsjCatalogos:
	 *  cifra( [totalSize(2)] + payload ). Ver SpeiInputEncryptedPartitionedMessage (MsjCatalogosMessage
	 *  extiende esta clase directamente, sin envoltura de firma, a diferencia de Abonos/OrdenTopoV). */
	public static byte[] buildEncryptedPartitioned(byte[] payload, byte[] sessionKey, byte[] sessionIv)
			throws Exception {
		return encryptSession(withLengthPrefix(payload), sessionKey, sessionIv);
	}

	/** Arma el cuerpo completo de un mensaje "particionado + firmado + cifrado" tipo Abonos:
	 *  cifra( [totalSize(2)] + [sigSize(4)] + payload + firmaB64 ). */
	public static byte[] buildEncryptedSignedPartitioned(byte[] payload, PrivateKey signingKey,
			byte[] sessionKey, byte[] sessionIv) throws Exception {
		byte[] signedBlock = signedBlock(payload, signingKey);
		byte[] withPrefix = withLengthPrefix(signedBlock);
		return encryptSession(withPrefix, sessionKey, sessionIv);
	}

	/**
	 * Deshace un cuerpo "particionado + firmado + cifrado" (ej. OrdenTopoV, que minos manda con
	 * exactamente esta envoltura — ver {@code SpeiOutputEncriptedAndSignedPartitionedMessage}).
	 *
	 * @param cipherBody cuerpo tal como llegó por el cable (ya cifrado)
	 * @return el payload (contenido específico del mensaje) ya desenvuelto
	 */
	public static Unwrapped unwrapEncryptedSignedPartitioned(byte[] cipherBody, byte[] sessionKey,
			byte[] sessionIv, PublicKey verifyingKey) throws Exception {
		byte[] plaintext = AesCipher.decrypt(cipherBody, sessionKey, sessionIv);
		ByteReader r = new ByteReader(plaintext);
		int totalSize = r.readUnsignedShortBE();
		if (totalSize != plaintext.length - 2) {
			throw new IllegalStateException(
					"Mensaje incompleto o corrupto: totalSize=" + totalSize + " pero cuerpo trae "
							+ (plaintext.length - 2) + " bytes. El simulador no reensambla particiones "
							+ "multi-parte en v1 (ver README, limitación conocida).");
		}
		int sigSize = r.readIntBE();
		int payloadLen = plaintext.length - 2 - 4 - sigSize;
		byte[] payload = r.readBytes(payloadLen);
		byte[] signatureB64 = r.readBytes(sigSize);
		boolean verified = false;
		if (verifyingKey != null) {
			try {
				byte[] rawSig = RsaCipher.decodeBase64(signatureB64);
				verified = RsaCipher.verify(payload, rawSig, verifyingKey);
			} catch (Exception e) {
				verified = false;
			}
		}
		return new Unwrapped(payload, verified);
	}

	public record Unwrapped(byte[] payload, boolean signatureVerified) {
	}
}
