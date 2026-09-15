package mx.endcom.hermes.banxicosim.crypto;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import javax.crypto.Cipher;

/**
 * RSA tal como lo usa minos: ver {@code util/impl/CipherBase64.java} del repo minos.
 * Modo/relleno exacto: {@code RSA/ECB/PKCS1Padding}. Firma: {@code SHA256withRSA}.
 *
 * minos codifica los blobs cifrados/firmados en Base64 y los manda como cadenas
 * terminadas en 0x00 (ver {@code ClvSim.build()}, {@code RespClvSim.bytes()},
 * {@code InicioSesionCifrada.bytes()}) — el simulador replica exactamente ese patrón:
 * cifrar/firmar bytes crudos, luego Base64, luego lo que corresponda como C-string.
 */
public final class RsaCipher {

	private static final String CIPHER_ALGO = "RSA/ECB/PKCS1Padding";
	private static final String SIGN_ALGO = "SHA256withRSA";

	/**
	 * Único mensaje del protocolo real que NO usa PKCS1Padding: {@code ClvSim} (el reto RSA de
	 * sesión). Verificado contra {@code core/spei/dto/in/ClvSim.java:92} del repo minos — minos
	 * desencripta la llave simétrica de sesión con esta transformación exacta, pidiendo
	 * explícitamente el proveedor "BC" (ver {@code util/impl/RSACipher.java:36}).
	 */
	private static final String CIPHER_ALGO_CLVSIM = "RSA/None/OAEPWithSHA-512AndMGF1Padding";

	private RsaCipher() {
	}

	public static byte[] encrypt(byte[] data, Key publicKey) throws Exception {
		Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return cipher.doFinal(data);
	}

	/** Cifra con el padding exacto que {@code ClvSim.build()} espera — ver {@link #CIPHER_ALGO_CLVSIM}. */
	public static byte[] encryptOaepSha512(byte[] data, Key publicKey) throws Exception {
		Cipher cipher = Cipher.getInstance(CIPHER_ALGO_CLVSIM, "BC");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return cipher.doFinal(data);
	}

	/** Como {@link #encryptOaepSha512} pero el resultado ya viene en Base64 (bytes ASCII). */
	public static byte[] encryptOaepSha512ToBase64(byte[] data, Key publicKey) throws Exception {
		return encodeBase64(encryptOaepSha512(data, publicKey));
	}

	public static byte[] decrypt(byte[] data, Key privateKey) throws Exception {
		Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		return cipher.doFinal(data);
	}

	/** Como {@link #encrypt} pero el resultado ya viene en Base64 (bytes ASCII). */
	public static byte[] encryptToBase64(byte[] data, Key publicKey) throws Exception {
		return encodeBase64(encrypt(data, publicKey));
	}

	/** Decodifica Base64 y luego desencripta — contraparte de {@code CipherBase64.decryptEncodeRSAECB}. */
	public static byte[] decryptFromBase64(byte[] base64Data, Key privateKey) throws Exception {
		return decrypt(Base64.getDecoder().decode(base64Data), privateKey);
	}

	public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
		Signature sig = Signature.getInstance(SIGN_ALGO);
		sig.initSign(privateKey);
		sig.update(data);
		return sig.sign();
	}

	public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
		Signature sig = Signature.getInstance(SIGN_ALGO);
		sig.initVerify(publicKey);
		sig.update(data);
		return sig.verify(signature);
	}

	public static byte[] encodeBase64(byte[] data) {
		return Base64.getEncoder().encodeToString(data).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	}

	public static byte[] decodeBase64(byte[] data) {
		return Base64.getDecoder().decode(data);
	}
}
